package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.UUID

class DriveBackupTest {
    @Test fun stalePhoneAndConflictingContentPreservePriorSuccessAndSwitchingTargetsClearsCoverage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "drive-guards-${UUID.randomUUID()}.db"
        val drive = MemoryDrive()
        try {
            Shop.open(context, name).use { shop ->
                shop.driveBackup.connect(DriveTarget("first@example.com", "first-folder", "First"))
                shop.driveBackup.publish(drive)
                val good = shop.driveBackup.status()
                shop.setAppearance(Appearance.Dark)
                drive.corruptNext = true
                try { shop.driveBackup.publish(drive); fail("Corrupt upload must fail") } catch (_: IllegalStateException) { }
                assertEquals(good.coveredRevision, shop.driveBackup.status().coveredRevision)
                assertEquals(good.successAt, shop.driveBackup.status().successAt)
                assertEquals(1, shop.driveBackup.recoverable(drive).size)
                val markerId = drive.names.entries.single { it.value == "active-phone.json" }.key
                drive.files[markerId] = org.json.JSONObject(drive.files.getValue(markerId).toString(Charsets.UTF_8))
                    .put("generation", "replacement-phone").toString().toByteArray()
                try { shop.driveBackup.publish(drive); fail("Stale phone must stop") } catch (_: IllegalStateException) { }
                assertEquals(good.successAt, shop.driveBackup.status().successAt)
                shop.driveBackup.connect(DriveTarget("second@example.com", "second-folder", "Second"))
                assertNull(shop.driveBackup.status().coveredRevision)
                assertFalse(shop.driveBackup.status().pending)
            }
            Shop.open(context, name).use { shop ->
                assertEquals("second@example.com", shop.driveBackup.status().target!!.account)
                assertNull(shop.driveBackup.status().successAt)
                val point = shop.driveBackup.publish(drive)
                assertEquals(1L, point.revision)
                assertEquals(1L, shop.driveBackup.status().coveredRevision)
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun interruptedUploadReopensAndLostManifestResponseIsVerifiedBeforeAcknowledgement() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "drive-${UUID.randomUUID()}.db"
        val drive = MemoryDrive()
        try {
            Shop.open(context, name).use { shop ->
                shop.driveBackup.connect(DriveTarget("owner@example.com", "folder", "Shop backups"))
                drive.interrupt = true
                try { shop.driveBackup.publish(drive); fail("Expected interruption") } catch (_: IOException) { }
                assertNull(shop.driveBackup.status().coveredRevision)
                assertTrue(shop.driveBackup.recoverable(drive).isEmpty())
            }
            Shop.open(context, name).use { shop ->
                shop.setAppearance(Appearance.Dark)
                drive.loseManifestResponse = true
                try { shop.driveBackup.publish(drive); fail("Expected lost response") } catch (_: IOException) { }
                assertNull(shop.driveBackup.status().coveredRevision)
                assertEquals(1, shop.driveBackup.recoverable(drive).size)
                val point = shop.driveBackup.publish(drive)
                assertEquals(0L, point.revision)
                assertEquals(0L, shop.driveBackup.status().coveredRevision)
                assertEquals(1L, shop.snapshot().revision)
                assertEquals(1, shop.driveBackup.recoverable(drive).size)
                val dependency = drive.files.keys.first { it != point.manifestId && !drive.names.getValue(it).contains("active-phone") }
                drive.files[dependency] = "tampered".toByteArray()
                assertTrue(shop.driveBackup.recoverable(drive).isEmpty())
            }
        } finally { context.deleteDatabase(name) }
    }
}

private class MemoryDrive : DriveTransport {
    val files = mutableMapOf<String, ByteArray>()
    val names = mutableMapOf<String, String>()
    val parents = mutableMapOf<String, String>()
    var corruptNext = false
    var interrupt = false
    var loseManifestResponse = false
    override suspend fun newId() = UUID.randomUUID().toString()
    override suspend fun list(folder: String) = names.filterKeys { parents[it] == folder }.map { DriveEntry(it.key, it.value) }
    override suspend fun download(id: String): ByteArray? = files[id]
    override suspend fun upload(id: String, folder: String, name: String, bytes: ByteArray, session: String?, progress: suspend (String?, Long) -> Unit) {
        if (interrupt && name != "active-phone.json") { interrupt = false; progress("session", 0); throw IOException("Interrupted") }
        if (files.containsKey(id)) throw IOException("Conflict")
        files[id] = if (corruptNext && name != "active-phone.json") { corruptNext = false; "corrupt".toByteArray() } else bytes; names[id] = name; parents[id] = folder
        if (name.startsWith("complete-") && loseManifestResponse) { loseManifestResponse = false; throw IOException("Response lost") }
    }
}
