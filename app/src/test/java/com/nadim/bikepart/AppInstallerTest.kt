package com.nadim.bikepart.updates

import org.junit.Assert.*
import org.junit.Test
import java.io.File

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

    @Test
    fun revalidatesApkBeforeHandoffAndRejectsIfMissing() {
        val nonExistent = File("does_not_exist.apk")
        val installer = TestInstaller(installed, FakeInspector(ApkIdentity("com.nadim.bikepart", 2L, "2.0", 33, setOf("signer1"))))
        val result = installer.install(nonExistent, expectedHash = "hash123")
        assertEquals(InstallResult.InvalidApk, result)
        assertNull(installer.launchedWithApk)
    }

    @Test
    fun rejectsIfApkVersionIsNotHigherThanInstalled() {
        val tempApk = File.createTempFile("update", ".apk").apply { writeBytes("dummy content".toByteArray()) }
        try {
            val inspector = FakeInspector(ApkIdentity("com.nadim.bikepart", 1L, "1.0", 33, setOf("signer1")))
            val installer = TestInstaller(installed, inspector)
            val result = installer.install(tempApk, expectedHash = sha256(tempApk))
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
            val result = installer.install(tempApk, expectedHash = sha256(tempApk))
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
            val result = installer.install(tempApk, expectedHash = sha256(tempApk))
            assertEquals(InstallResult.Success, result)
            assertEquals(tempApk, installer.launchedWithApk)
        } finally {
            tempApk.delete()
        }
    }
}
