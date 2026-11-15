package com.nadim.bikepart.updates

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Installation-local application boundary. No shop data or credentials enter discovery. */
data class InstalledApp(val packageId: String, val versionCode: Long, val versionName: String, val androidApi: Int,
    val signers: Set<String> = emptySet())
data class AvailableUpdate(val id: Long, val versionCode: Long, val versionName: String, val sizeBytes: Long,
    val summary: String, val apkUrl: String, val sha256: String, val minSdk: Int)
enum class UpdateFailure { Connection, Timeout, RateLimited }
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object NoUpdate : UpdateState
    data class Available(val release: AvailableUpdate) : UpdateState
    data class Failed(val reason: UpdateFailure) : UpdateState
}
private val noDownloadState = MutableStateFlow<DownloadState>(DownloadState.None).asStateFlow()

interface AppUpdater {
    val installed: InstalledApp
    val state: StateFlow<UpdateState>
    val downloadState: StateFlow<DownloadState> get() = noDownloadState
    suspend fun check()
    /** Starts a user-requested transfer. Implementations ignore duplicate starts. */
    fun startDownload() {}
    fun cancelDownload() {}
    fun close() {}
}
data class UpdateHttpResponse(val status: Int, val body: String)

/** Public GitHub requests only. Timeouts and response limits bound offline and hostile responses. */
class UpdateHttp(private val timeoutMillis: Int = 10_000,
    private val openConnection: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection }) {
    suspend fun get(url: String): UpdateHttpResponse = suspendCancellableCoroutine { continuation ->
        val connection = openConnection(url)
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Bikepart-updater")
        // Cancelling a coroutine alone cannot interrupt a blocking socket read. Close
        // the actual connection on cancellation, including foreground exit/total timeout.
        continuation.invokeOnCancellation { connection.disconnect() }
        Dispatchers.IO.dispatch(continuation.context, Runnable {
            try {
                if (!continuation.isActive) return@Runnable
                val status = connection.responseCode
                val body = if (status == 200) connection.inputStream.use { stream ->
                    val bytes = stream.readNBytes(1_048_577)
                    if (bytes.size > 1_048_576) throw IOException("Update response is too large")
                    bytes.toString(Charsets.UTF_8)
                } else ""
                continuation.resume(UpdateHttpResponse(status, body))
            } catch (error: Exception) {
                continuation.resumeWithException(error)
            } finally { connection.disconnect() }
        })
    }

    /**
     * Streams an APK into [destination] and reports cumulative bytes. The declared
     * body length is checked when present, [maxBytes] prevents oversized content
     * filling private storage, and cancellation disconnects the real socket.
     */
    suspend fun download(url: String, destination: File, maxBytes: Long, onProgress: (Long) -> Unit): Long {
        val workerFinished = CompletableDeferred<Unit>()
        var dispatched = false
        try {
            return suspendCancellableCoroutine { continuation ->
                val connection = openConnection(url)
                connection.connectTimeout = timeoutMillis
                connection.readTimeout = timeoutMillis
                connection.setRequestProperty("Accept", "application/octet-stream")
                connection.setRequestProperty("User-Agent", "Bikepart-updater")
                continuation.invokeOnCancellation { connection.disconnect() }
                Dispatchers.IO.dispatch(continuation.context, Runnable {
                    try {
                        if (!continuation.isActive) return@Runnable
                        val status = connection.responseCode
                        if (status != 200) throw HttpFailure(status)
                        val declared = connection.contentLengthLong
                        if (declared > maxBytes || declared > MAX_UPDATE_BYTES) throw UpdateTooLargeException()
                        var total = 0L
                        FileOutputStream(destination).use { output ->
                            connection.inputStream.use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    if (!continuation.isActive) throw CancellationException()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    if (total > maxBytes || total > MAX_UPDATE_BYTES) throw UpdateTooLargeException()
                                    output.write(buffer, 0, count)
                                    onProgress(total)
                                }
                            }
                            output.fd.sync()
                        }
                        if (declared >= 0 && total != declared) throw UpdateIntegrityException("Update download was truncated")
                        continuation.resume(total)
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    } finally {
                        try { connection.disconnect() } finally { workerFinished.complete(Unit) }
                    }
                })
                dispatched = true
            }
        } finally {
            if (!dispatched) workerFinished.complete(Unit)
            // Cancellation disconnects the socket, but the blocking worker may still
            // hold the destination file. Do not let cleanup or retry race that worker.
            withContext(NonCancellable) { workerFinished.await() }
        }
    }
}

private class HttpFailure(val status: Int) : IOException("Update server returned $status")

private const val MAX_UPDATE_BYTES = 256L * 1024 * 1024

