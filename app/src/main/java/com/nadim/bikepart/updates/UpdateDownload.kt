package com.nadim.bikepart.updates

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.Properties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Recoverable download failures shown inside Application updates. */
enum class DownloadFailure { Connection, Timeout, Storage, Integrity, Identity, Signing, Unknown }

/** A completed APK is only exposed as Ready after every verification has passed. */
sealed interface DownloadState {
    val release: AvailableUpdate? get() = null
    data object None : DownloadState
    data class Downloading(override val release: AvailableUpdate, val downloadedBytes: Long) : DownloadState {
        val fraction: Float
            get() = if (release.sizeBytes <= 0) 0f else (downloadedBytes.toFloat() / release.sizeBytes).coerceIn(0f, 1f)
    }
    data class Verifying(override val release: AvailableUpdate) : DownloadState
    data class Ready(override val release: AvailableUpdate, val apk: File) : DownloadState
    data class Failed(override val release: AvailableUpdate, val reason: DownloadFailure) : DownloadState
}

/** Properties read from an APK by the platform, kept separate so verification is testable. */
data class ApkIdentity(val packageId: String, val versionCode: Long, val versionName: String?, val minSdk: Int,
    val signerDigests: Set<String>)

fun interface ApkArchiveInspector {
    /** Returns null when the file is not an inspectable APK. */
    fun inspect(file: File): ApkIdentity?
}

internal class UpdateStorageException(message: String) : IOException(message)
internal class UpdateIntegrityException(message: String) : IOException(message)
internal class UpdateTooLargeException : IOException("The update download is larger than expected")
internal class UpdateVerificationException(val failure: DownloadFailure) : IOException("The update APK failed verification")

/**
 * Owns the foreground-only APK transfer, verification and installation-local retention.
 * Shop data is never read or written here; the retained APK and its identity are
 * excluded from shop backups by the app manifest's backup rules.
 */
