package com.nadim.bikepart.shop

import android.util.AtomicFile
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class DriveTarget(val account: String, val folderId: String, val folderName: String)
data class DriveEntry(val id: String, val name: String)
data class DrivePoint(val manifestId: String, val revision: Long, val capturedAt: Long)
enum class ActivePhoneState { Unknown, Active, Mismatch, Retired }
data class RestoreOrigin(val datasetId: String, val captureId: String, val revision: Long, val capturedAt: Long,
    val manifestId: String?, val account: String?, val folderId: String?, val writer: String?)
data class PhoneReplacement internal constructor(val target: DriveTarget, val datasetId: String,
    val previousWriter: String, val origin: RestoreOrigin?, internal val markerId: String,
    internal val markerHash: String, internal val connection: String, internal val localWriter: String)

data class RestorePoint(val point: DrivePoint, val datasetId: String, val captureId: String,
    val products: Int, val sales: Int, val carts: Int, val photos: Int,
    internal val manifestHash: String, internal val target: DriveTarget)
data class DriveBackupStatus(val target: DriveTarget?, val coveredRevision: Long?, val successAt: Long?, val pending: Boolean,
    val capturedAt: Long?, val pendingRevision: Long?, val pendingCapturedAt: Long?,
    val allowMobileData: Boolean = false, val failure: String? = null,
    val phoneState: ActivePhoneState = ActivePhoneState.Unknown, val takeoverPending: Boolean = false,
    val writer: String? = null, val origin: RestoreOrigin? = null) {
    val automaticDeletionPaused: Boolean get() = phoneState != ActivePhoneState.Active || takeoverPending
}

/** Only external Google operations are substituted in interruption tests. Missing means HTTP 404 only. */
interface DriveTransport {
    suspend fun newId(): String
    suspend fun list(folder: String): List<DriveEntry>
    suspend fun download(id: String): ByteArray?
    suspend fun upload(id: String, folder: String, name: String, bytes: ByteArray, session: String?, progress: suspend (String?, Long) -> Unit)
    /** Update only the cooperative marker. This is not a per-installation exclusion fence. */
    suspend fun replace(id: String, bytes: ByteArray) { error("This Drive transport cannot activate a replacement phone.") }
}

