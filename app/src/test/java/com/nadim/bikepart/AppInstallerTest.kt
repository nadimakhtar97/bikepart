package com.nadim.bikepart.updates

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlinx.coroutines.runBlocking

class AppInstallerTest {
    private val installed = InstalledApp("com.nadim.bikepart", 1L, "1.0", 33, setOf("signer1"))

    private class FakeInspector(private val validApk: ApkIdentity?) : ApkArchiveInspector {
        override fun inspect(file: File): ApkIdentity? = validApk
    }

    private class TestInstaller(
        installed: InstalledApp,
        inspector: ApkArchiveInspector,
        var canInstall: Boolean = true
    ) : AppInstaller(installed, inspector) {
        var launchedWithApk: File? = null
        var openedSettings: Boolean = false

        override fun canRequestPackageInstalls(): Boolean = canInstall

        override fun launchInstaller(apk: File): Boolean {
            launchedWithApk = apk
            return true
        }

        override fun openInstallPermissionSettings() {
            openedSettings = true
        }
    }

    private fun release(file: File, code: Long = 2L) = AvailableUpdate(1, code, if (code == 1L) "1.0" else "2.0", file.length(), "", "url", if (file.exists()) sha256(file) else "hash123", 33)

    @Test
    fun revalidatesApkBeforeHandoffAndRejectsIfMissing() {
        val nonExistent = File("does_not_exist.apk")
        val installer = TestInstaller(installed, FakeInspector(ApkIdentity("com.nadim.bikepart", 2L, "2.0", 33, setOf("signer1"))))
        val result = runBlocking { installer.install(nonExistent, release(nonExistent)) }
        assertEquals(InstallResult.InvalidApk, result)
        assertNull(installer.launchedWithApk)
    }

    @Test
    fun rejectsIfApkVersionIsNotHigherThanInstalled() {
        val tempApk = File.createTempFile("update", ".apk").apply { writeBytes("dummy content".toByteArray()) }
        try {
            val inspector = FakeInspector(ApkIdentity("com.nadim.bikepart", 1L, "1.0", 33, setOf("signer1")))
            val installer = TestInstaller(installed, inspector)
            val result = runBlocking { installer.install(tempApk, release(tempApk)) }
            assertEquals(InstallResult.InvalidApk, result)
            assertNull(installer.launchedWithApk)
        } finally {
            tempApk.delete()
        }
    }

    @Test
    fun requestsPermissionWhenNotGranted() {
        val tempApk = File.createTempFile("update", ".apk").apply { writeBytes("dummy content".toByteArray()) }
        try {
            val inspector = FakeInspector(ApkIdentity("com.nadim.bikepart", 2L, "2.0", 33, setOf("signer1")))
            val installer = TestInstaller(installed, inspector, canInstall = false)
            val result = runBlocking { installer.install(tempApk, release(tempApk)) }
            assertEquals(InstallResult.PermissionRequired, result)
            assertTrue(installer.openedSettings)
            assertNull(installer.launchedWithApk)
        } finally {
            tempApk.delete()
        }
    }

    @Test
    fun launchesInstallerWhenValidAndPermissionGranted() {
        val tempApk = File.createTempFile("update", ".apk").apply { writeBytes("dummy content".toByteArray()) }
        try {
            val inspector = FakeInspector(ApkIdentity("com.nadim.bikepart", 2L, "2.0", 33, setOf("signer1")))
            val installer = TestInstaller(installed, inspector, canInstall = true)
            val result = runBlocking { installer.install(tempApk, release(tempApk)) }
            assertEquals(InstallResult.Success, result)
            assertEquals(tempApk, installer.launchedWithApk)
        } finally {
            tempApk.delete()
        }
    }
    @Test
    fun rejectsMetadataMismatchAndCancelsHandoffWhenWorkResumes() = runBlocking {
        val apk = File.createTempFile("update", ".apk").apply { writeText("valid bytes") }
        try {
            val inspector = FakeInspector(ApkIdentity("com.nadim.bikepart", 2, "2.0", 33, setOf("signer1")))
            val installer = TestInstaller(installed, inspector)
            assertEquals(InstallResult.InvalidApk, installer.install(apk, release(apk).copy(versionName = "different")))
            assertEquals(InstallResult.Cancelled, installer.install(apk, release(apk)) { false })
            assertNull(installer.launchedWithApk)
        } finally { apk.delete() }
    }

}
