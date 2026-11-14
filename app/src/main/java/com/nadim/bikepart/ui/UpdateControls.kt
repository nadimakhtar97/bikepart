package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.nadim.bikepart.updates.*
import com.nadim.bikepart.ui.theme.ShopSpacing
import java.text.NumberFormat
import androidx.compose.ui.res.stringResource
import com.nadim.bikepart.R

@Composable
internal fun UpdateControls(installed: InstalledApp, state: UpdateState, check: () -> Unit) {
    ShopSectionHeader(stringResource(R.string.application_updates))
    ShopGroup {
        Text(stringResource(R.string.installed_version, installed.versionName, installed.versionCode), style = MaterialTheme.typography.bodyMedium)
        Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
            when (state) {
                UpdateState.Idle -> Text(stringResource(R.string.update_idle))
                UpdateState.Checking -> Text(stringResource(R.string.update_checking_detail))
                UpdateState.NoUpdate -> Text(stringResource(R.string.update_none))
                is UpdateState.Failed -> Text(stringResource(when (state.reason) {
                    UpdateFailure.Connection -> R.string.update_failed
                    UpdateFailure.Timeout -> R.string.update_timeout
                    UpdateFailure.RateLimited -> R.string.update_rate_limited
                }))
                is UpdateState.Available -> {
                    Text(stringResource(R.string.update_available, state.release.versionName), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.update_size,
                        NumberFormat.getNumberInstance().apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(state.release.sizeBytes / 1_048_576.0),
                        NumberFormat.getIntegerInstance().format(state.release.sizeBytes)))
                    Text(state.release.summary.ifBlank { stringResource(R.string.update_no_summary) }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        ShopPrimaryButton(stringResource(when (state) {
            UpdateState.Checking -> R.string.update_checking
            is UpdateState.Failed -> R.string.update_retry
            else -> R.string.update_check
        }), enabled = state != UpdateState.Checking, onClick = check)
    }
}
