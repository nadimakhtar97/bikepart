package com.nadim.bikepart

import com.nadim.bikepart.shop.GoogleDriveTransport
import com.nadim.bikepart.shop.DriveHttpResponse
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DriveUploadTransportTest {
    @Test fun storageRequiresTheMetadataGrantAndEveryListingPageAndNeverTreats404AsDeleted() = runBlocking {
        val withoutGrant = GoogleDriveTransport("token") { _, _, _, _ -> error("A limited token must not start an inventory") }
        try { withoutGrant.inventory("folder"); fail("Limited file access cannot prove a full inventory") }
        catch (error: IllegalStateException) { assertTrue(error.message!!.contains("metadata access")) }
        var incomplete = true
        var repeatToken = false
        var deletedStatus = 204
        val transport = GoogleDriveTransport("token", true) { url, method, _, _ ->
            val body = when {
                method == "DELETE" -> return@GoogleDriveTransport DriveHttpResponse(deletedStatus, byteArrayOf(), null, null)
                url.contains("/files/folder?") -> """{"id":"folder","mimeType":"application/vnd.google-apps.folder","ownedByMe":true,"capabilities":{"canListChildren":true}}"""
                url.endsWith("pageToken=next") -> """{"incompleteSearch":$incomplete,${if (repeatToken) "\"nextPageToken\":\"next\"," else ""}"files":[{"id":"two","name":"photo","size":"5","version":"8","capabilities":{"canDelete":true}}]}"""
                else -> """{"nextPageToken":"next","files":[{"id":"one","name":"table","size":"3","version":"7","capabilities":{"canDelete":true}}]}"""
            }
            DriveHttpResponse(200, body.toByteArray(), null, null)
        }
        try { transport.inventory("folder"); fail("Incomplete second page was accepted") }
        catch (error: IllegalStateException) { assertTrue(error.message!!.contains("incomplete")) }
        incomplete = false
        val inventory = transport.inventory("folder")
        assertTrue(inventory.ownerCanListAll)
        assertEquals(listOf("one", "two"), inventory.entries.map { it.id })
        assertEquals(listOf("7", "8"), inventory.entries.map { it.version })
        assertEquals(8L, inventory.entries.sumOf { it.size!! })
        repeatToken = true
        try { transport.inventory("folder"); fail("Repeated page token was accepted") }
        catch (error: IllegalStateException) { assertTrue(error.message!!.contains("pagination")) }
        transport.delete("old-file")
        for (status in listOf(403, 404)) {
            deletedStatus = status
            try { transport.delete("old-file"); fail("Uncertain delete response was accepted") } catch (_: IOException) { }
        }
    }

    @Test fun markerReplacementUpdatesOnlyTheSelectedRemoteFileAndRejectsPermissionFailure() = runBlocking {
        val bytes = "{\"datasetId\":\"shop\",\"generation\":\"new-writer\"}".toByteArray()
        var forbidden = false
        val transport = GoogleDriveTransport("memory-token") { url, method, body, headers ->
            assertEquals("https://www.googleapis.com/upload/drive/v3/files/marker-id?uploadType=media", url)
            assertEquals("PATCH", method)
            assertEquals("application/json; charset=UTF-8", headers["Content-Type"])
            assertArrayEquals(bytes, body)
            DriveHttpResponse(if (forbidden) 403 else 200, byteArrayOf(), null, null)
        }
        transport.replace("marker-id", bytes)
        forbidden = true
        try { transport.replace("marker-id", bytes); fail("Permission failure accepted") }
        catch (error: IOException) { assertTrue(error.message!!.contains("repair")) }
        // Verify Android's actual connection implementation accepts the method without making a request.
        val connection = java.net.URL("https://www.googleapis.com").openConnection() as java.net.HttpURLConnection
        try { connection.requestMethod = "PATCH"; assertEquals("PATCH", connection.requestMethod) }
        finally { connection.disconnect() }
    }
    @Test fun automaticNetworkPolicyIsCheckedBeforeOpeningEveryDriveRequest() = runBlocking {
        var checks = 0
        val transport = GoogleDriveTransport("unused-token") {
            checks++
            throw IOException("Wi-Fi unavailable")
        }
        repeat(2) {
            try { transport.newId(); fail("Policy must prevent network access") }
            catch (error: IOException) { assertEquals("Wi-Fi unavailable", error.message) }
        }
        assertEquals(2, checks)
    }
    @Test fun partialAcknowledgementResumesAtServerOffsetAfterLostChunkResponse() = runBlocking {
        val bytes = ByteArray(600_000) { (it % 251).toByte() }
        var savedSession: String? = null
        var acknowledged = 0
        var interrupted = false
        val received = java.io.ByteArrayOutputStream()
        val transport = GoogleDriveTransport("memory-token") { _, method, body, headers ->
            if (method == "POST") DriveHttpResponse(200, byteArrayOf(), "https://www.googleapis.com/upload/session", null)
            else if (headers["Content-Range"] == "bytes */600000") DriveHttpResponse(308, byteArrayOf(), null, "bytes=0-${acknowledged - 1}")
            else {
                assertEquals("bytes $acknowledged-${acknowledged + body!!.size - 1}/600000", headers["Content-Range"])
                received.write(body); acknowledged += body.size
                if (!interrupted) { interrupted = true; throw IOException("Response lost after server accepted first chunk") }
                DriveHttpResponse(if (acknowledged == bytes.size) 200 else 308, byteArrayOf(), null, "bytes=0-${acknowledged - 1}")
            }
        }
        try { transport.upload("id", "folder", "photo", bytes, null) { session, _ -> savedSession = session }; fail("Expected interruption") } catch (_: IOException) { }
        assertEquals(262144, acknowledged)
        transport.upload("id", "folder", "photo", bytes, savedSession) { _, _ -> }
        assertArrayEquals(bytes, received.toByteArray())
    }

    @Test fun expiredSessionRestartsUsingTheSameRemoteIdentityAndJournalsTheNewSession() = runBlocking {
        val sessions = mutableListOf<String?>()
        val transport = GoogleDriveTransport("memory-token") { _, method, body, headers ->
            when {
                method == "POST" -> {
                    val metadata = org.json.JSONObject(body!!.toString(Charsets.UTF_8))
                    assertEquals("stable-id", metadata.getString("id"))
                    DriveHttpResponse(200, byteArrayOf(), "https://www.googleapis.com/upload/replacement", null)
                }
                headers["Content-Range"] == "bytes */3" -> DriveHttpResponse(410, byteArrayOf(), null, null)
                else -> { assertArrayEquals(byteArrayOf(1, 2, 3), body); DriveHttpResponse(200, byteArrayOf(), null, null) }
            }
        }
        transport.upload("stable-id", "folder", "table", byteArrayOf(1, 2, 3), "https://www.googleapis.com/upload/expired") { session, _ -> sessions += session }
        assertNull(sessions.first())
        assertEquals("https://www.googleapis.com/upload/replacement", sessions.last())
    }
}
