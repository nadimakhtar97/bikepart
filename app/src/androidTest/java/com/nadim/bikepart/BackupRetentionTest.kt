package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupRetentionTest {
    @Test fun indiaDaysAndLatestSevenFormOneUnionAcrossReopenAndEmptyDays() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "retention-union-${UUID.randomUUID()}.db"
        val drive = RetentionDrive(File(context.cacheDir, "remote-${UUID.randomUUID()}"))
        val now = Instant.parse("2026-11-12T19:00:00Z").toEpochMilli() // November 13, 00:30 in India.
        val eligible = mutableSetOf<String>()
        Shop.open(context, name).use { shop ->
            shop.driveBackup.connect(DriveTarget("owner@example.com", "shop", "Owner backups"))
            eligible += drive.publishAt(shop, "2026-10-14T18:29:59Z").manifestId // Just outside day 30.
            drive.publishAt(shop, "2026-10-14T18:30:00Z") // First instant inside day 30.
            eligible += drive.publishAt(shop, "2026-11-12T00:00:00Z").manifestId
            drive.publishAt(shop, "2026-11-12T18:29:59Z") // Latest on the preceding India day.
            eligible += drive.publishAt(shop, "2026-11-12T18:30:00Z").manifestId
            for (minute in 1..7) drive.publishAt(shop, "2026-11-12T18:${30 + minute}:00Z")
        }
        Shop.open(context, name).use { shop ->
            val before = shop.snapshot()
            val storage = shop.driveBackup.inspectStorage(RetentionDrive(drive.root), now)
            assertEquals(12, storage.completePoints)
            assertEquals(9, storage.retainedPoints)
            assertEquals(eligible, storage.eligible.map { it.manifestId }.toSet())
            assertTrue(storage.reclaimableBytes > 0)
            assertEquals(before, shop.snapshot())
        }
    }

    @Test fun sharedPhotosAndFailedPendingCaptureSurviveCleanupResponseLossAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "retention-pending-${UUID.randomUUID()}.db"
        val root = File(context.cacheDir, "remote-${UUID.randomUUID()}")
        val now = Instant.parse("2026-11-12T19:00:00Z").toEpochMilli()
        var blob = ""
        var revision = 0L
        Shop.open(context, name).use { shop ->
            val drive = RetentionDrive(root)
            val category = shop.snapshot().categories.first().id
            val product = shop.createProduct("Shared photo", category, "", "5", "30",
                photo = PhotoChange.Import { "shared attachment".byteInputStream() })
            shop.driveBackup.connect(DriveTarget("owner@example.com", "shop", "Owner backups"))
            val first = drive.publishAt(shop, "2026-10-01T00:00:00Z")
            val second = drive.publishAt(shop, "2026-10-02T00:00:00Z")
            fun blobId(point: DrivePoint): String {
                val files = JSONObject(File(root, point.manifestId).readText()).getJSONArray("files")
                return (0 until files.length()).map { files.getJSONObject(it) }.single { it.getString("path").startsWith("blobs/") }.getString("id")
            }
            blob = blobId(first)
            assertEquals("Unchanged photos must use one remote blob", blob, blobId(second))
            shop.editProduct(product, "Without photo", category, "", photo = PhotoChange.Remove)
            for (minute in 1..7) drive.publishAt(shop, "2026-11-12T18:${30 + minute}:00Z")
            val preceding = shop.driveBackup.status()
            shop.editProduct(product, "Pending photo", category, "", photo = PhotoChange.Import { "shared attachment".byteInputStream() })
            revision = shop.snapshot().revision
            drive.failUpload = true
            try { shop.driveBackup.publish(drive); fail("Expected failed pending upload") } catch (_: IOException) { }
            assertEquals(preceding.coveredRevision, shop.driveBackup.status().coveredRevision)
            assertEquals(preceding.successAt, shop.driveBackup.status().successAt)
            val review = shop.driveBackup.inspectStorage(drive, now)
            assertEquals(2, review.eligible.size)
            drive.loseDeleteResponse = true
            try { shop.driveBackup.cleanupStorage(drive, review, now); fail("Expected lost deletion response") } catch (_: IOException) { }
            assertEquals(8, shop.driveBackup.recoverable(drive).size)
            assertNotNull(drive.download(blob))
        }
        Shop.open(context, name).use { shop ->
            val drive = RetentionDrive(root)
            val review = shop.driveBackup.inspectStorage(drive, now)
            assertTrue(review.cleanupPending)
            val result = shop.driveBackup.cleanupStorage(drive, review, now)
            assertFalse(result.cleanupPending)
            assertEquals(7, result.completePoints)
            assertEquals(revision, shop.snapshot().revision)
            assertTrue(shop.driveBackup.status().pending)
            assertNotNull(drive.download(blob))
            shop.driveBackup.publish(drive)
            assertEquals(8, shop.driveBackup.recoverable(drive).size)
            assertArrayEquals("shared attachment".toByteArray(), drive.download(blob))
            assertEquals(revision, shop.driveBackup.status().coveredRevision)
        }
    }

    @Test fun uncertainInventoriesManifestsPermissionsAndPhoneIdentityNeverAuthorizeDeletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "retention-uncertain-${UUID.randomUUID()}.db"
        val drive = RetentionDrive(File(context.cacheDir, "remote-${UUID.randomUUID()}"))
        val now = Instant.parse("2026-11-12T19:00:00Z").toEpochMilli()
        Shop.open(context, name).use { shop ->
            shop.driveBackup.connect(DriveTarget("owner@example.com", "shop", "Owner backups"))
            val old = drive.publishAt(shop, "2026-10-01T00:00:00Z")
            for (minute in 1..7) drive.publishAt(shop, "2026-11-12T18:${30 + minute}:00Z")
            val valid = shop.driveBackup.inspectStorage(drive, now)
            assertEquals(1, valid.eligible.size)
            val manifest = requireNotNull(drive.download(old.manifestId))
            val dependency = JSONObject(String(manifest, Charsets.UTF_8)).getJSONArray("files").getJSONObject(0).getString("id")
            val markerId = drive.list("shop").single { it.name == "active-phone.json" }.id
            val marker = requireNotNull(drive.download(markerId))
            val before = drive.list("shop")
            for (fault in listOf("partial", "permission", "owner", "omitted dependency", "invalid manifest", "phone")) {
                when (fault) {
                    "partial" -> drive.partial = true
                    "permission" -> drive.permission = false
                    "owner" -> drive.owner = false
                    "omitted dependency" -> drive.omitted = dependency
                    "invalid manifest" -> drive.replace(old.manifestId, "invalid".toByteArray())
                    "phone" -> drive.replace(markerId, JSONObject(String(marker, Charsets.UTF_8)).put("generation", "another-phone").toString().toByteArray())
                }
                var rejected = false
                try { shop.driveBackup.cleanupStorage(drive, valid, now) } catch (_: Exception) { rejected = true }
                assertTrue("$fault must pause cleanup", rejected)
                assertTrue("$fault must not delete files", drive.deleted.isEmpty())
                drive.partial = false; drive.permission = true; drive.owner = true; drive.omitted = null
                drive.replace(old.manifestId, manifest); drive.replace(markerId, marker)
                assertEquals(before, drive.list("shop"))
            }
            assertFalse(shop.driveBackup.inspectStorage(drive, now).cleanupPending)
            assertEquals(8, shop.driveBackup.recoverable(drive).size)
        }
    }

    @Test fun theOnlyGoodPointSurvivesAnOldCaptureAndFailedReplacementAcrossRestart() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "retention-only-${UUID.randomUUID()}.db"
        val root = File(context.cacheDir, "remote-${UUID.randomUUID()}")
        val now = Instant.parse("2026-11-12T19:00:00Z").toEpochMilli()
        Shop.open(context, name).use { shop ->
            val drive = RetentionDrive(root)
            shop.driveBackup.connect(DriveTarget("owner@example.com", "shop", "Owner backups"))
            drive.publishAt(shop, "2026-01-01T00:00:00Z")
            shop.setAppearance(Appearance.Dark)
            drive.failUpload = true
            try { shop.driveBackup.publish(drive); fail("Expected upload interruption") } catch (_: IOException) { }
        }
        Shop.open(context, name).use { shop ->
            val drive = RetentionDrive(root)
            val preceding = shop.driveBackup.status()
            assertTrue(preceding.pending)
            val reviewed = shop.driveBackup.inspectStorage(drive, now)
            assertEquals(1, reviewed.completePoints)
            assertEquals(1, reviewed.retainedPoints)
            assertTrue(reviewed.eligible.isEmpty())
            try { shop.driveBackup.cleanupStorage(drive, reviewed, now); fail("Only good point must be protected") }
            catch (_: IllegalArgumentException) { }
            assertTrue(drive.deleted.isEmpty())
            assertEquals(1, shop.driveBackup.recoverable(drive).size)
            assertEquals(preceding.coveredRevision, shop.driveBackup.status().coveredRevision)
            assertEquals(preceding.successAt, shop.driveBackup.status().successAt)
        }
    }

    @Test fun internallyChecksummedButUnrestorableBusinessStatePausesCleanup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "retention-invalid-state-${UUID.randomUUID()}.db"
        val drive = RetentionDrive(File(context.cacheDir, "remote-${UUID.randomUUID()}"))
        val now = Instant.parse("2026-11-12T19:00:00Z").toEpochMilli()
        Shop.open(context, name).use { shop ->
            val product = shop.createProduct("Real stock", shop.snapshot().categories.first().id, "", "5", "30")
            shop.driveBackup.connect(DriveTarget("owner@example.com", "shop", "Owner backups"))
            drive.publishAt(shop, "2026-10-01T00:00:00Z")
            var latest: DrivePoint? = null
            for (minute in 1..7) latest = drive.publishAt(shop, "2026-11-12T18:${30 + minute}:00Z")
            val manifest = JSONObject(String(requireNotNull(drive.download(latest!!.manifestId)), Charsets.UTF_8))
            val files = manifest.getJSONArray("files")
            val balance = (0 until files.length()).map { files.getJSONObject(it) }.single { it.getString("path") == "tables/balances.csv" }
            val bytes = LocalBackup.csv(listOf(listOf("productId", "quantity"), listOf(product, "-1")))
            drive.replace(balance.getString("id"), bytes)
            val declared = manifest.getJSONObject("localManifest").getJSONArray("files")
            for (array in listOf(files, declared)) for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                if (item.getString("path") == "tables/balances.csv") item.put("size", bytes.size).put("sha256", LocalBackup.hash(bytes))
            }
            drive.replace(latest.manifestId, manifest.toString().toByteArray())
            // The forged hashes are consistent, but this point cannot reconstruct valid shop stock.
            var rejected = false
            try { shop.driveBackup.inspectStorage(drive, now) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue("Unrestorable points must not displace the good recovery history", rejected)
            assertTrue(drive.deleted.isEmpty())
            assertEquals(5L, shop.snapshot().products.single().quantity)
        }
    }

    @Test fun sameSizeCorruptionDuringCleanupPreservesTheRemainingGoodPoint() = runBlocking { verifyMutationSafety(true) }

    @Test fun missingRemoteVersionsRequireFreshContentValidation() = runBlocking { verifyMutationSafety(false) }

    private suspend fun verifyMutationSafety(withVersions: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "retention-change-${UUID.randomUUID()}.db"
        val drive = RetentionDrive(File(context.cacheDir, "remote-${UUID.randomUUID()}"))
        drive.versions = withVersions
        val now = Instant.parse("2026-11-12T19:00:00Z").toEpochMilli()
        Shop.open(context, name).use { shop ->
            val category = shop.snapshot().categories.first().id
            val product = shop.createProduct("Photo history", category, "", "5", "30", photo = PhotoChange.Import { "old photo".byteInputStream() })
            shop.driveBackup.connect(DriveTarget("owner@example.com", "shop", "Owner backups"))
            val first = drive.publishAt(shop, "2026-10-01T00:00:00Z")
            val good = drive.publishAt(shop, "2026-10-02T00:00:00Z")
            shop.editProduct(product, "New photo", category, "", photo = PhotoChange.Import { "new photo".byteInputStream() })
            var recent: DrivePoint? = null
            for (minute in 1..7) recent = drive.publishAt(shop, "2026-11-12T18:${30 + minute}:00Z")
            val files = JSONObject(String(requireNotNull(drive.download(recent!!.manifestId)), Charsets.UTF_8)).getJSONArray("files")
            val blob = (0 until files.length()).map { files.getJSONObject(it) }.single { it.getString("path").startsWith("blobs/") }.getString("id")
            val bytes = requireNotNull(drive.download(blob))
            val review = shop.driveBackup.inspectStorage(drive, now)
            drive.afterDelete = { id -> if (id == first.manifestId) drive.replace(blob, ByteArray(bytes.size)) }
            try { shop.driveBackup.cleanupStorage(drive, review, now); fail("Changed retained contents must pause cleanup") }
            catch (_: Exception) { }
            assertNotNull("The remaining good restore point must survive", drive.download(good.manifestId))
            assertEquals(listOf(good.manifestId), shop.driveBackup.recoverable(drive).map { it.manifestId })
            assertEquals(5L, shop.snapshot().products.single().quantity)
        }
    }
}

