package com.nadim.bikepart

import com.nadim.bikepart.updates.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

class UpdateDiscoveryTest {
    @Test fun highestAndroidVersionWinsRatherThanGithubOrdering() = runBlocking {
        val updater = GitHubUpdater(InstalledApp("com.nadim.bikepart", 1, "1.0", 33)) { url ->
            when {
                url.contains("api.github.com") -> UpdateHttpResponse(200, """[${release(2)},${release(9)}]""")
                url.endsWith("update.json") -> UpdateHttpResponse(200, metadata(if (url.contains("/v9/")) 9 else 2))
                else -> UpdateHttpResponse(200, "${"a".repeat(64)}  bikepart.apk\n${"b".repeat(64)}  bikepart.aab\n")
            }
        }
        updater.check()
        assertEquals(9L, (updater.state.value as UpdateState.Available).release.versionCode)
    }
    @Test fun onlyCompleteConsistentCompatibleStableReleasesAreEligible() = runBlocking {
        val invalid = listOf(
            release(2).replace("\"draft\":false", "\"draft\":true"),
            release(2).replace("\"prerelease\":false", "\"prerelease\":true"),
            release(2).replace("bikepart.aab", "missing.aab"),
            release(2).replace("\"size\":1024", "\"size\":1"),
            release(2).replace("\"name\":\"bikepart.apk\"", "\"digest\":\"sha256:bad\",\"name\":\"bikepart.apk\""),
        )
        for (entry in invalid) {
            val updater = GitHubUpdater(InstalledApp("com.nadim.bikepart", 1, "1", 33)) { url ->
                UpdateHttpResponse(200, when {
                    url.contains("api.github.com") -> "[$entry]"
                    url.endsWith("update.json") -> metadata(2)
                    else -> "${"a".repeat(64)}  bikepart.apk\n${"b".repeat(64)}  bikepart.aab\n"
                })
            }
            updater.check()
            assertEquals(entry, UpdateState.NoUpdate, updater.state.value)
        }
        for (body in listOf("{}", "not json", metadata(2).replace("\"formatVersion\":1", "\"formatVersion\":2"),
            metadata(2).replace("\"versionCode\":2", "\"versionCode\":\"2\""),
            metadata(2).replace("\"versionCode\":2", "\"versionCode\":2.5"),
            metadata(2).replace("\"minSdk\":33", "\"minSdk\":34"),
            metadata(1), metadata(0), metadata(2).replace("\"versionName\":\"1.2\"", "\"versionName\":123"),
            metadata(2).replace("com.nadim.bikepart", "other.app"),
            metadata(2).replace("a".repeat(64), "invalid"))) {
            val updater = GitHubUpdater(InstalledApp("com.nadim.bikepart", 1, "1", 33)) { url ->
                UpdateHttpResponse(200, when {
                    url.contains("api.github.com") -> "[${release(2)}]"
                    url.endsWith("update.json") -> body
                    else -> "${"a".repeat(64)}  bikepart.apk\n${"b".repeat(64)}  bikepart.aab\n"
                })
            }
            updater.check()
            assertEquals(body, UpdateState.NoUpdate, updater.state.value)
        }
    }
    @Test fun paginatesAllStableReleasesAndUsesRealHttpWithoutCredentials() = runBlocking {
        UpdateTestServer { url ->
            when {
                url.contains("&page=1") -> UpdateHttpResponse(200, "[" + List(100) { release(2) }.joinToString() + "]")
                url.contains("&page=2") -> UpdateHttpResponse(200, "[${release(9)}]")
                url.endsWith("update.json") -> UpdateHttpResponse(200, metadata(if (url.contains("/v9/")) 9 else 2))
                else -> UpdateHttpResponse(200, "${"a".repeat(64)}  bikepart.apk\n${"b".repeat(64)}  bikepart.aab\n")
            }
        }.use { server ->
            val updater = GitHubUpdater(InstalledApp("com.nadim.bikepart", 1, "1", 33), get = server::get)
            updater.check()
            assertEquals(9L, (updater.state.value as UpdateState.Available).release.versionCode)
            assertEquals("Workshop improvements", (updater.state.value as UpdateState.Available).release.summary)
            assertFalse(server.authorizationSeen)
        }
    }

