package com.nadim.bikepart.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.File

/** Reads identity and signer digests from a downloaded APK without installing it. */
class PackageManagerApkInspector(private val context: Context) : ApkArchiveInspector {
    override fun inspect(file: File): ApkIdentity? {
        val info = try {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (_: Exception) {
            null
        } ?: return null
        val application = info.applicationInfo ?: return null
        return try {
            ApkIdentity(info.packageName, info.longVersionCode, info.versionName, application.minSdkVersion, signingDigests(info))
        } catch (_: Exception) {
            null
        }
    }
}

/** Certificate digests for the installed app and for a candidate APK. */
internal fun signingDigests(info: PackageInfo): Set<String> =
    info.signingInfo?.apkContentsSigners.orEmpty().map { sha256(it.toByteArray()) }.toSet()
