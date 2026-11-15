package com.nadim.bikepart

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Loopback serves real APK bytes through the production HTTP transfer. */
class UpdateApkServer(private val bytes: ByteArray) : AutoCloseable {
    private val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newCachedThreadPool()
    val requests = AtomicInteger()
    val url get() = "http://127.0.0.1:${server.localPort}/bikepart.apk"

    init {
        executor.execute {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: java.io.IOException) { break }
                executor.execute {
                    socket.use {
                        try {
                            val reader = it.getInputStream().bufferedReader()
                            reader.readLine()
                            while (true) {
                                val header = reader.readLine() ?: break
                                if (header.isEmpty()) break
                            }
                            requests.incrementAndGet()
                            val output = it.getOutputStream()
                            output.write(("HTTP/1.1 200 Test\r\nContent-Type: application/vnd.android.package-archive\r\n" +
                                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                            output.write(bytes)
                            output.flush()
                        } catch (_: java.io.IOException) { }
                    }
                }
            }
        }
    }

    override fun close() { server.close(); executor.shutdownNow() }
}
