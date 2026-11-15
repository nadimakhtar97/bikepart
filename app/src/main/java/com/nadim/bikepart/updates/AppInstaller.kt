package com.nadim.bikepart.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class InstallResult {
    Success,
    PermissionRequired,
    InvalidApk,
    Failed,
    Cancelled
}

open class AppInstaller(
    val installed: InstalledApp,
    val inspector: ApkArchiveInspector
) {
    suspend fun install(apk: File, release: AvailableUpdate, canProceed: () -> Boolean = { true }): InstallResult {
        val valid = withContext(Dispatchers.IO) {
            try { verifyApk(apk, release, installed, inspector) == null }
            catch (_: Exception) { false }
        }
        if (!valid) return InstallResult.InvalidApk
        if (!canProceed()) return InstallResult.Cancelled
        if (!canRequestPackageInstalls()) {
            return try {
                openInstallPermissionSettings()
                InstallResult.PermissionRequired
            } catch (_: Exception) { InstallResult.Failed }
        }
        return if (launchInstaller(apk)) InstallResult.Success else InstallResult.Failed
    }

    open fun canRequestPackageInstalls(): Boolean = true

    open fun launchInstaller(apk: File): Boolean = false

    open fun openInstallPermissionSettings() {}
}

class AndroidAppInstaller(
    private val context: Context,
    installed: InstalledApp,
    inspector: ApkArchiveInspector
) : AppInstaller(installed, inspector) {

    override fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    override fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun launchInstaller(apk: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
