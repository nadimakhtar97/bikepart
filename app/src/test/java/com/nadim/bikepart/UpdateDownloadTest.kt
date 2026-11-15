package com.nadim.bikepart

import com.nadim.bikepart.updates.ApkArchiveInspector
import com.nadim.bikepart.updates.ApkIdentity
import com.nadim.bikepart.updates.AvailableUpdate
import com.nadim.bikepart.updates.DownloadFailure
import com.nadim.bikepart.updates.DownloadState
import com.nadim.bikepart.updates.InstalledApp
import com.nadim.bikepart.updates.UpdateDownloads
import com.nadim.bikepart.updates.UpdateHttp
import com.nadim.bikepart.updates.sha256
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDownloadTest {
    private val payload = ByteArray(240_000) { (it * 31 % 251).toByte() }
    private val signer = "a".repeat(64)
    private val installed = InstalledApp("com.nadim.bikepart", 1, "1.0", 33, setOf(signer))

    private fun release(code: Long = 2, body: ByteArray = payload, url: String = UPDATE_URL) = AvailableUpdate(
        id = code, versionCode = code, versionName = "1.$code", sizeBytes = body.size.toLong(),
        summary = "Workshop improvements", apkUrl = url, sha256 = sha256(body), minSdk = 33)

    private fun identity(code: Long = 2, packageId: String = "com.nadim.bikepart", minSdk: Int = 33,
        signers: Set<String> = setOf(signer), versionName: String = "1.$code") =
        ApkIdentity(packageId, code, versionName, minSdk, signers)

    private fun inspector(identity: ApkIdentity? = identity()) = ApkArchiveInspector { identity }

    private fun downloads(directory: File, server: ApkServer, identity: ApkIdentity? = identity(),
        space: () -> Long = { Long.MAX_VALUE }): UpdateDownloads = UpdateDownloads(
        directory, installed, inspector(identity),
        transfer = { _, destination, maxBytes, onProgress ->
            UpdateHttp().download(server.url, destination, maxBytes, onProgress)
        },
        availableBytes = { space() },
    )

    private suspend fun UpdateDownloads.await(predicate: (DownloadState) -> Boolean): DownloadState =
        withTimeout(10_000) { state.first(predicate) }

    private fun temporaryDirectory() = kotlin.io.path.createTempDirectory("bikepart-updates").toFile()

    @Test fun verifiedDownloadIsRetainedAcrossReopenAndSupersededCleanly() = runBlocking {
        val directory = temporaryDirectory()
        ApkServer { Reply(payload) }.use { server ->
            val first = downloads(directory, server)
            try {
                first.awaitRestored()
                first.start(release())
                assertTrue(first.await(DownloadState::isReady) is DownloadState.Ready)
                assertEquals(File(directory, "update.apk"), (first.state.value as DownloadState.Ready).apk)
                assertArrayEquals(payload, File(directory, "update.apk").readBytes())
                assertTrue(File(directory, "completed.properties").isFile)
            } finally { first.close() }

            val reopened = downloads(directory, server)
            try {
                reopened.awaitRestored()
                val restored = reopened.state.value
                assertTrue(restored is DownloadState.Ready)
                assertEquals(2L, (restored as DownloadState.Ready).release.versionCode)

                val newerBody = payload.copyOf().also { it[0] = 99 }
                val newer = release(code = 3, body = newerBody)
                reopened.reconcile(newer)
                assertTrue(reopened.state.value is DownloadState.None)
                assertFalse(File(directory, "update.apk").exists())
                assertFalse(File(directory, "completed.properties").exists())

                server.reply = { Reply(newerBody) }
                val upgraded = downloads(directory, server, identity(code = 3))
                try {
                    upgraded.awaitRestored()
                    upgraded.start(newer)
                    assertTrue(upgraded.await(DownloadState::isReady) is DownloadState.Ready)
                    assertEquals(3L, (upgraded.state.value as DownloadState.Ready).release.versionCode)
                } finally { upgraded.close() }
            } finally { reopened.close() }
        }
        directory.deleteRecursively()
        Unit
    }

    @Test fun duplicateTapsStartOneTransfer() = runBlocking {
        val directory = temporaryDirectory()
        ApkServer { Reply(payload, chunkSize = 4_096, chunkMillis = 15) }.use { server ->
            val target = downloads(directory, server)
            try {
                target.awaitRestored()
                target.start(release())
                target.await { it is DownloadState.Downloading }
                target.start(release())
                withTimeout(5_000) { while (server.requests.get() == 0) kotlinx.coroutines.delay(5) }
                assertEquals(1, server.requests.get())
                assertTrue(target.await(DownloadState::isReady) is DownloadState.Ready)
                assertEquals(1, server.requests.get())
            } finally { target.close() }
        }
        directory.deleteRecursively()
        Unit
    }

    @Test fun rapidDuplicateTapsBeforeProgressStillStartOneTransfer() = runBlocking {
        val directory = temporaryDirectory()
        ApkServer { Reply(payload, chunkSize = 4_096, chunkMillis = 10) }.use { server ->
            val target = downloads(directory, server)
            try {
                target.awaitRestored()
                target.start(release())
                target.start(release())
                assertTrue(target.await(DownloadState::isReady) is DownloadState.Ready)
                assertEquals(1, server.requests.get())
            } finally { target.close() }
        }
        directory.deleteRecursively()
        Unit
    }

    @Test fun cancellationLeavesNoInstallablePartialAndRetryStartsOver() = runBlocking {
        val directory = temporaryDirectory()
        ApkServer { Reply(payload, chunkSize = 2_048, chunkMillis = 20) }.use { server ->
            val target = downloads(directory, server)
            try {
                target.awaitRestored()
                target.start(release())
                target.await { it is DownloadState.Downloading && it.downloadedBytes > 0 }
                target.cancel()
                assertTrue(target.await { it is DownloadState.None } is DownloadState.None)
                withTimeout(5_000) { while (File(directory, "update.partial").exists()) kotlinx.coroutines.delay(5) }
                assertFalse(File(directory, "update.partial").exists())
                assertFalse(File(directory, "update.apk").exists())
                assertFalse(File(directory, "completed.properties").exists())

                server.reply = { Reply(payload) }
                target.start(release())
                assertTrue(target.await(DownloadState::isReady) is DownloadState.Ready)
                assertArrayEquals(payload, File(directory, "update.apk").readBytes())
                assertEquals(2, server.requests.get())
            } finally { target.close() }
        }
        directory.deleteRecursively()
        Unit
    }

    @Test fun retryWaitsForCancelledHttpWriterToExit() = runBlocking {
        val directory = temporaryDirectory()
        val entered = CountDownLatch(1)
        val releaseWriter = CountDownLatch(1)
        val requests = AtomicInteger()
        val http = UpdateHttp(openConnection = {
            val attempt = requests.incrementAndGet()
            object : HttpURLConnection(URL("http://localhost/update.apk")) {
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun getResponseCode(): Int {
                    if (attempt == 1) {
                        entered.countDown()
                        check(releaseWriter.await(5, TimeUnit.SECONDS))
                    }
                    return 200
                }
                override fun getContentLengthLong() = payload.size.toLong()
                override fun getInputStream() = ByteArrayInputStream(payload)
            }
        })
        val target = UpdateDownloads(directory, installed, inspector(), transfer = http::download,
            availableBytes = { Long.MAX_VALUE })
        try {
            target.awaitRestored()
            target.start(release())
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            target.cancel()
            target.start(release())
            kotlinx.coroutines.delay(300)
            assertEquals("Retry started before cancelled writer exited", 1, requests.get())
            releaseWriter.countDown()
            assertTrue(target.await(DownloadState::isReady) is DownloadState.Ready)
            assertArrayEquals(payload, File(directory, "update.apk").readBytes())
            assertEquals(2, requests.get())
        } finally {
            releaseWriter.countDown()
            target.close()
            directory.deleteRecursively()
        }
    }

    @Test fun corruptTruncatedOversizedAndMismatchedApksAreRejected() = runBlocking {
        val cases = listOf(
            FailureCase("wrong checksum", identity(), release().copy(sha256 = "c".repeat(64)), { Reply(payload) }, DownloadFailure.Integrity),
            FailureCase("truncated response", identity(), release(),
                { Reply(payload, declaredLength = payload.size + 64L, closeAfter = payload.size / 2) }, DownloadFailure.Integrity),
            FailureCase("oversized response", identity(), release(), { Reply(payload + payload) }, DownloadFailure.Integrity),
            FailureCase("wrong package", identity(packageId = "other.app"), release(), { Reply(payload) }, DownloadFailure.Identity),
            FailureCase("older or equal version", identity(code = 1), release(), { Reply(payload) }, DownloadFailure.Identity),
            FailureCase("incompatible api", identity(minSdk = 34), release(), { Reply(payload) }, DownloadFailure.Identity),
            FailureCase("wrong version name", identity(versionName = "9.9"), release(), { Reply(payload) }, DownloadFailure.Identity),
            FailureCase("wrong signing", identity(signers = setOf("b".repeat(64))), release(), { Reply(payload) }, DownloadFailure.Signing),
            FailureCase("extra signing", identity(signers = setOf(signer, "b".repeat(64))), release(), { Reply(payload) }, DownloadFailure.Signing),
            FailureCase("unverifiable apk", null, release(), { Reply(payload) }, DownloadFailure.Identity),
        )
        for (case in cases) {
            val directory = temporaryDirectory()
            ApkServer { case.reply() }.use { server ->
                val target = downloads(directory, server, case.identity)
                try {
                    target.awaitRestored()
                    target.start(case.offered)
                    val failed = target.await { it is DownloadState.Failed } as DownloadState.Failed
                    assertEquals(case.name, case.expected, failed.reason)
                    assertFalse("${case.name} retained an APK", File(directory, "update.apk").exists())
                    assertFalse("${case.name} retained metadata", File(directory, "completed.properties").exists())
                    assertFalse("${case.name} retained a partial", File(directory, "update.partial").exists())
                } finally { target.close() }
                assertEquals(case.name, 1, server.requests.get())
            }
            directory.deleteRecursively()
        }
    }

    @Test fun networkAndStorageFailuresRecoverOnRetry() = runBlocking {
        val directory = temporaryDirectory()
        var attempts = 0
        ApkServer { if (++attempts == 1) Reply(payload, status = 500) else Reply(payload) }.use { server ->
            var space = Long.MAX_VALUE
            val target = downloads(directory, server, space = { space })
            try {
                target.awaitRestored()
                target.start(release())
                assertEquals(DownloadFailure.Connection, (target.await { it is DownloadState.Failed } as DownloadState.Failed).reason)
                assertFalse(File(directory, "update.apk").exists())

                space = 0
                target.start(release())
                assertEquals(DownloadFailure.Storage,
                    (target.await { it is DownloadState.Failed && it.reason == DownloadFailure.Storage } as DownloadState.Failed).reason)
                assertFalse(File(directory, "update.apk").exists())

                space = Long.MAX_VALUE
                target.start(release())
                assertTrue(target.await(DownloadState::isReady) is DownloadState.Ready)
                assertArrayEquals(payload, File(directory, "update.apk").readBytes())
            } finally { target.close() }
        }
        directory.deleteRecursively()
        Unit
    }

    @Test fun storageFailureDuringWriteIsReportedAndRecovers() = runBlocking {
        val directory = temporaryDirectory()
        ApkServer { Reply(payload) }.use { server ->
            var failWrite = true
            val target = UpdateDownloads(directory, installed, inspector(),
                transfer = { _, destination, maxBytes, onProgress ->
                    if (failWrite) {
                        destination.writeBytes(payload.copyOf(1_000))
                        throw java.io.IOException("No space left on device")
                    }
                    UpdateHttp().download(server.url, destination, maxBytes, onProgress)
                },
                availableBytes = { Long.MAX_VALUE })
            try {
                target.awaitRestored()
                target.start(release())
                assertEquals(DownloadFailure.Storage,
                    (target.await { it is DownloadState.Failed } as DownloadState.Failed).reason)
                assertFalse(File(directory, "update.partial").exists())
                failWrite = false
                target.start(release())
                assertTrue(target.await(DownloadState::isReady) is DownloadState.Ready)
            } finally { target.close() }
        }
        directory.deleteRecursively()
        Unit
    }

    @Test fun staleFailureIsClearedWhenDiscoveryOffersAnotherRelease() = runBlocking {
        val directory = temporaryDirectory()
        ApkServer { Reply(payload) }.use { server ->
            val target = downloads(directory, server, identity(packageId = "other.app"))
            try {
                target.awaitRestored()
                target.start(release())
                assertTrue(target.await { it is DownloadState.Failed } is DownloadState.Failed)
                target.reconcile(release())
                assertTrue(target.state.value is DownloadState.Failed)
                target.reconcile(release(code = 3))
                assertTrue(target.state.value is DownloadState.None)
            } finally { target.close() }
        }
        directory.deleteRecursively()
        Unit
    }

    @Test fun partialAndInvalidRetainedFilesAreCleanedOnReopen() = runBlocking {
        val directory = temporaryDirectory()
        directory.mkdirs()
        File(directory, "update.partial").writeBytes(payload.copyOf(1_000))
        File(directory, "orphan.bin").writeText("leftover")
        File(directory, "completed.properties").writeText("not a valid record")
        File(directory, "update.apk").writeBytes(payload.copyOf(1_000))
        ApkServer { Reply(payload) }.use { server ->
            val target = downloads(directory, server)
            try {
                target.awaitRestored()
                assertTrue(target.state.value is DownloadState.None)
                assertFalse(File(directory, "update.partial").exists())
                assertFalse(File(directory, "orphan.bin").exists())
                assertFalse(File(directory, "completed.properties").exists())
                assertFalse(File(directory, "update.apk").exists())
            } finally { target.close() }
        }
        directory.deleteRecursively()
        Unit
    }

    @Test fun retainedApkIsRevalidatedAndDiscardedWhenDamaged() = runBlocking {
        val directory = temporaryDirectory()
        ApkServer { Reply(payload) }.use { server ->
            val first = downloads(directory, server)
            try {
                first.awaitRestored()
                first.start(release())
                assertTrue(first.await(DownloadState::isReady) is DownloadState.Ready)
            } finally { first.close() }
            File(directory, "update.apk").writeBytes(ByteArray(payload.size))

            val reopened = downloads(directory, server)
            try {
                reopened.awaitRestored()
                assertTrue(reopened.state.value is DownloadState.None)
                assertFalse(File(directory, "update.apk").exists())
                assertFalse(File(directory, "completed.properties").exists())
            } finally { reopened.close() }
        }
        directory.deleteRecursively()
        Unit
    }

    @Test fun installationLocalUpdateStateIsExcludedFromBackups() {
        for (name in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val file = File("src/main/res/xml/$name")
            assertTrue("$name is missing", file.isFile)
            val text = file.readText()
            assertTrue("$name must exclude update downloads", text.contains("domain=\"file\" path=\"app-updates/\""))
        }
    }
}

