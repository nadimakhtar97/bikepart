package com.nadim.bikepart

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nadim.bikepart.updates.AvailableUpdate
import com.nadim.bikepart.updates.DownloadState
import com.nadim.bikepart.updates.InstalledApp
import com.nadim.bikepart.updates.PackageManagerApkInspector
import com.nadim.bikepart.updates.UpdateDownloads
import com.nadim.bikepart.updates.UpdateHttp
import com.nadim.bikepart.updates.sha256
import com.nadim.bikepart.updates.signingDigests
import com.nadim.bikepart.updates.verifyApk
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the real PackageManager path against genuinely signed APKs on the device. */
@RunWith(AndroidJUnit4::class)
class UpdateApkInspectionTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val inspector get() = PackageManagerApkInspector(context)

    private fun installed(versionCode: Long = 0, signers: Set<String> = appSigners(), androidApi: Int = Build.VERSION.SDK_INT) =
        InstalledApp(context.packageName, versionCode, "0.9", androidApi, signers)

    private fun appSigners(): Set<String> = signingDigests(
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES))

    private fun releaseFor(file: File): AvailableUpdate {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        return AvailableUpdate(1, info.longVersionCode, requireNotNull(info.versionName), file.length(),
            "Workshop improvements", "https://github.com/nadimakhtar97/bikepart/releases/download/v1/bikepart.apk",
            sha256(file), requireNotNull(info.applicationInfo).minSdkVersion)
    }

    @Test fun trulySignedInstalledApkVerifiesAndIsRetainedAcrossReopen() = runBlocking {
        val source = File(context.applicationInfo.sourceDir)
        val release = releaseFor(source)
        val directory = File(context.filesDir, "update-test-${System.nanoTime()}")
        UpdateApkServer(source.readBytes()).use { server ->
            val downloads = UpdateDownloads(directory, installed(), inspector,
                transfer = { _, destination, maxBytes, onProgress -> UpdateHttp().download(server.url, destination, maxBytes, onProgress) })
            try {
                downloads.awaitRestored()
                downloads.start(release)
                val ready = withTimeout(30_000) { downloads.state.first { it !is DownloadState.Downloading && it !is DownloadState.None && it !is DownloadState.Verifying } }
                assertTrue("$ready", ready is DownloadState.Ready)
                assertEquals(release.sha256, sha256(File(directory, "update.apk")))
                assertEquals(1, server.requests.get())
            } finally { downloads.close() }

            val reopened = UpdateDownloads(directory, installed(), inspector)
            try {
                reopened.awaitRestored()
                assertTrue(reopened.state.value is DownloadState.Ready)
            } finally { reopened.close() }
        }
        directory.deleteRecursively()
        Unit
    }

    @Test fun realApkIdentityAndSigningMismatchesAreRejected() {
        val source = File(context.applicationInfo.sourceDir)
        val release = releaseFor(source)
        assertNull(verifyApk(source, release, installed(), inspector))
        assertEquals(com.nadim.bikepart.updates.DownloadFailure.Identity, verifyApk(source, release, installed(versionCode = release.versionCode), inspector))
        assertEquals(com.nadim.bikepart.updates.DownloadFailure.Signing,
            verifyApk(source, release, installed(signers = setOf("b".repeat(64))), inspector))
        assertEquals(com.nadim.bikepart.updates.DownloadFailure.Identity,
            verifyApk(source, release, installed(androidApi = release.minSdk - 1), inspector))
        val damaged = File.createTempFile("damaged", ".apk", context.cacheDir).apply { writeBytes(source.readBytes().copyOf(1_024)) }
        try {
            assertEquals(com.nadim.bikepart.updates.DownloadFailure.Integrity, verifyApk(damaged, release, installed(), inspector))
        } finally { damaged.delete() }
    }

    @Test fun aGenuinelySignedApkForAnotherPackageIsRejected() {
        val testApk = File(instrumentation.context.applicationInfo.sourceDir)
        val identity = requireNotNull(inspector.inspect(testApk))
        assertNotEquals(context.packageName, identity.packageId)
        val release = AvailableUpdate(1, identity.versionCode, identity.versionName.orEmpty(), testApk.length(),
            "", "https://github.com/nadimakhtar97/bikepart/releases/download/v1/bikepart.apk", sha256(testApk),
            identity.minSdk)
        assertEquals(com.nadim.bikepart.updates.DownloadFailure.Identity, verifyApk(testApk, release, installed(), inspector))
        assertFalse(testApk.length() == 0L)
    }
}
