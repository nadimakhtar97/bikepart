package com.nadim.bikepart.shop

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Token stays in memory; session URLs stay in the non-exported operational journal. */
internal data class DriveHttpResponse(val code: Int, val bytes: ByteArray, val location: String?, val range: String?)

class GoogleDriveTransport internal constructor(private val token: String,
    private val exchange: (suspend (String, String, ByteArray?, Map<String, String>) -> DriveHttpResponse)?
) : DriveTransport {
    constructor(token: String) : this(token, null)
    constructor(token: String, beforeRequest: suspend () -> Unit) : this(token, null) {
        this.beforeRequest = beforeRequest
    }
    private var beforeRequest: suspend () -> Unit = {}
    companion object { private const val API = "https://www.googleapis.com/drive/v3" }
    private suspend fun request(url: String, method: String = "GET", bytes: ByteArray? = null, headers: Map<String, String> = emptyMap()): DriveHttpResponse = withContext(Dispatchers.IO) {
        beforeRequest()
        val uri = java.net.URI(url)
        require(uri.scheme == "https" && uri.host == "www.googleapis.com") { "Invalid Drive upload session" }
        exchange?.let { return@withContext it(url, method, bytes, headers) }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method; connection.instanceFollowRedirects = false
            connection.connectTimeout = 30_000; connection.readTimeout = 60_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (bytes != null) { connection.doOutput = true; connection.setFixedLengthStreamingMode(bytes.size); connection.outputStream.use { it.write(bytes) } }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes() } ?: byteArrayOf()
            DriveHttpResponse(code, body, connection.getHeaderField("Location"), connection.getHeaderField("Range"))
        } finally { connection.disconnect() }
    }
    private fun DriveHttpResponse.checked(): DriveHttpResponse {
        if (code !in 200..299) throw IOException(if (code == 401 || code == 403) "Drive access needs repair. Reconnect the selected account." else "Drive request failed ($code). Retry; the previous backup is preserved.")
        return this
    }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    override suspend fun newId(): String = JSONObject(request("$API/files/generateIds?count=1&space=drive&type=files").checked().bytes.toString(Charsets.UTF_8)).getJSONArray("ids").getString(0)
    override suspend fun download(id: String): ByteArray? {
        val response = request("$API/files/${encode(id)}?alt=media")
        return if (response.code == 404) null else response.checked().bytes
    }
    override suspend fun replace(id: String, bytes: ByteArray) {
        request("https://www.googleapis.com/upload/drive/v3/files/${encode(id)}?uploadType=media", "PATCH", bytes,
            mapOf("Content-Type" to "application/json; charset=UTF-8")).checked()
    }
    override suspend fun list(folder: String): List<DriveEntry> = listQuery("'${folder.replace("'", "\\'")}' in parents and trashed = false")
    suspend fun folders(): List<DriveEntry> = listQuery("mimeType = 'application/vnd.google-apps.folder' and trashed = false")
    private suspend fun listQuery(query: String): List<DriveEntry> {
        val result = mutableListOf<DriveEntry>(); var page = ""
        do {
            val json = JSONObject(request("$API/files?q=${encode(query)}&fields=nextPageToken,incompleteSearch,files(id,name)&pageSize=1000&pageToken=${encode(page)}").checked().bytes.toString(Charsets.UTF_8))
            check(!json.optBoolean("incompleteSearch")) { "Drive listing was incomplete. Retry later." }
            val files = json.getJSONArray("files")
            for (index in 0 until files.length()) { val file = files.getJSONObject(index); result += DriveEntry(file.getString("id"), file.getString("name")) }
            page = json.optString("nextPageToken")
        } while (page.isNotEmpty())
        return result
    }
    suspend fun createFolder(name: String): DriveEntry {
        require(name.isNotBlank())
        val json = JSONObject(request("$API/files?fields=id,name", "POST", JSONObject().put("name", name.trim()).put("mimeType", "application/vnd.google-apps.folder").toString().toByteArray(), mapOf("Content-Type" to "application/json; charset=UTF-8")).checked().bytes.toString(Charsets.UTF_8))
        return DriveEntry(json.getString("id"), json.getString("name"))
    }
    override suspend fun upload(id: String, folder: String, name: String, bytes: ByteArray, session: String?, progress: suspend (String?, Long) -> Unit) {
        var uri = session
        var offset = 0L
        if (uri != null) {
            val status = request(uri, "PUT", byteArrayOf(), mapOf("Content-Range" to "bytes */${bytes.size}"))
            when (status.code) {
                200, 201 -> return
                308 -> offset = status.range?.substringAfterLast('-')?.toLong()?.plus(1) ?: 0L
                404, 410 -> { uri = null; progress(null, 0) }
                else -> status.checked()
            }
        }
        if (uri == null) {
            val metadata = JSONObject().put("id", id).put("name", name).put("parents", org.json.JSONArray().put(folder)).put("mimeType", "application/octet-stream")
            val response = request("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable", "POST", metadata.toString().toByteArray(),
                mapOf("Content-Type" to "application/json; charset=UTF-8", "X-Upload-Content-Type" to "application/octet-stream", "X-Upload-Content-Length" to bytes.size.toString())).checked()
            uri = requireNotNull(response.location); progress(uri, 0)
        }
        do {
            val end = minOf(offset + 256 * 1024, bytes.size.toLong())
            val response = request(uri, "PUT", bytes.copyOfRange(offset.toInt(), end.toInt()), mapOf("Content-Type" to "application/octet-stream", "Content-Range" to if (bytes.isEmpty()) "bytes */0" else "bytes $offset-${end - 1}/${bytes.size}"))
            if (response.code in 200..299) { progress(uri, bytes.size.toLong()); return }
            if (response.code != 308) response.checked()
            val accepted = response.range?.substringAfterLast('-')?.toLong()?.plus(1) ?: 0L
            check(accepted > offset && accepted <= end) { "Drive upload made no progress. Retry." }
            offset = accepted; progress(uri, offset)
        } while (offset < bytes.size)
        throw IOException("Upload completion requires reconciliation. Retry.")
    }
}
