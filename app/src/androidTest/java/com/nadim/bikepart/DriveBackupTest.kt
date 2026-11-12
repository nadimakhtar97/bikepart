package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.UUID

class DriveBackupTest {
    @Test fun coverageRemainsReadableWhileUploadIsWaitingForNetwork() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "readable-${UUID.randomUUID()}.db"
        val drive = MemoryDrive()
        try {
            Shop.open(context, name).use { shop ->
                shop.driveBackup.connect(DriveTarget("owner@example.com", "folder", "Shop"))
                shop.driveBackup.publish(drive)
                val previous = shop.driveBackup.status()
                shop.setAppearance(Appearance.Dark)
                val paused = kotlinx.coroutines.CompletableDeferred<Unit>()
                val release = kotlinx.coroutines.CompletableDeferred<Unit>()
                drive.beforeContent = { paused.complete(Unit); release.await() }
                val upload = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext()).async {
                    shop.driveBackup.publish(drive)
                }
                try {
                    kotlinx.coroutines.withTimeout(10_000) { paused.await() }
                    shop.setAppearance(Appearance.Light)
                    val status = kotlinx.coroutines.withTimeout(2_000) { shop.driveBackup.status() }
                    assertEquals(previous.capturedAt, status.capturedAt)
                    assertEquals(1L, status.pendingRevision)
                    assertEquals(2L, shop.snapshot().revision)
                } finally { release.complete(Unit) }
                upload.await()
                assertEquals(1L, shop.driveBackup.status().coveredRevision)
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun automaticPolicyAndFailuresSurviveRestartWithoutCoveringNewerEdits() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "automatic-${UUID.randomUUID()}.db"
        val drive = MemoryDrive()
        try {
            Shop.open(context, name).use { shop ->
                shop.driveBackup.connect(DriveTarget("owner@example.com", "folder", "Shop"))
                assertFalse(shop.driveBackup.status().allowMobileData)
                shop.driveBackup.setAllowMobileData(true)
                shop.driveBackup.publish(drive)
                val complete = shop.driveBackup.status()
                shop.setAppearance(Appearance.Dark)
                drive.interrupt = true
                try { shop.driveBackup.publish(drive); fail("Expected network failure") } catch (_: IOException) { }
                assertNotNull(shop.driveBackup.status().failure)
                assertEquals(complete.capturedAt, shop.driveBackup.status().capturedAt)
            }
            Shop.open(context, name).use { shop ->
                assertTrue(shop.driveBackup.status().allowMobileData)
                assertNotNull(shop.driveBackup.status().failure)
                shop.setAppearance(Appearance.Light)
                shop.driveBackup.publish(drive)
                assertEquals(1L, shop.driveBackup.status().coveredRevision)
                assertEquals(2L, shop.snapshot().revision)
                assertNull(shop.driveBackup.status().failure)
                shop.driveBackup.publish(drive)
                assertEquals(2L, shop.driveBackup.status().coveredRevision)
                assertEquals(2L, shop.snapshot().revision)
            }
        } finally { context.deleteDatabase(name) }
    }
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
                assertEquals(0L, shop.driveBackup.status().pendingRevision)
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
                assertEquals(point.capturedAt, shop.driveBackup.status().capturedAt)
                assertFalse(drive.files.getValue(point.manifestId).toString(Charsets.UTF_8).contains("\"session\""))
                assertTrue(drive.resumed)
                assertEquals(1L, shop.snapshot().revision)
                assertEquals(1, shop.driveBackup.recoverable(drive).size)
                val dependency = drive.files.keys.first { it != point.manifestId && !drive.names.getValue(it).contains("active-phone") }
                drive.files[dependency] = "tampered".toByteArray()
                assertTrue(shop.driveBackup.recoverable(drive).isEmpty())
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun generationChangedDuringManifestResponseCannotAcknowledgeTheOldPhone() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "drive-ack-${UUID.randomUUID()}.db"
        val drive = MemoryDrive()
        try {
            Shop.open(context, name).use { shop ->
                shop.driveBackup.connect(DriveTarget("owner@example.com", "folder", "Shop"))
                shop.driveBackup.publish(drive)
                val preceding = shop.driveBackup.status()
                shop.setAppearance(Appearance.Dark)
                drive.afterManifest = {
                    val id = drive.names.entries.single { it.value == "active-phone.json" }.key
                    drive.files[id] = org.json.JSONObject(drive.files.getValue(id).toString(Charsets.UTF_8))
                        .put("generation", "new-phone").toString().toByteArray()
                }
                try { shop.driveBackup.publish(drive); fail("A stale final response must not acknowledge") } catch (_: IllegalStateException) { }
                assertEquals(preceding.coveredRevision, shop.driveBackup.status().coveredRevision)
                assertEquals(preceding.successAt, shop.driveBackup.status().successAt)
                assertTrue(shop.driveBackup.status().pending)
            }
        } finally { context.deleteDatabase(name) }
    }
}

internal class MemoryDrive : DriveTransport {
    var beforeContent: (suspend () -> Unit)? = null
    val files = mutableMapOf<String, ByteArray>()
    val names = mutableMapOf<String, String>()
    val parents = mutableMapOf<String, String>()
    var corruptNext = false
    var interrupt = false
    var loseManifestResponse = false
    var resumed = false
    var afterManifest: (() -> Unit)? = null
    private var partial: Pair<String, ByteArray>? = null
    override suspend fun newId() = UUID.randomUUID().toString()
    override suspend fun list(folder: String) = names.filterKeys { parents[it] == folder }.map { DriveEntry(it.key, it.value) }
    override suspend fun download(id: String): ByteArray? = files[id]
    override suspend fun upload(id: String, folder: String, name: String, bytes: ByteArray, session: String?, progress: suspend (String?, Long) -> Unit) {
        if (name != "active-phone.json") beforeContent?.invoke()
        if (interrupt && name != "active-phone.json") {
            interrupt = false
            partial = id to bytes.copyOfRange(0, bytes.size / 2)
            progress("session", (bytes.size / 2).toLong())
            throw IOException("Interrupted after partial acknowledgement")
        }
        if (partial?.first == id) {
            check(session == "session") { "Durable session was lost" }
            check(partial!!.second.contentEquals(bytes.copyOfRange(0, bytes.size / 2)))
            resumed = true; partial = null
        }
        if (files.containsKey(id)) throw IOException("Conflict")
        files[id] = if (corruptNext && name != "active-phone.json") { corruptNext = false; "corrupt".toByteArray() } else bytes; names[id] = name; parents[id] = folder
        if (name.startsWith("complete-")) afterManifest?.invoke()
        if (name.startsWith("complete-") && loseManifestResponse) { loseManifestResponse = false; throw IOException("Response lost") }
    }
}
