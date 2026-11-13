package com.nadim.bikepart.shop

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class RestoreBoundary { SafetySaved, MigrationsStarting, DatabaseStaged, AttachmentsStaged, GenerationStaged, RootPrepared, JournalWritten, JournalCommitted, Activated }
class ShopReplaced : IllegalStateException("The shop was restored. Reopen it before continuing.")

/** One journal entry selects the database, blobs and operational generation together.
 * Prepared roots and the previous state are retained; startup never guesses from partial files.
 */
internal class ShopStorage(private val context: Context, val name: String) {
    private val key = LocalBackup.hash(name.toByteArray(Charsets.UTF_8))
    private val base = File(context.filesDir, "shop-activations/$key")
    private val journal get() = AtomicFile(File(base, "active.json"))
    private val gate = gates.getOrPut(base.absolutePath) { Gate() }
    val changes get() = gate.changes
    val active: String? get() = if (journal.baseFile.exists()) journal.openRead().use { JSONObject(it.bufferedReader().readText()).getString("generation") } else null
    fun database(generation: String?) = generation?.let { File(base, "$it/shop.db").absolutePath } ?: name
    fun photos(generation: String?) = generation?.let { File(base, "$it/blobs") } ?: File(context.filesDir, "product-photos/$key")
    fun backups(generation: String?) = File(context.filesDir, "local-backups/$key" + (generation?.let { "/$it" } ?: ""))
    fun drive(generation: String?) = File(context.noBackupFilesDir, "drive/$key" + (generation?.let { "/$it" } ?: ""))
    fun recoveryCopy(): LocalCapture? = if (journal.baseFile.isFile) {
        journal.openRead().use { JSONObject(it.bufferedReader().readText()) }.optString("recovery").takeIf { it.isNotEmpty() }
            ?.let { runCatching { LocalBackup.inspect(File(it)) }.getOrNull() }
    } else null
    fun checkActive(generation: String?) { if (active != generation) throw ShopReplaced() }

    suspend fun <T> exclusive(block: suspend () -> T): T {
        if (currentCoroutineContext()[Held]?.gate === gate) return block()
        return gate.mutex.withLock { withContext(Held(gate)) { block() } }
    }

    suspend fun activate(shop: Shop, staged: ValidatedRestore, generation: String?,
        boundary: suspend (RestoreBoundary) -> Unit): LocalCapture = exclusive {
        checkActive(generation)
        // Keep this copy outside the selected generation so it survives any later activation.
        val safety = shop.captureLocalBackup(File(base, "recovery"))
        shop.validateLocalRestore(safety.file).use { check(it.snapshot == shop.snapshot()) { "Recovery copy verification failed." } }
        boundary(RestoreBoundary.SafetySaved)
        // Reconstruct and migrate again from our retained archive; a preview is not a mutable DB capability.
        RestoreValidation.prepare(context, File(staged.directory, "capture.zip"), File(drive(generation), "restore")) {
            boundary(RestoreBoundary.MigrationsStarting)
        }.use { validated ->
            check(validated.snapshot == staged.snapshot) { "Validated restore changed. Select and validate it again." }
            val next = UUID.randomUUID().toString()
            val root = File(base, next).apply { check(mkdirs()) }
            copySynced(File(validated.directory, "shop.db"), File(root, "shop.db"))
            boundary(RestoreBoundary.DatabaseStaged)
            val blobs = File(root, "blobs").apply { check(mkdirs()) }
            File(validated.directory, "blobs").listFiles().orEmpty().forEach { copySynced(it, File(blobs, it.name)) }
            sync(blobs)
            boundary(RestoreBoundary.AttachmentsStaged)
            val target = shop.driveBackup.status()
            val operational = drive(next).apply { check(mkdirs()) }
            val state = JSONObject().put("generation", next).put("connection", UUID.randomUUID().toString())
                .put("allowMobileData", target.allowMobileData)
            target.target?.let { state.put("target", JSONObject().put("account", it.account).put("folderId", it.folderId).put("folderName", it.folderName)) }
            writeSynced(File(operational, "state.json"), state.toString().toByteArray())
            syncAncestors(operational, context.noBackupFilesDir)
            boundary(RestoreBoundary.GenerationStaged)
            val provenance = JSONObject(staged.provenance.toString()).put("generation", next)
                .put("recovery", safety.file.absolutePath).put("previousGeneration", generation ?: JSONObject.NULL)
            writeSynced(File(root, "provenance.json"), provenance.toString().toByteArray())
            syncAncestors(root, context.filesDir)
            boundary(RestoreBoundary.RootPrepared)
            // AtomicFile recovers the previous entry if death interrupts this write.
            val output = journal.startWrite()
            try {
                output.write(provenance.toString().toByteArray()); output.fd.sync()
                boundary(RestoreBoundary.JournalWritten)
                journal.finishWrite(output)
            }
            catch (error: Throwable) { journal.failWrite(output); throw error }
            try {
                check(active == next) { "Could not commit the restore activation journal." }
                boundary(RestoreBoundary.JournalCommitted)
                sync(base)
            } finally {
                // A lost response/fsync failure after rename must still wake old handles.
                gate.changes.value++
            }
            boundary(RestoreBoundary.Activated)
        }
        safety
    }

    private class Gate { val mutex = Mutex(); val changes = MutableStateFlow(0L) }
    private class Held(val gate: Gate) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Held>
    }
    private companion object {
        val gates = ConcurrentHashMap<String, Gate>()
        fun copySynced(source: File, target: File) = FileOutputStream(target).use { output -> source.inputStream().use { it.copyTo(output) }; output.fd.sync() }
        fun writeSynced(target: File, bytes: ByteArray) = FileOutputStream(target).use { it.write(bytes); it.fd.sync() }
        fun sync(directory: File) {
            val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
            try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
        }
        fun syncAncestors(directory: File, stop: File) {
            var current = directory
            while (true) { sync(current); if (current == stop) break; current = requireNotNull(current.parentFile) }
        }
    }
}