class GitHubUpdater(override val installed: InstalledApp,
    private val downloads: UpdateDownloads? = null,
    private val get: suspend (String) -> UpdateHttpResponse = UpdateHttp()::get) : AppUpdater {
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state = mutableState.asStateFlow()
    override val downloadState: StateFlow<DownloadState> get() = downloads?.state ?: noDownloadState
    private val checkLock = Mutex()
    override suspend fun check() {
        if (!checkLock.tryLock()) return
        val previous = mutableState.value
        mutableState.value = UpdateState.Checking
        try {
            val outcome = withTimeout(30_000) {
                withContext(Dispatchers.IO) {
                    val seen = mutableSetOf<Long>()
                    val candidates = mutableListOf<AvailableUpdate>()
                    var page = 1
                    while (true) {
                        val releases = JSONArray(fetch("https://api.github.com/repos/nadimakhtar97/bikepart/releases?per_page=100&page=$page"))
                        for (index in 0 until releases.length()) {
                            val release = releases.optJSONObject(index) ?: continue
                            val id = release.optLong("id", -1)
                            if (id > 0 && seen.add(id)) candidate(release)?.let(candidates::add)
                        }
                        if (releases.length() < 100) break
                        if (++page > 20) throw IOException("Too many release pages")
                    }
                    candidates.maxByOrNull { it.versionCode }?.let(UpdateState::Available) ?: UpdateState.NoUpdate
                }
            }
            try {
                downloads?.reconcile((outcome as? UpdateState.Available)?.release)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Retention cleanup is best effort; a completed discovery still stands.
            }
            mutableState.value = outcome
        } catch (_: TimeoutCancellationException) {
            mutableState.value = UpdateState.Failed(UpdateFailure.Timeout)
        } catch (cancelled: CancellationException) {
            mutableState.value = previous
            throw cancelled
        } catch (error: Exception) {
            mutableState.value = UpdateState.Failed(if (error is HttpFailure && error.status in listOf(403, 429)) UpdateFailure.RateLimited else UpdateFailure.Connection)
        } finally { checkLock.unlock() }
    }

    override fun startDownload() {
        (mutableState.value as? UpdateState.Available)?.let { downloads?.start(it.release) }
    }

    override fun cancelDownload() {
        downloads?.cancel()
    }

    override fun close() {
        downloads?.close()
    }
    private suspend fun fetch(url: String): String {
        coroutineContext.ensureActive()
        val response = get(url)
        coroutineContext.ensureActive()
        if (response.status != 200) throw HttpFailure(response.status)
        return response.body
    }
    private suspend fun candidate(release: JSONObject): AvailableUpdate? {
        try {
            if (release.get("draft") != false || release.get("prerelease") != false) return null
            val assets = release.getJSONArray("assets")
            val required = listOf("bikepart.apk", "bikepart.aab", "SHA256SUMS.txt", "update.json").associateWith { name ->
                (0 until assets.length()).map { assets.getJSONObject(it) }.single { it.getString("name") == name }
            }
            val urls = required.mapValues { (name, asset) ->
                require(asset.getString("state") == "uploaded" && integer(asset, "size") > 0)
                val url = asset.getString("browser_download_url")
                require(url.startsWith("https://github.com/nadimakhtar97/bikepart/releases/download/") && url.endsWith("/$name"))
                url
            }
            // Every required asset belongs to the same immutable release path.
            require(urls.values.map { it.substringBeforeLast('/') }.distinct().size == 1)
            coroutineContext.ensureActive()
            val metadata = get(urls.getValue("update.json"))
            coroutineContext.ensureActive()
            if (metadata.status == 404) return null
            requireSuccess(metadata)
            val data = JSONObject(metadata.body)
            require(integer(data, "formatVersion") == 1L)
            val code = integer(data, "versionCode")
            val minimum = integer(data, "minSdk")
            val size = integer(data, "sizeBytes")
            val version = data.get("versionName")
            require(version is String)
            val hash = data.getString("sha256")
            require(data.getString("packageId") == installed.packageId && data.getString("apkAssetName") == "bikepart.apk")
            require(code > installed.versionCode && minimum in 1..installed.androidApi.toLong() && size > 0 && version.isNotBlank() && version.length <= 100 && version.none { it.isISOControl() })
            require(hash.matches(Regex("[0-9a-f]{64}")))
            val apk = required.getValue("bikepart.apk")
            require(integer(apk, "size") == size)
            if (apk.has("digest") && !apk.isNull("digest")) require(apk.getString("digest") == "sha256:$hash")
            coroutineContext.ensureActive()
            val checksums = get(urls.getValue("SHA256SUMS.txt"))
            coroutineContext.ensureActive()
            if (checksums.status == 404) return null
            requireSuccess(checksums)
            val entries = checksums.body.lineSequence().filter { it.isNotBlank() }.map {
                val match = Regex("([0-9a-f]{64})  (bikepart\\.(?:apk|aab))").matchEntire(it) ?: throw IllegalArgumentException("Malformed checksums")
                match.groupValues[2] to match.groupValues[1]
            }.toList()
            require(entries.size == 2 && entries.map { it.first }.toSet() == setOf("bikepart.apk", "bikepart.aab"))
            require(entries.toMap()["bikepart.apk"] == hash)
            val bundle = required.getValue("bikepart.aab")
            if (bundle.has("digest") && !bundle.isNull("digest")) require(bundle.getString("digest") == "sha256:${entries.toMap()["bikepart.aab"]}")
            return AvailableUpdate(integer(release, "id"), code, version, size,
                plainSummary(release.opt("body") as? String ?: ""), urls.getValue("bikepart.apk"), hash, minimum.toInt())
        } catch (_: NoSuchElementException) { return null } catch (_: org.json.JSONException) { return null } catch (_: IllegalArgumentException) { return null }
    }
    private fun requireSuccess(response: UpdateHttpResponse) {
        if (response.status != 200) throw HttpFailure(response.status)
    }
    private fun integer(json: JSONObject, name: String): Long {
        val value = json.get(name)
        require(value is Int || value is Long)
        return (value as Number).toLong()
    }
    private fun plainSummary(body: String): String = body
        .replace(Regex("<[^>]*>"), "")
        .replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("https?://\\S+"), "")
        .replace(Regex("(?m)^\\s*[#>*+\\-]+\\s*"), "")
        .replace(Regex("[`*_~]"), "")
        .replace(Regex("[\\p{Cntrl}\\s]+"), " ").trim().take(320)
}
