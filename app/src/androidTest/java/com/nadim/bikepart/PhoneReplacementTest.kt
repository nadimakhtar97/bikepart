package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nadim.bikepart.shop.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PhoneReplacementTest {
    @Test fun takeoverDuringContentUploadStopsFinalPublicationAndLeavesThePreviousPointRecoverable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drive = ReplacementDrive()
        Shop.open(context, "phone-upload-race-${UUID.randomUUID()}.db").use { shop ->
            shop.driveBackup.connect(DriveTarget("owner@example.com", "upload-race", "Upload race"))
            shop.driveBackup.publish(drive)
            val covered = shop.driveBackup.status()
            shop.setAppearance(Appearance.Dark)
            drive.beforeContent = { drive.changeWriter("replacement-during-upload") }
            try { shop.driveBackup.publish(drive); fail("Old writer published after takeover") } catch (_: IllegalStateException) { }
            assertEquals(1, shop.driveBackup.recoverable(drive).size)
            assertEquals(covered.coveredRevision, shop.driveBackup.status().coveredRevision)
            assertTrue(shop.driveBackup.status().automaticDeletionPaused)
        }
    }

    @Test fun lostTakeoverResponseReopensUncoveredAndRequiresExplicitReadOnlyReconciliation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "phone-lost-${UUID.randomUUID()}.db"
        val drive = ReplacementDrive()
        var previousWriter = ""
        Shop.open(context, name).use { shop ->
            shop.driveBackup.connect(DriveTarget("owner@example.com", "lost", "Lost response"))
            shop.driveBackup.publish(drive)
            previousWriter = drive.marker().getString("generation")
            shop.setAppearance(Appearance.Dark)
            drive.interruptContent = true
            try { shop.driveBackup.publish(drive); fail("Upload interruption missing") } catch (_: java.io.IOException) { }
            assertTrue(shop.driveBackup.status().pending)
            val preview = shop.driveBackup.previewReplacement(drive)
            drive.loseReplacementResponse = true
            try { shop.driveBackup.activateReplacement(drive, preview, true); fail("Lost response missing") } catch (_: java.io.IOException) { }
        }
        Shop.open(context, name).use { reopened ->
            val uncertain = reopened.driveBackup.status()
            assertTrue(uncertain.takeoverPending)
            assertTrue(uncertain.automaticDeletionPaused)
            assertFalse(uncertain.pending)
            assertNull(uncertain.coveredRevision)
            assertNotEquals(previousWriter, uncertain.writer)
            val before = drive.snapshot()
            try { reopened.driveBackup.publish(drive); fail("Uncertain takeover published") } catch (_: IllegalStateException) { }
            assertEquals(before, drive.snapshot())
            reopened.driveBackup.requireActivePhone(drive)
            assertEquals(before, drive.snapshot())
            assertEquals(1, drive.replacementRequests)
            assertFalse(reopened.driveBackup.status().automaticDeletionPaused)
            assertNull(reopened.driveBackup.status().coveredRevision)
            reopened.driveBackup.publish(drive)
            assertEquals(1L, reopened.driveBackup.status().coveredRevision)
            assertEquals(2, reopened.driveBackup.recoverable(drive).size)
        }
    }

    @Test fun takeoverRacesAndUncertainChecksPreserveHistoryAndCannotAcknowledgeAnotherWriter() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "phone-races-${UUID.randomUUID()}.db"
        val drive = ReplacementDrive()
        Shop.open(context, name).use { shop ->
            shop.driveBackup.connect(DriveTarget("owner@example.com", "races", "Races"))
            shop.driveBackup.publish(drive)
            val preview = shop.driveBackup.previewReplacement(drive)
            drive.afterReplace = { drive.changeWriter("competing-takeover") }
            try { shop.driveBackup.activateReplacement(drive, preview, true); fail("Competing takeover acknowledged") } catch (_: IllegalStateException) { }
            assertTrue(shop.driveBackup.status().takeoverPending)
            assertTrue(shop.driveBackup.status().automaticDeletionPaused)
            assertNull(shop.driveBackup.status().coveredRevision)
            assertEquals(1, shop.driveBackup.recoverable(drive).size)
            drive.afterReplace = null
            shop.driveBackup.activateReplacement(drive, shop.driveBackup.previewReplacement(drive), true)
            shop.driveBackup.publish(drive)
            val success = shop.driveBackup.status()
            shop.setAppearance(Appearance.Dark)
            drive.afterManifest = { drive.changeWriter("writer-after-final-publication") }
            try { shop.driveBackup.publish(drive); fail("Old-generation final response acknowledged") } catch (_: IllegalStateException) { }
            assertEquals(success.coveredRevision, shop.driveBackup.status().coveredRevision)
            assertEquals(success.successAt, shop.driveBackup.status().successAt)
            assertTrue(shop.driveBackup.status().automaticDeletionPaused)
            assertEquals(3, shop.driveBackup.recoverable(drive).size)
            drive.unavailable = true
            try { shop.driveBackup.requireActivePhone(drive); fail("Unavailable identity accepted") } catch (_: java.io.IOException) { }
            assertEquals(ActivePhoneState.Unknown, shop.driveBackup.status().phoneState)
            assertTrue(shop.driveBackup.status().automaticDeletionPaused)
        }
    }

    @Test fun aChangedReviewCannotLeaveAutomaticDeletionEnabled() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "phone-review-${UUID.randomUUID()}.db"
        val drive = ReplacementDrive()
        Shop.open(context, name).use { shop ->
            shop.driveBackup.connect(DriveTarget("owner@example.com", "review", "Reviewed folder"))
            shop.driveBackup.publish(drive)
            val preview = shop.driveBackup.previewReplacement(drive)
            drive.changeWriter("competing-phone")
            val before = drive.snapshot()
            try { shop.driveBackup.activateReplacement(drive, preview, true); fail("Stale review accepted") }
            catch (_: IllegalArgumentException) { }
            assertTrue("Changed review must pause deletion", shop.driveBackup.status().automaticDeletionPaused)
            assertEquals(before, drive.snapshot())
            assertEquals(1, shop.driveBackup.recoverable(drive).size)
        }
    }

    @Test fun retiredInstallationCannotResumeBackupsByReconnectingToAnotherFolder() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "retired-phone-${UUID.randomUUID()}.db"
        val drive = ReplacementDrive()
        Shop.open(context, name).use { shop ->
            shop.driveBackup.connect(DriveTarget("owner@example.com", "before", "Before retirement"))
            shop.driveBackup.publish(drive)
            shop.driveBackup.retireInstallation(true)
        }
        Shop.open(context, name).use { shop ->
            shop.driveBackup.connect(DriveTarget("owner@example.com", "after", "After retirement"))
            val before = drive.snapshot()
            try { shop.driveBackup.publish(drive); fail("Reconnect revived a retired installation") }
            catch (error: IllegalStateException) { assertTrue(error.message!!.contains("retired")) }
            assertEquals(before, drive.snapshot())
            assertEquals(ActivePhoneState.Retired, shop.driveBackup.status().phoneState)
        }
    }

    @Test fun restoredPhoneRequiresRetirementThenPublishesWithANewWriterAndTheOldPhoneStops() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceName = "phone-source-${UUID.randomUUID()}.db"
        val nextName = "phone-next-${UUID.randomUUID()}.db"
        val drive = ReplacementDrive()
        val target = DriveTarget("owner@example.com", "shop-folder", "Owner backups")
        Shop.open(context, sourceName).use { old ->
            old.createProduct("Recovered brake pads", old.snapshot().categories.first().id, "", "8", "50")
            old.driveBackup.connect(target)
            val original = old.driveBackup.publish(drive)
            val sourceWriter = drive.marker().getString("generation")
            Shop.open(context, nextName).use { next ->
                next.driveBackup.connect(target)
                val selected = next.driveBackup.restorePoints(drive).single()
                next.driveBackup.validateRestore(drive, selected).use { next.activateRestore(it, true) }
            }
            Shop.open(context, nextName).use { next ->
                val preview = next.driveBackup.previewReplacement(drive)
                assertEquals(target, preview.target)
                assertEquals(old.snapshot().datasetId, preview.datasetId)
                assertEquals(original.manifestId, preview.origin!!.manifestId)
                val before = drive.snapshot()
                try { next.driveBackup.activateReplacement(drive, preview, oldInstallationRetired = false); fail("Retirement must be explicit") }
                catch (_: IllegalArgumentException) { }
                assertEquals(before, drive.snapshot())
                next.driveBackup.activateReplacement(drive, preview, oldInstallationRetired = true)
                assertNotEquals(sourceWriter, drive.marker().getString("generation"))
                assertEquals(ActivePhoneState.Active, next.driveBackup.status().phoneState)
                assertNull(next.driveBackup.status().coveredRevision)
                val current = next.driveBackup.publish(drive)
                assertEquals(1L, current.revision)
                assertEquals(1L, next.driveBackup.status().coveredRevision)
                assertEquals(2, next.driveBackup.recoverable(drive).size)
            }
            val beforeOldRetry = drive.snapshot()
            try { old.driveBackup.publish(drive); fail("Old installation published") }
            catch (error: IllegalStateException) { assertTrue(error.message!!.contains("active shop phone")) }
            assertEquals(beforeOldRetry, drive.snapshot())
            assertTrue(old.driveBackup.status().automaticDeletionPaused)
            // Cooperative cloud checks cannot stop an offline installation from local edits.
            old.setSellingPrice(old.snapshot().products.single().id, "75")
            assertEquals(75L, old.snapshot().products.single().priceRupees)
        }
    }
}

