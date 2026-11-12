package com.nadim.bikepart

import com.nadim.bikepart.shop.GoogleDriveTransport
import com.nadim.bikepart.shop.DriveHttpResponse
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DriveUploadTransportTest {
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