/** Operational journal is outside Room/business exports and Android automatic backup. */
class DriveBackup internal constructor(private val shop: Shop, private val directory: File) {
    internal suspend fun <T> exclusiveActivation(block: suspend () -> T): T = withContext(Dispatchers.IO) { lock.withLock { block() } }
    companion object {
        private val lock = Mutex()
        private val journalLock = Any()
    }
    private val stateFile get() = AtomicFile(File(directory, "state.json"))
    private fun read(): JSONObject = synchronized(journalLock) {
        if (stateFile.baseFile.exists()) stateFile.openRead().use { JSONObject(it.bufferedReader().readText()) } else JSONObject()
    }
    private fun write(value: JSONObject) = synchronized(journalLock) {
        check(directory.isDirectory || directory.mkdirs())
        val output = stateFile.startWrite()
        try { output.write(value.toString().toByteArray(Charsets.UTF_8)); stateFile.finishWrite(output); syncJournalDirectory() }
        catch (error: Throwable) { stateFile.failWrite(output); throw error }
    }
    private fun syncJournalDirectory() {
        var current: File? = directory
        repeat(3) {
            current?.let { folder ->
                val descriptor = android.system.Os.open(folder.path, android.system.OsConstants.O_RDONLY, 0)
                try { android.system.Os.fsync(descriptor) } finally { android.system.Os.close(descriptor) }
                current = folder.parentFile
            }
        }
    }
    suspend fun status(): DriveBackupStatus = withContext(Dispatchers.IO) {
        val state = read()
        DriveBackupStatus(state.optJSONObject("target")?.let { DriveTarget(it.getString("account"), it.getString("folderId"), it.getString("folderName")) },
            if (state.has("covered")) state.getLong("covered") else null,
            if (state.has("successAt")) state.getLong("successAt") else null, state.has("job"),
            if (state.has("capturedAt")) state.getLong("capturedAt") else null,
            state.optJSONObject("job")?.getLong("revision"), state.optJSONObject("job")?.getLong("capturedAt"),
            state.optBoolean("allowMobileData"), state.optString("failure").ifEmpty { null },
            if (state.optBoolean("retired")) ActivePhoneState.Retired else ActivePhoneState.valueOf(state.optString("phoneState", ActivePhoneState.Unknown.name)), state.has("takeover"),
            state.optString("generation").ifEmpty { null }, shop.restoreOrigin())
    }
    suspend fun setAllowMobileData(allowed: Boolean) = withContext(Dispatchers.IO) { lock.withLock {
        val state = read(); state.put("allowMobileData", allowed); write(state)
    } }
    suspend fun recordFailure(message: String) = withContext(Dispatchers.IO) { lock.withLock {
        val state = read(); state.put("failure", message); write(state)
    } }
    suspend fun connect(target: DriveTarget) = withContext(Dispatchers.IO) { lock.withLock {
        require(target.account.isNotBlank() && target.folderId.isNotBlank() && target.folderName.isNotBlank())
        val state = read()
        val old = state.optJSONObject("target")
        if (old?.optString("account") != target.account || old.optString("folderId") != target.folderId) {
            // Old jobs remain on disk in an archive, and can never acknowledge a new connection.
            if (state.has("job")) { val archive = AtomicFile(File(directory, "abandoned-${UUID.randomUUID()}.json")); val stream = archive.startWrite(); stream.write(state.toString().toByteArray()); archive.finishWrite(stream) }
            state.remove("job"); state.remove("covered"); state.remove("successAt"); state.remove("capturedAt"); state.remove("marker"); state.remove("markerInitialized")
            state.remove("failure")
            state.remove("takeover"); state.remove("phoneState")
            state.put("connection", UUID.randomUUID().toString())
        }
        state.put("target", JSONObject().put("account", target.account).put("folderId", target.folderId).put("folderName", target.folderName))
        write(state)
    } }

    /** A preview binds owner confirmation to this dataset, connection and exact observed marker. */
    suspend fun previewReplacement(drive: DriveTransport): PhoneReplacement = withContext(Dispatchers.IO) { lock.withLock {
        shop.checkActive()
        val state = read()
        val target = requireNotNull(statusTarget()) { "Connect the owner's account and backup folder first." }
        val dataset = shop.snapshot().datasetId
        if (!state.has("generation")) { state.put("generation", UUID.randomUUID().toString()); write(state) }
        state.put("phoneState", ActivePhoneState.Unknown.name); write(state)
        val marker = marker(drive, target.folderId)
        require(marker.third.getString("datasetId") == dataset) { "This folder belongs to another shop. Restore the intended shop before activating this phone." }
        PhoneReplacement(target, dataset, marker.third.getString("generation"), shop.restoreOrigin(), marker.first,
            LocalBackup.hash(marker.second), state.getString("connection"), state.getString("generation"))
    } }