internal class ReplacementDrive : DriveTransport {
    val files = mutableMapOf<String, ByteArray>()
    val names = mutableMapOf<String, String>()
    private val parents = mutableMapOf<String, String>()
    var interruptContent = false
    var loseReplacementResponse = false
    var unavailable = false
    var replacementRequests = 0
    var afterReplace: (() -> Unit)? = null
    var afterManifest: (() -> Unit)? = null
    var beforeContent: (() -> Unit)? = null
    fun marker() = org.json.JSONObject(files.getValue(names.entries.single { it.value == "active-phone.json" }.key).toString(Charsets.UTF_8))
    fun changeWriter(writer: String) {
        val id = names.entries.single { it.value == "active-phone.json" }.key
        files[id] = marker().put("generation", writer).toString().toByteArray()
    }
    fun snapshot() = files.mapValues { it.value.toList() }
    override suspend fun newId() = UUID.randomUUID().toString()
    override suspend fun list(folder: String): List<DriveEntry> {
        if (unavailable) throw java.io.IOException("Drive listing incomplete")
        return names.filterKeys { parents[it] == folder }.map { DriveEntry(it.key, it.value) }
    }
    override suspend fun download(id: String) = files[id]
    override suspend fun upload(id: String, folder: String, name: String, bytes: ByteArray, session: String?, progress: suspend (String?, Long) -> Unit) {
        if (interruptContent && name != "active-phone.json") { interruptContent = false; throw java.io.IOException("Content interrupted") }
        if (name != "active-phone.json") beforeContent?.invoke()
        check(id !in files)
        files[id] = bytes; names[id] = name; parents[id] = folder
        if (name.startsWith("complete-")) afterManifest?.invoke()
    }
    override suspend fun replace(id: String, bytes: ByteArray) {
        replacementRequests++
        check(id in files); files[id] = bytes
        afterReplace?.invoke()
        if (loseReplacementResponse) { loseReplacementResponse = false; throw java.io.IOException("Marker accepted, response lost") }
    }
}