/** Durable external-service substitute. Room, portable files and the operational journal are real. */
internal class RetentionDrive(val root: File) : DriveTransport {
    init { check(root.isDirectory || root.mkdirs()) }
    private val metadata get() = File(root, "metadata.json")
    private fun entries() = if (metadata.exists()) JSONObject(metadata.readText()) else JSONObject()
    private fun save(value: JSONObject) { metadata.writeText(value.toString()) }
    var owner = true
    var permission = true
    var partial = false
    var versions = true
    var omitted: String? = null
    var loseDeleteResponse = false
    var failUpload = false
    var afterDelete: (suspend (String) -> Unit)? = null
    val deleted = mutableListOf<String>()
    override suspend fun newId(): String = UUID.randomUUID().toString()
    override suspend fun list(folder: String): List<DriveEntry> {
        val all = entries()
        return all.keys().asSequence().filter { all.getJSONObject(it).getString("folder") == folder }
            .map { DriveEntry(it, all.getJSONObject(it).getString("name")) }.toList()
    }
    override suspend fun inventory(folder: String): DriveInventory {
        check(!partial) { "Drive listing was incomplete." }
        val all = entries()
        return DriveInventory(list(folder).filter { it.id != omitted }.map {
            DriveStoredFile(it.id, it.name, File(root, it.id).length(), permission,
                if (versions) all.getJSONObject(it.id).optString("version").ifBlank { null } else null)
        }, owner)
    }
    override suspend fun download(id: String): ByteArray? = File(root, id).takeIf { it.isFile }?.readBytes()
    override suspend fun upload(id: String, folder: String, name: String, bytes: ByteArray, session: String?, progress: suspend (String?, Long) -> Unit) {
        if (failUpload && name != "active-phone.json") throw IOException("Upload interrupted")
        check(!File(root, id).exists()) { "Conflict" }
        File(root, id).writeBytes(bytes)
        save(entries().put(id, JSONObject().put("folder", folder).put("name", name).put("version", 1L)))
        progress(null, bytes.size.toLong())
    }
    override suspend fun replace(id: String, bytes: ByteArray) {
        check(File(root, id).isFile); File(root, id).writeBytes(bytes)
        val all = entries(); val file = all.getJSONObject(id)
        file.put("version", Math.addExact(file.optLong("version"), 1L)); save(all)
    }
    override suspend fun delete(id: String) {
        check(permission && File(root, id).isFile) { "Delete permission or identity is uncertain" }
        File(root, id).delete()
        save(entries().apply { remove(id) })
        deleted += id
        afterDelete?.invoke(id)
        if (loseDeleteResponse) { loseDeleteResponse = false; throw IOException("Delete response lost") }
    }
    suspend fun publishAt(shop: Shop, time: String): DrivePoint {
        val point = shop.driveBackup.publish(this)
        val manifest = JSONObject(String(requireNotNull(download(point.manifestId)), Charsets.UTF_8))
        val capturedAt = Instant.parse(time).toEpochMilli()
        manifest.put("capturedAt", capturedAt).getJSONObject("localManifest").put("capturedAt", capturedAt)
        replace(point.manifestId, manifest.toString().toByteArray(Charsets.UTF_8))
        return point.copy(capturedAt = capturedAt)
    }
}