    suspend fun activateReplacement(drive: DriveTransport, preview: PhoneReplacement, oldInstallationRetired: Boolean) = withContext(Dispatchers.IO) { lock.withLock {
        require(oldInstallationRetired) { "Retire the old installation before activating this phone. An offline old phone can still edit locally." }
        shop.checkActive()
        val state = read()
        require(statusTarget() == preview.target && shop.snapshot().datasetId == preview.datasetId &&
            state.getString("connection") == preview.connection && state.getString("generation") == preview.localWriter) { "Shop or connection changed. Review replacement again." }
        // Publication may have reconfirmed this writer since preview. Invalidate that observation before network I/O.
        state.put("phoneState", ActivePhoneState.Unknown.name); write(state)
        val observed = marker(drive, preview.target.folderId)
        require(observed.first == preview.markerId && LocalBackup.hash(observed.second) == preview.markerHash) { "Active phone changed since review. Review replacement again; recovery history is preserved." }
        // Journal the new writer before the request. A lost response never permits an old job to acknowledge it.
        archiveJob(state)
        for (key in listOf("job", "covered", "successAt", "capturedAt")) state.remove(key)
        val generation = UUID.randomUUID().toString()
        state.remove("retired")
        state.put("generation", generation).put("marker", preview.markerId).put("markerInitialized", true)
            .put("phoneState", ActivePhoneState.Unknown.name)
            .put("takeover", JSONObject().put("previousWriter", preview.previousWriter).put("markerHash", preview.markerHash))
            .put("failure", "Phone activation is uncertain. Use Check active phone before backing up. Recovery history is preserved.")
        write(state)
        drive.replace(preview.markerId, phoneBytes(preview.datasetId, generation))
        verifyPhone(drive, state, reconcileTakeover = true)
    } }

    /** Reconciles a lost activation response without repeating a remote takeover. Call before cleanup too. */
    suspend fun requireActivePhone(drive: DriveTransport) = withContext(Dispatchers.IO) { lock.withLock {
        verifyPhone(drive, read(), reconcileTakeover = true)
    } }

    /** Local owner action only; uninstall/clear the old installation to retire its offline editing data. */
    suspend fun retireInstallation(confirmed: Boolean) = withContext(Dispatchers.IO) { lock.withLock {
        require(confirmed) { "Confirm retirement of this installation." }
        shop.checkActive()
        val state = read(); state.put("retired", true).put("phoneState", ActivePhoneState.Retired.name)
            .put("failure", "This installation is retired. Stop using it for shop work; uninstall it or clear its app data after checking the replacement.")
        write(state)
    } }

    private fun archiveJob(state: JSONObject) {
        if (!state.has("job")) return
        val archive = AtomicFile(File(directory, "abandoned-${UUID.randomUUID()}.json"))
        val output = archive.startWrite()
        try { output.write(state.toString().toByteArray(Charsets.UTF_8)); archive.finishWrite(output) }
        catch (error: Throwable) { archive.failWrite(output); throw error }
    }

    private fun phoneBytes(dataset: String, generation: String) = JSONObject().put("datasetId", dataset).put("generation", generation).toString().toByteArray(Charsets.UTF_8)

    private suspend fun marker(drive: DriveTransport, folder: String): Triple<String, ByteArray, JSONObject> {
        val entries = drive.list(folder).filter { it.name == "active-phone.json" }
        check(entries.size == 1) { "Active-phone marker is missing or duplicated. Repair the folder; recovery history is preserved." }
        val id = entries.single().id
        val bytes = requireNotNull(drive.download(id)) { "Active-phone marker is missing. Repair the folder." }
        val content = JSONObject(LocalBackup.utf8(bytes))
        require(content.getString("datasetId").isNotBlank() && content.getString("generation").isNotBlank()) { "Invalid active-phone marker. Repair the folder." }
        return Triple(id, bytes, content)
    }

