package com.nadim.bikepart.updates

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel

/** Survives screen navigation/configuration changes; performs no automatic or background work. */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val info = application.packageManager.getPackageInfo(application.packageName, 0)
    val updater: AppUpdater = GitHubUpdater(InstalledApp(application.packageName, info.longVersionCode,
        info.versionName ?: info.longVersionCode.toString(), Build.VERSION.SDK_INT))
}