private val UPDATE_URL = "https://github.com/nadimakhtar97/bikepart/releases/download/v2/bikepart.apk"

private fun DownloadState.isReady() = this is DownloadState.Ready

private class FailureCase(val name: String, val identity: ApkIdentity?, val offered: AvailableUpdate,
    val reply: () -> Reply, val expected: DownloadFailure)

private data class Reply(val body: ByteArray, val status: Int = 200, val declaredLength: Long? = null,
    val closeAfter: Int? = null, val chunkSize: Int = Int.MAX_VALUE, val chunkMillis: Long = 0)

/** Loopback APK server exercising the production HTTP transfer without credentials. */
private class ApkServer(@Volatile var reply: (Int) -> Reply) : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newCachedThreadPool()
    val requests = AtomicInteger()
    val url get() = "http://127.0.0.1:${server.localPort}/bikepart.apk"

    init {
        executor.execute {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                executor.execute {
                    socket.use {
                        try {
                            val reader = it.getInputStream().bufferedReader()
                            reader.readLine()
                            while (true) {
                                val header = reader.readLine() ?: break
                                if (header.isEmpty()) break
                            }
                            val response = reply(requests.incrementAndGet())
                            val output = it.getOutputStream()
                            val length = response.declaredLength ?: response.body.size.toLong()
                            output.write(("HTTP/1.1 ${response.status} Test\r\nContent-Type: application/vnd.android.package-archive\r\n" +
                                "Content-Length: $length\r\nConnection: close\r\n\r\n").toByteArray())
                            if (response.status == 200) {
                                val limit = response.closeAfter ?: response.body.size
                                var sent = 0
                                while (sent < limit) {
                                    val count = minOf(response.chunkSize, limit - sent)
                                    output.write(response.body, sent, count)
                                    output.flush()
                                    sent += count
                                    if (response.chunkMillis > 0) Thread.sleep(response.chunkMillis)
                                }
                            }
                            output.flush()
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    }

    override fun close() { server.close(); executor.shutdownNow() }
}