    private suspend fun verifyPhone(drive: DriveTransport, state: JSONObject, initialize: Boolean = false, reconcileTakeover: Boolean = false) {
        shop.checkActive()
        check(!state.optBoolean("retired") && state.optString("phoneState") != ActivePhoneState.Retired.name) { "This installation is retired. Use the replacement phone for backups." }
        state.put("phoneState", ActivePhoneState.Unknown.name); write(state)
        check(!state.has("takeover") || reconcileTakeover) { "Phone activation is uncertain. Use Check active phone; recovery history is preserved." }
        val folder = requireNotNull(state.optJSONObject("target")) { "Connect a Google account and folder first." }.getString("folderId")
        val dataset = shop.snapshot().datasetId
        val generation = state.getString("generation")
        val candidates = drive.list(folder).filter { it.name == "active-phone.json" }
        if (initialize && candidates.isEmpty() && !state.optBoolean("markerInitialized")) {
            check(drive.list(folder).none { it.name.startsWith("complete-") }) { "Existing shop backups require replacement activation." }
            val id = state.optString("marker").ifEmpty { drive.newId().also { state.put("marker", it); write(state) } }
            val bytes = drive.download(id)
            if (bytes == null) drive.upload(id, folder, "active-phone.json", phoneBytes(dataset, generation), null) { _, _ -> }
        }
        val remote = marker(drive, folder)
        if ((state.has("marker") && remote.first != state.getString("marker")) ||
            remote.third.getString("datasetId") != dataset || remote.third.getString("generation") != generation) {
            state.put("phoneState", ActivePhoneState.Mismatch.name); write(state)
            error("This is not the active shop phone. Stop backups on this installation. Use the replacement phone, or review Activate replacement phone after retiring the old installation.")
        }
        check(drive.list(folder).filter { it.name == "active-phone.json" }.singleOrNull()?.id == remote.first) { "Active-phone identity is uncertain. Repair the folder before backup or cleanup." }
        state.put("marker", remote.first).put("markerInitialized", true).put("phoneState", ActivePhoneState.Active.name)
        state.remove("takeover"); state.remove("failure"); write(state)
    }

    suspend fun publish(drive: DriveTransport, expectedTarget: DriveTarget? = null, onCapture: (revision: Long, capturedAt: Long) -> Unit = { _, _ -> }): DrivePoint = try {
        publishCapture(drive, expectedTarget, onCapture)
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (error: Exception) {
        recordFailure(error.message ?: "Backup failed. Check phone storage and Drive access, then retry.")
        throw error
    }

    private suspend fun publishCapture(drive: DriveTransport, expectedTarget: DriveTarget?, onCapture: (Long, Long) -> Unit): DrivePoint = withContext(Dispatchers.IO) { lock.withLock {
        shop.checkActive()
        val state = read()
        val target = requireNotNull(state.optJSONObject("target")) { "Connect a Google account and folder first." }
        check(expectedTarget == null || (expectedTarget.account == target.getString("account") && expectedTarget.folderId == target.getString("folderId"))) { "Backup account or folder changed. Retry with the chosen connection." }
        val folder = target.getString("folderId")
        val dataset = shop.snapshot().datasetId
        if (!state.has("generation")) { state.put("generation", UUID.randomUUID().toString()); write(state) }
        val generation = state.getString("generation")
        suspend fun checkPhone() = verifyPhone(drive, state, initialize = true)
        checkPhone()
        var job = state.optJSONObject("job")
        if (job == null) {
            val capture = shop.captureLocalBackup()
            val manifest = ZipFile(capture.file).use { zip -> JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().readText()) }
            // Session URLs are operational capabilities and must never reach the portable manifest.
            val files = JSONArray(manifest.getJSONArray("files").toString())
            for (index in 0 until files.length()) files.getJSONObject(index).put("id", drive.newId())
            job = JSONObject().put("capture", capture.file.path).put("captureId", capture.id).put("revision", capture.revision).put("capturedAt", capture.capturedAt)
                .put("connection", state.getString("connection")).put("datasetId", dataset).put("generation", generation)
                .put("folderId", folder).put("account", target.getString("account")).put("files", files).put("manifestId", drive.newId())
                .put("localManifest", manifest)
            state.put("job", job); write(state)
        }
        val activeJob = job
        check(activeJob.getString("connection") == state.getString("connection") && activeJob.getString("datasetId") == dataset && activeJob.getString("generation") == generation && activeJob.getString("folderId") == folder && activeJob.getString("account") == target.getString("account")) { "Backup identity changed. Reconnect before retrying." }
        onCapture(activeJob.getLong("revision"), activeJob.getLong("capturedAt"))
        val captureFile = File(activeJob.getString("capture"))
        LocalBackup.inspect(captureFile)
        val files = activeJob.getJSONArray("files")
        ZipFile(captureFile).use { zip ->
            for (index in 0 until files.length()) {
                checkPhone()
                val item = files.getJSONObject(index)
                val bytes = zip.getInputStream(zip.getEntry(item.getString("path"))).use { it.readBytes() }
                val id = item.getString("id")
                val existing = drive.download(id)
                if (existing == null) drive.upload(id, folder, "${activeJob.getString("captureId")}-${item.getString("path").replace('/', '-')}", bytes, item.optString("session").ifEmpty { null }) { session, offset ->
                    if (session == null) item.remove("session") else item.put("session", session)
                    item.put("offset", offset); write(state)
                }
                verify(requireNotNull(drive.download(id)) { "Uploaded content cannot be read." }, item)
            }
        }
        // Revalidate all dependencies immediately before publication, including resumed files.
        for (index in 0 until files.length()) { val item = files.getJSONObject(index); verify(requireNotNull(drive.download(item.getString("id"))), item) }
        checkPhone()
        val manifest = JSONObject().put("driveFormat", 1).put("datasetId", dataset).put("generation", generation)
            .put("captureId", activeJob.getString("captureId")).put("revision", activeJob.getLong("revision")).put("capturedAt", activeJob.getLong("capturedAt"))
            .put("files", files.let { source -> JSONArray().apply { for (index in 0 until source.length()) { val item = source.getJSONObject(index); put(JSONObject().put("id", item.getString("id")).put("path", item.getString("path")).put("size", item.getLong("size")).put("sha256", item.getString("sha256"))) } } })
            .put("localManifest", activeJob.getJSONObject("localManifest"))
        val bytes = manifest.toString().toByteArray(Charsets.UTF_8)
        val manifestId = activeJob.getString("manifestId")
        if (drive.download(manifestId) == null) drive.upload(manifestId, folder, "complete-${activeJob.getString("captureId")}.json", bytes, activeJob.optString("manifestSession").ifEmpty { null }) { session, _ ->
            if (session == null) activeJob.remove("manifestSession") else activeJob.put("manifestSession", session); write(state)
        }
        check(requireNotNull(drive.download(manifestId)).contentEquals(bytes)) { "Completion manifest content does not match." }
        checkPhone()
        val point = DrivePoint(manifestId, activeJob.getLong("revision"), activeJob.getLong("capturedAt"))
        state.remove("failure")
        state.put("covered", maxOf(state.optLong("covered", -1), point.revision)).put("successAt", System.currentTimeMillis()).put("capturedAt", point.capturedAt).remove("job")
        write(state)
        point
    } }

