package com.nadim.bikepart

import com.nadim.bikepart.updates.UpdateHttp
import com.nadim.bikepart.updates.UpdateHttpResponse
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Loopback serves controlled external responses through the production HTTP implementation. */
class UpdateTestServer(private val respond: (String) -> UpdateHttpResponse) : AutoCloseable {
    private val server = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newCachedThreadPool()
    @Volatile var authorizationSeen = false
        private set
    init {
        executor.execute {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: java.io.IOException) { break }
                executor.execute {
                    socket.use {
                        try {
                            val reader = it.getInputStream().bufferedReader()
                            val path = reader.readLine().split(' ')[1]
                            while (true) {
                                val header = reader.readLine() ?: break
                                if (header.isEmpty()) break
                                if (header.startsWith("Authorization:", true)) authorizationSeen = true
                            }
                            val response = respond(URLDecoder.decode(path.substringAfter("?url="), "UTF-8"))
                            val bytes = response.body.toByteArray()
                            val output = it.getOutputStream()
                            output.write("HTTP/1.1 ${response.status} Test\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            output.write(bytes); output.flush()
                        } catch (_: java.io.IOException) { } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                    }
                }
            }
        }
    }
    suspend fun get(url: String, timeoutMillis: Int = 10_000) = UpdateHttp(timeoutMillis).get(
        "http://127.0.0.1:${server.localPort}/?url=" + URLEncoder.encode(url, "UTF-8"))
    override fun close() { server.close(); executor.shutdownNow() }
}
