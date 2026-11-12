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

data class DriveBackupStatus(val target: DriveTarget?, val coveredRevision: Long?, val successAt: Long?, val pending: Boolean,
    val capturedAt: Long?, val pendingRevision: Long?, val pendingCapturedAt: Long?,
    val allowMobileData: Boolean = false, val failure: String? = null)

/** Only external Google operations are substituted in interruption tests. Missing means HTTP 404 only. */
interface DriveTransport {
    suspend fun newId(): String
    suspend fun list(folder: String): List<DriveEntry>
    suspend fun download(id: String): ByteArray?
    suspend fun upload(id: String, folder: String, name: String, bytes: ByteArray, session: String?, progress: suspend (String?, Long) -> Unit)
}

/** Operational journal is outside Room/business exports and Android automatic backup. */
class DriveBackup internal constructor(private val shop: Shop, private val directory: File) {
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
            state.optBoolean("allowMobileData"), state.optString("failure").ifEmpty { null })
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
            state.put("connection", UUID.randomUUID().toString())
        }
        state.put("target", JSONObject().put("account", target.account).put("folderId", target.folderId).put("folderName", target.folderName))
        write(state)
    } }

    suspend fun publish(drive: DriveTransport, expectedTarget: DriveTarget? = null, onCapture: (revision: Long, capturedAt: Long) -> Unit = { _, _ -> }): DrivePoint = try {
        publishCapture(drive, expectedTarget, onCapture)
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (error: Exception) {
        recordFailure(error.message ?: "Backup failed. Check phone storage and Drive access, then retry.")
        throw error
    }

    private suspend fun publishCapture(drive: DriveTransport, expectedTarget: DriveTarget?, onCapture: (Long, Long) -> Unit): DrivePoint = withContext(Dispatchers.IO) { lock.withLock {
        val state = read()
        val target = requireNotNull(state.optJSONObject("target")) { "Connect a Google account and folder first." }
        check(expectedTarget == null || (expectedTarget.account == target.getString("account") && expectedTarget.folderId == target.getString("folderId"))) { "Backup account or folder changed. Retry with the chosen connection." }
        val folder = target.getString("folderId")
        val dataset = shop.snapshot().datasetId
        if (!state.has("generation")) { state.put("generation", UUID.randomUUID().toString()); write(state) }
        val generation = state.getString("generation")
        suspend fun checkPhone() {
            val candidates = drive.list(folder).filter { it.name == "active-phone.json" }
            check(candidates.size <= 1) { "Multiple active-phone markers. Repair the Drive folder before backing up." }
            var id = state.optString("marker")
            if (id.isEmpty()) {
                id = candidates.singleOrNull()?.id ?: drive.newId()
                state.put("marker", id); write(state)
            }
            check(candidates.all { it.id == id }) { "Active-phone identity changed. Stop and repair this connection." }
            val expected = JSONObject().put("datasetId", dataset).put("generation", generation).toString().toByteArray()
            var bytes = drive.download(id)
            if (bytes == null) {
                check(candidates.isEmpty() && !state.optBoolean("markerInitialized")) { "Active-phone marker is missing. Repair required." }
                check(drive.list(folder).none { it.name.startsWith("complete-") }) { "Existing shop backups require replacement activation." }
                drive.upload(id, folder, "active-phone.json", expected, null) { _, _ -> }
                bytes = drive.download(id)
            }
            val marker = JSONObject(requireNotNull(bytes).toString(Charsets.UTF_8))
            check(marker.getString("datasetId") == dataset && marker.getString("generation") == generation) { "This is not the active shop phone. Replacement activation is required." }
            check(drive.list(folder).filter { it.name == "active-phone.json" }.single().id == id)
            state.put("markerInitialized", true); write(state)
        }
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

    /** Discovery is fail closed: owner edits never become shop edits or trusted restore points. */
    suspend fun recoverable(drive: DriveTransport): List<DrivePoint> = withContext(Dispatchers.IO) { lock.withLock {
        val state = read(); val target = state.optJSONObject("target") ?: return@withLock emptyList()
        val dataset = shop.snapshot().datasetId
        drive.list(target.getString("folderId")).filter { it.name.startsWith("complete-") }.mapNotNull { entry ->
            try {
                val manifest = JSONObject(requireNotNull(drive.download(entry.id)).toString(Charsets.UTF_8))
                require(manifest.getInt("driveFormat") == 1 && manifest.getString("datasetId") == dataset)
                val local = manifest.getJSONObject("localManifest")
                require(local.getString("datasetId") == dataset && local.getString("captureId") == manifest.getString("captureId"))
                val files = manifest.getJSONArray("files")
                require(files.length() == local.getJSONArray("files").length())
                val temporary = File(directory, "verify-${UUID.randomUUID()}.zip")
                try {
                    java.util.zip.ZipOutputStream(temporary.outputStream()).use { zip ->
                        for (index in 0 until files.length()) {
                            val item = files.getJSONObject(index); val bytes = requireNotNull(drive.download(item.getString("id"))); verify(bytes, item)
                            zip.putNextEntry(java.util.zip.ZipEntry(item.getString("path"))); zip.write(bytes); zip.closeEntry()
                        }
                        zip.putNextEntry(java.util.zip.ZipEntry("manifest.json")); zip.write(local.toString().toByteArray()); zip.closeEntry()
                    }
                    val capture = LocalBackup.inspect(temporary)
                    require(capture.revision == manifest.getLong("revision") && capture.capturedAt == manifest.getLong("capturedAt"))
                    DrivePoint(entry.id, capture.revision, capture.capturedAt)
                } finally { temporary.delete() }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }.sortedByDescending { it.revision }
    } }
    private fun verify(bytes: ByteArray, item: JSONObject) {
        check(bytes.size.toLong() == item.getLong("size") && LocalBackup.hash(bytes) == item.getString("sha256")) { "Drive content failed verification. Repair the connection; the previous backup is preserved." }
    }
}
