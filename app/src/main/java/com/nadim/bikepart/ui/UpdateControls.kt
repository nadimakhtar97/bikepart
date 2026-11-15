package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.nadim.bikepart.R
import com.nadim.bikepart.updates.*
import com.nadim.bikepart.ui.theme.ShopSpacing
import java.text.NumberFormat
import kotlin.math.roundToInt

@Composable
internal fun UpdateControls(installed: InstalledApp, state: UpdateState, download: DownloadState,
    check: () -> Unit, startDownload: () -> Unit, cancelDownload: () -> Unit) {
    ShopSectionHeader(stringResource(R.string.application_updates))
    ShopGroup {
        Text(stringResource(R.string.installed_version, installed.versionName, installed.versionCode),
            style = MaterialTheme.typography.bodyMedium)
        val release = download.release ?: (state as? UpdateState.Available)?.release
        Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
            when (download) {
                is DownloadState.Downloading -> {
                    ReleaseSummary(download.release)
                    Text(stringResource(R.string.update_downloading, (download.fraction * 100).roundToInt(),
                        megabytes(download.downloadedBytes), megabytes(download.release.sizeBytes)))
                    LinearProgressIndicator(progress = { download.fraction }, modifier = Modifier.fillMaxWidth())
                }
                is DownloadState.Verifying -> {
                    ReleaseSummary(download.release)
                    Text(stringResource(R.string.update_verifying))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is DownloadState.Ready -> {
                    ReleaseSummary(download.release)
                    Text(stringResource(R.string.update_download_ready), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.update_download_ready_detail), style = MaterialTheme.typography.bodyMedium)
                }
                is DownloadState.Failed -> {
                    ReleaseSummary(download.release)
                    Text(downloadFailure(download.reason))
                }
                DownloadState.None -> when (state) {
                    UpdateState.Idle -> Text(stringResource(R.string.update_idle))
                    UpdateState.Checking -> {
                        Text(stringResource(R.string.update_checking_detail))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    UpdateState.NoUpdate -> Text(stringResource(R.string.update_none))
                    is UpdateState.Failed -> Text(discoveryFailure(state.reason))
                    is UpdateState.Available -> ReleaseSummary(state.release)
                }
            }
        }
        when {
            download is DownloadState.Downloading || download is DownloadState.Verifying ->
                ShopSecondaryButton(onClick = cancelDownload) { Text(stringResource(R.string.update_cancel_download)) }
            release != null && download !is DownloadState.Ready ->
                ShopPrimaryButton(stringResource(if (download is DownloadState.Failed) R.string.update_download_retry
                    else R.string.update_download), enabled = state != UpdateState.Checking, onClick = startDownload)
            else -> ShopPrimaryButton(stringResource(when (state) {
                UpdateState.Checking -> R.string.update_checking
                is UpdateState.Failed -> R.string.update_retry
                else -> R.string.update_check
            }), enabled = state != UpdateState.Checking, onClick = check)
        }
    }
}

@Composable
private fun ReleaseSummary(release: AvailableUpdate) {
    Text(stringResource(R.string.update_available, release.versionName), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.update_size, megabytes(release.sizeBytes),
        NumberFormat.getIntegerInstance().format(release.sizeBytes)))
    Text(release.summary.ifBlank { stringResource(R.string.update_no_summary) }, style = MaterialTheme.typography.bodyMedium)
}

private fun megabytes(bytes: Long) = NumberFormat.getNumberInstance()
    .apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(bytes / 1_048_576.0)

@Composable
private fun discoveryFailure(reason: UpdateFailure) = stringResource(when (reason) {
    UpdateFailure.Connection -> R.string.update_failed
    UpdateFailure.Timeout -> R.string.update_timeout
    UpdateFailure.RateLimited -> R.string.update_rate_limited
})

@Composable
private fun downloadFailure(reason: DownloadFailure) = stringResource(when (reason) {
    DownloadFailure.Connection -> R.string.update_download_failed
    DownloadFailure.Timeout -> R.string.update_download_timeout
    DownloadFailure.Storage -> R.string.update_download_storage
    DownloadFailure.Integrity -> R.string.update_download_integrity
    DownloadFailure.Identity -> R.string.update_download_identity
    DownloadFailure.Signing -> R.string.update_download_signing
    DownloadFailure.Unknown -> R.string.update_download_unknown
})