    /** Discovery checks completion and every required byte. Business validation follows selection. */
    suspend fun restorePoints(drive: DriveTransport): List<RestorePoint> = withContext(Dispatchers.IO) { lock.withLock {
        val target = statusTarget() ?: return@withLock emptyList()
        check(directory.isDirectory || directory.mkdirs())
        drive.list(target.folderId).filter { it.name.startsWith("complete-") }.mapNotNull { entry ->
            try {
                downloadPoint(drive, entry.id) { file, manifest, hash ->
                    val local = manifest.getJSONObject("localManifest")
                    require(entry.name == "complete-${manifest.getString("captureId")}.json") { "Completion file identity does not match." }
                    val contents = LocalBackup.readForRestore(file)
                    fun count(table: String) = contents.tables[table]?.records?.size ?: 0
                    RestorePoint(DrivePoint(entry.id, manifest.getLong("revision"), manifest.getLong("capturedAt")),
                        contents.datasetId, local.getString("captureId"), count("products"), count("sales"), count("carts"), contents.blobs.size, hash, target)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }.sortedByDescending { it.point.capturedAt }
    } }

    suspend fun recoverable(drive: DriveTransport): List<DrivePoint> = restorePoints(drive).map { it.point }

    /** Redownload and bind validation to the exact preview, even if Drive changed after discovery. */
    suspend fun validateRestore(drive: DriveTransport, selected: RestorePoint): ValidatedRestore = withContext(Dispatchers.IO) { lock.withLock {
        require(statusTarget() == selected.target) { "Drive connection changed. Refresh restore points." }
        downloadPoint(drive, selected.point.manifestId, selected.manifestHash) { file, manifest, _ ->
            shop.validateLocalRestore(file).also {
                it.provenance.put("sourceWriter", manifest.getString("generation"))
                    .put("sourceManifest", selected.point.manifestId).put("sourceManifestHash", selected.manifestHash)
                    .put("sourceAccount", selected.target.account).put("sourceFolder", selected.target.folderId)
            }
        }
    } }

    private fun statusTarget(): DriveTarget? = read().optJSONObject("target")?.let { DriveTarget(it.getString("account"), it.getString("folderId"), it.getString("folderName")) }

    private suspend fun <T> downloadPoint(drive: DriveTransport, id: String, expectedHash: String? = null,
        use: suspend (File, JSONObject, String) -> T): T {
        check(directory.isDirectory || directory.mkdirs())
        val bytes = requireNotNull(drive.download(id)) { "Restore manifest is missing. Refresh restore points." }
        require(expectedHash == null || LocalBackup.hash(bytes) == expectedHash) { "Restore point changed since preview. Refresh and select it again." }
        val manifest = JSONObject(LocalBackup.utf8(bytes))
        require(manifest.get("driveFormat") == 1) { "Unsupported Drive restore version. Update the app." }
        val local = manifest.getJSONObject("localManifest")
        require(local.getBoolean("complete") && local.getInt("exportVersion") == LocalBackup.FORMAT_VERSION && local.getInt("roomVersion") in 1..14) { "Incomplete or unsupported restore point." }
        for (key in listOf("datasetId", "captureId", "revision", "capturedAt")) require(manifest.getString(key) == local.getString(key)) { "Restore manifest identity mismatch: $key." }
        require(manifest.getString("generation").isNotBlank()) { "Missing writer identity." }
        val files = manifest.getJSONArray("files")
        val declared = local.getJSONArray("files")
        require(files.length() == declared.length()) { "Restore dependencies differ from the portable manifest." }
        val expected = (0 until declared.length()).associate { declared.getJSONObject(it).let { item -> item.getString("path") to item } }
        require(expected.size == declared.length()) { "Duplicate restore dependency." }
        val ids = mutableSetOf(id)
        val paths = mutableSetOf<String>()
        val temporary = File(directory, "download-${UUID.randomUUID()}.zip")
        try {
            java.util.zip.ZipOutputStream(temporary.outputStream()).use { zip ->
                for (index in 0 until files.length()) {
                    val item = files.getJSONObject(index)
                    val path = item.getString("path")
                    val identity = item.getString("id")
                    require(identity.isNotBlank() && ids.add(identity) && paths.add(path)) { "Duplicate restore file identity." }
                    val original = requireNotNull(expected[path]) { "Unexpected restore dependency: $path." }
                    require(LocalBackup.integer(item, "size") == LocalBackup.integer(original, "size") && item.getString("sha256") == original.getString("sha256")) { "Restore file metadata mismatch: $path." }
                    val content = requireNotNull(drive.download(identity)) { "Required restore file is missing: $path." }
                    verify(content, item)
                    zip.putNextEntry(java.util.zip.ZipEntry(path)); zip.write(content); zip.closeEntry()
                }
                zip.putNextEntry(java.util.zip.ZipEntry("manifest.json")); zip.write(local.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            return use(temporary, manifest, LocalBackup.hash(bytes))
        } finally { temporary.delete() }
    }
    private fun verify(bytes: ByteArray, item: JSONObject) {
        check(bytes.size.toLong() == item.getLong("size") && LocalBackup.hash(bytes) == item.getString("sha256")) { "Drive content failed verification. Repair the connection; the previous backup is preserved." }
    }
}