class UpdateDownloads(
    private val directory: File,
    private val installed: InstalledApp,
    private val inspector: ApkArchiveInspector,
    private val transfer: suspend (url: String, destination: File, maxBytes: Long,
        onProgress: (Long) -> Unit) -> Long = UpdateHttp()::download,
    private val availableBytes: (File) -> Long = { it.usableSpace },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val mutableState = MutableStateFlow<DownloadState>(DownloadState.None)
    val state: StateFlow<DownloadState> = mutableState.asStateFlow()

    private val startLock = Any()
    @Volatile private var generation = 0
    private var job: Job? = null
    private val restored = scope.async { restore() }

    /** Waits for retained-download revalidation. Tests use this to avoid polling. */
    suspend fun awaitRestored() = restored.await()

    /**
     * Starts a fresh transfer. Duplicate taps and taps while verifying are ignored;
     * a retry after cancellation waits for the old cleanup and starts from byte zero.
     */
    fun start(release: AvailableUpdate) {
        synchronized(startLock) {
            if (mutableState.value is DownloadState.Downloading || mutableState.value is DownloadState.Verifying) return
            // A tap can arrive before the launched transfer publishes its first
            // state; the active job, not just the state, prevents a second one.
            if (job?.isActive == true) return
            val ready = mutableState.value as? DownloadState.Ready
            if (ready != null && matches(ready.release, release)) return
            val current = ++generation
            val previous = job
            job = scope.launch {
                try {
                    previous?.join()
                    if (generation != current) return@launch
                    restored.await()
                    if (generation != current) return@launch
                    downloadAndVerify(release, current)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    publishIfCurrent(current, DownloadState.Failed(release, DownloadFailure.Unknown))
                }
            }
        }
    }

    /** Stops the active transfer; the partial file is never installable. */
    fun cancel() {
        synchronized(startLock) {
            generation++
            job?.cancel()
        }
        val value = mutableState.value
        if (value is DownloadState.Downloading || value is DownloadState.Verifying) mutableState.value = DownloadState.None
    }

    /** Drops a retained APK or stale failure that no longer matches the release offered by discovery. */
    suspend fun reconcile(available: AvailableUpdate?) {
        restored.await()
        val ready = mutableState.value as? DownloadState.Ready
        if (ready != null) {
            if (matches(ready.release, available)) return
            withContext(Dispatchers.IO) { deleteCompleted() }
            if (mutableState.value is DownloadState.Ready) mutableState.value = DownloadState.None
            return
        }
        val failed = mutableState.value as? DownloadState.Failed
        if (failed != null && !matches(failed.release, available)) mutableState.value = DownloadState.None
    }

    private fun matches(offered: AvailableUpdate, available: AvailableUpdate?) =
        available != null && available.versionCode == offered.versionCode && available.sha256 == offered.sha256

    fun close() {
        synchronized(startLock) { generation++ }
        job?.cancel()
        scope.coroutineContext[Job]?.cancel()
    }

    private suspend fun downloadAndVerify(release: AvailableUpdate, current: Int) = withContext(Dispatchers.IO) {
        val partial = File(directory, PARTIAL_NAME)
        val target = File(directory, APK_NAME)
        fun publishIfCurrent(value: DownloadState) {
            if (generation == current) mutableState.value = value
        }
        try {
            if (!directory.isDirectory && !directory.mkdirs()) throw UpdateStorageException("Could not create update storage")
            partial.delete()
            deleteCompleted()
            if (availableBytes(directory) < release.sizeBytes) throw UpdateStorageException("Not enough storage for the update")
            publishIfCurrent(DownloadState.Downloading(release, 0))
            var lastReported = 0L
            val transferred = transfer(release.apkUrl, partial, release.sizeBytes) { written ->
                if (written - lastReported >= 65_536 || written == release.sizeBytes) {
                    lastReported = written
                    publishIfCurrent(DownloadState.Downloading(release, written))
                }
            }
            if (transferred != release.sizeBytes || partial.length() != release.sizeBytes) throw UpdateIntegrityException("Update size did not match")
            publishIfCurrent(DownloadState.Verifying(release))
            verifyApk(partial, release, installed, inspector)?.let { throw UpdateVerificationException(it) }
            coroutineContext.ensureActive()
            if (target.exists() && !target.delete()) throw UpdateStorageException("Could not replace the retained update")
            if (!partial.renameTo(target)) throw UpdateStorageException("Could not retain the verified update")
            coroutineContext.ensureActive()
            persist(release)
            coroutineContext.ensureActive()
            publishIfCurrent(DownloadState.Ready(release, target))
        } catch (cancelled: CancellationException) {
            partial.delete()
            deleteCompleted()
            throw cancelled
        } catch (error: Exception) {
            partial.delete()
            deleteCompleted()
            publishIfCurrent(DownloadState.Failed(release, classify(error)))
        }
    }

    private fun restore() {
        try {
            if (!directory.isDirectory && !directory.mkdirs()) return
            // Process death, cancellation and failed writes leave only temporary files.
            directory.listFiles()?.forEach { file ->
                if (file.name != COMPLETED_NAME && file.name != APK_NAME) file.delete()
            }
            val release = readMetadata()
            val apk = File(directory, APK_NAME)
            if (release == null || !apk.isFile || verifyApk(apk, release, installed, inspector) != null ||
                release.versionCode <= installed.versionCode) {
                deleteCompleted()
                return
            }
            mutableState.value = DownloadState.Ready(release, apk)
        } catch (_: Exception) {
            deleteCompleted()
        }
    }

    private fun readMetadata(): AvailableUpdate? = try {
        val properties = Properties()
        FileInputStream(File(directory, COMPLETED_NAME)).use { properties.load(it) }
        fun text(name: String): String = requireNotNull(properties.getProperty(name)).also { require(it.isNotBlank()) }
        fun number(name: String): Long = requireNotNull(properties.getProperty(name)).toLong()
        val release = AvailableUpdate(number("releaseId"), number("versionCode"), text("versionName"), number("sizeBytes"),
            properties.getProperty("summary").orEmpty(), text("apkUrl"), text("sha256"), number("minSdk").toInt())
        require(properties.getProperty("format") == "1")
        require(release.versionCode > 0 && release.sizeBytes > 0 && release.minSdk > 0)
        require(release.sha256.matches(Regex("[0-9a-f]{64}")))
        require(release.apkUrl.startsWith("https://") && release.apkUrl.length <= 2_048)
        release
    } catch (_: Exception) {
        null
    }

    private fun persist(release: AvailableUpdate) {
        val temporary = File(directory, "$COMPLETED_NAME.tmp")
        val properties = Properties().apply {
            setProperty("format", "1")
            setProperty("releaseId", release.id.toString())
            setProperty("versionCode", release.versionCode.toString())
            setProperty("versionName", release.versionName)
            setProperty("sizeBytes", release.sizeBytes.toString())
            setProperty("summary", release.summary)
            setProperty("apkUrl", release.apkUrl)
            setProperty("sha256", release.sha256)
            setProperty("minSdk", release.minSdk.toString())
        }
        FileOutputStream(temporary).use { output -> properties.store(output, null); output.fd.sync() }
        val target = File(directory, COMPLETED_NAME)
        target.delete()
        if (!temporary.renameTo(target)) throw UpdateStorageException("Could not record the retained update")
    }

    private fun deleteCompleted() {
        File(directory, COMPLETED_NAME).delete()
        File(directory, "$COMPLETED_NAME.tmp").delete()
        File(directory, APK_NAME).delete()
    }

    private fun publishIfCurrent(current: Int, value: DownloadState) {
        if (generation == current) mutableState.value = value
    }

    private fun classify(error: Throwable): DownloadFailure {
        var cause: Throwable? = error
        while (cause != null) {
            when (cause) {
                is UpdateVerificationException -> return cause.failure
                is UpdateStorageException -> return DownloadFailure.Storage
                is UpdateIntegrityException, is UpdateTooLargeException -> return DownloadFailure.Integrity
                is SocketTimeoutException -> return DownloadFailure.Timeout
                is IOException -> if (cause.message.orEmpty().contains("ENOSPC", true) ||
                    cause.message.orEmpty().contains("No space left", true)) return DownloadFailure.Storage
            }
            cause = cause.cause?.takeUnless { it === cause }
        }
        return if (error is IOException) DownloadFailure.Connection else DownloadFailure.Unknown
    }

    private companion object {
        const val APK_NAME = "update.apk"
        const val PARTIAL_NAME = "update.partial"
        const val COMPLETED_NAME = "completed.properties"
    }
}

/** Size, checksum, identity, compatibility and signing must all agree before Ready. */
internal fun verifyApk(file: File, release: AvailableUpdate, installed: InstalledApp,
    inspector: ApkArchiveInspector): DownloadFailure? {
    if (!file.isFile || file.length() != release.sizeBytes) return DownloadFailure.Integrity
    if (sha256(file) != release.sha256) return DownloadFailure.Integrity
    val identity = try { inspector.inspect(file) } catch (_: Exception) { null } ?: return DownloadFailure.Identity
    if (identity.packageId != installed.packageId) return DownloadFailure.Identity
    if (identity.versionCode != release.versionCode || identity.versionName != release.versionName ||
        identity.minSdk != release.minSdk) return DownloadFailure.Identity
    if (identity.versionCode <= installed.versionCode || identity.minSdk > installed.androidApi) return DownloadFailure.Identity
    if (installed.signers.isEmpty() || identity.signerDigests.isEmpty()) return DownloadFailure.Signing
    // Android refuses an upgrade signed by the installed key plus an unrelated
    // extra certificate, so the candidate signer set must match exactly.
    if (identity.signerDigests != installed.signers) return DownloadFailure.Signing
    return null
}

internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
