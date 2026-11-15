package com.nadim.bikepart.updates

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import java.io.File

/** Survives screen navigation/configuration changes; performs no automatic or background work. */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = application.packageManager
    private val info = manager.getPackageInfo(application.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    private val installed = InstalledApp(application.packageName, info.longVersionCode,
        info.versionName ?: info.longVersionCode.toString(), Build.VERSION.SDK_INT, signingDigests(info))
    private val downloads = UpdateDownloads(File(application.filesDir, "app-updates"), installed,
        PackageManagerApkInspector(application))
    val updater: AppUpdater = GitHubUpdater(installed, downloads = downloads)

    override fun onCleared() {
        updater.close()
    }
}
