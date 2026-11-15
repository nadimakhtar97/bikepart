package com.nadim.bikepart.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

enum class InstallResult {
    Success,
    PermissionRequired,
    InvalidApk,
    Failed
}

open class AppInstaller(
    val installed: InstalledApp,
    val inspector: ApkArchiveInspector
) {
    fun install(apk: File, expectedHash: String): InstallResult {
        if (!apk.exists() || !apk.isFile) return InstallResult.InvalidApk
        if (sha256(apk) != expectedHash) return InstallResult.InvalidApk

        val id = inspector.inspect(apk) ?: return InstallResult.InvalidApk
        if (id.packageId != installed.packageId ||
            id.versionCode <= installed.versionCode ||
            id.minSdk > installed.androidApi ||
            id.signerDigests != installed.signers) {
            return InstallResult.InvalidApk
        }

        if (!canRequestPackageInstalls()) {
            openInstallPermissionSettings()
            return InstallResult.PermissionRequired
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