    @Test fun emptyOfflineRateLimitedAndTimedOutChecksRemainRetryable() = runBlocking {
        var status = 429
        var body = "[]"
        UpdateTestServer { UpdateHttpResponse(status, body) }.use { server ->
            val updater = GitHubUpdater(InstalledApp("com.nadim.bikepart", 1, "1", 33), get = server::get)
            for (error in listOf(429, 403, 500)) {
                status = error; updater.check()
                assertTrue(updater.state.value is UpdateState.Failed)
            }
            status = 200; body = "invalid"; updater.check()
            assertTrue(updater.state.value is UpdateState.Failed)
            body = "[]"; updater.check()
            assertEquals(UpdateState.NoUpdate, updater.state.value)
        }
        UpdateTestServer { Thread.sleep(300); UpdateHttpResponse(200, "[]") }.use { server ->
            val updater = GitHubUpdater(InstalledApp("com.nadim.bikepart", 1, "1", 33)) { server.get(it, 30) }
            updater.check()
            assertTrue(updater.state.value is UpdateState.Failed)
        }
        val updater = GitHubUpdater(InstalledApp("com.nadim.bikepart", 1, "1", 33)) { throw java.io.IOException("offline") }
        updater.check()
        assertTrue(updater.state.value is UpdateState.Failed)
    }

    @Test fun concurrentChecksShareOneAttemptAndCancellationAllowsRetry() = runBlocking {
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val finish = kotlinx.coroutines.CompletableDeferred<Unit>()
        var requests = 0
        val updater = GitHubUpdater(InstalledApp("com.nadim.bikepart", 1, "1", 33)) {
            requests++; entered.complete(Unit); finish.await(); UpdateHttpResponse(200, "[]")
        }
        val first = launch { updater.check() }
        entered.await()
        updater.check()
        assertEquals(UpdateState.Checking, updater.state.value)
        assertEquals(1, requests)
        first.cancel(); first.join()
        assertEquals(UpdateState.Idle, updater.state.value)
        finish.complete(Unit); updater.check()
        assertEquals(UpdateState.NoUpdate, updater.state.value)
    }
    @Test fun cancellingRealHttpDisconnectsATricklingResponseImmediately() = runBlocking {
        val server = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val started = java.util.concurrent.CountDownLatch(1)
        val disconnected = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val output = socket.getOutputStream()
                    output.write("HTTP/1.1 200 OK\r\nContent-Length: 1000000\r\nConnection: close\r\n\r\n".toByteArray())
                    repeat(100) {
                        output.write('x'.code); output.flush(); started.countDown(); Thread.sleep(100)
                    }
                }
            } catch (_: java.io.IOException) { disconnected.countDown() }
            catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        try {
            val job = launch { UpdateHttp().get("http://127.0.0.1:${server.localPort}/") }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { assertTrue(started.await(3, java.util.concurrent.TimeUnit.SECONDS)) }
            job.cancel()
            kotlinx.coroutines.withTimeout(1_000) { job.join() }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { assertTrue("Socket remained open after cancellation", disconnected.await(2, java.util.concurrent.TimeUnit.SECONDS)) }
        } finally { server.close(); executor.shutdownNow() }
    }
    @Test fun missingMetadataAndContradictoryChecksumNeverOfferAnUpdate() = runBlocking {
        for (missing in listOf(true, false)) {
            UpdateTestServer { url ->
                when {
                    url.contains("api.github.com") -> UpdateHttpResponse(200, "[${release(2)}]")
                    url.endsWith("update.json") -> UpdateHttpResponse(if (missing) 404 else 200, metadata(2))
                    else -> UpdateHttpResponse(200, "${"b".repeat(64)}  bikepart.apk\n${"b".repeat(64)}  bikepart.aab\n")
                }
            }.use { server ->
                val updater = GitHubUpdater(InstalledApp("com.nadim.bikepart", 1, "1", 33), get = server::get)
                updater.check()
                assertEquals(UpdateState.NoUpdate, updater.state.value)
            }
        }
    }
    private fun release(code: Int) = """{"id":$code,"draft":false,"prerelease":false,"body":"## Workshop improvements","assets":[${listOf("bikepart.apk", "bikepart.aab", "SHA256SUMS.txt", "update.json").joinToString { """{"name":"$it","size":1024,"state":"uploaded","browser_download_url":"https://github.com/nadimakhtar97/bikepart/releases/download/v$code/$it"}""" }}]}"""
    private fun metadata(code: Int) = """{"formatVersion":1,"packageId":"com.nadim.bikepart","versionCode":$code,"versionName":"1.$code","minSdk":33,"apkAssetName":"bikepart.apk","sizeBytes":1024,"sha256":"${"a".repeat(64)}"}"""
}
