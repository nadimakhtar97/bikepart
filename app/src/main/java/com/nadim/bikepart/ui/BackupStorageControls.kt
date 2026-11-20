package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopViewModel
import com.nadim.bikepart.shop.ActivePhoneState
import com.nadim.bikepart.ui.theme.ShopSpacing
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@Composable
internal fun BackupStorageControls(model: ShopViewModel, onInspect: () -> Unit, onCleanup: () -> Unit) {
    val storage by model.backupStorage.collectAsState()
    val status by model.driveStatus.collectAsState()
    val busy by model.driveBusy.collectAsState()
    val complete by model.cleanupComplete.collectAsState()
    val error by model.driveError.collectAsState()
    val progress by model.storageProgress.collectAsState()
    var confirm by rememberSaveable(storage?.fingerprint) { mutableStateOf(false) }
    val canInspect = !busy && status?.target != null && status?.phoneState != ActivePhoneState.Retired
    fun bytes(value: Long) = "${NumberFormat.getIntegerInstance().format(value)} bytes"
    ShopSection(stringResource(R.string.storage_section)) {
        Text(stringResource(R.string.keep_the_latest_seven_complete_captures_plus), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.google_will_ask_for_read_only_drive), style = MaterialTheme.typography.bodyMedium)
        ShopSecondaryButton(onClick = onInspect, enabled = canInspect) { Text(stringResource(R.string.storage_inspect)) }
        progress?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium) }
        if (complete) Text(stringResource(R.string.reviewed_cleanup_finished_protected_recovery_history_is),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        storage?.let { reviewed ->
            Text(stringResource(R.string.current_folder_file_data_drive_revisions_and, bytes(reviewed.bytes)), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.complete_restore_points_protected_eligible_older_points, reviewed.completePoints, reviewed.retainedPoints, reviewed.eligible.size), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.up_to_can_be_removed_shared_photos, bytes(reviewed.reclaimableBytes)), style = MaterialTheme.typography.bodyMedium)
            if (reviewed.eligible.isNotEmpty()) ShopListGroup(showDividers = true) {
                reviewed.eligible.forEachIndexed { index, point ->
                    ShopListRow(
                        title = DateFormat.getDateTimeInstance().format(Date(point.capturedAt)),
                        supporting = "Revision ${point.revision}",
                        dividerAbove = index > 0,
                    )
                }
            }
            if (reviewed.cleanupPending) Text(stringResource(R.string.a_reviewed_cleanup_was_interrupted_continue_to), style = MaterialTheme.typography.bodyMedium)
            if (reviewed.eligible.isNotEmpty() || reviewed.cleanupPending) ShopSecondaryButton(enabled = canInspect, onClick = { confirm = true }) {
                Text(if (reviewed.cleanupPending) "Continue reviewed cleanup" else "Remove eligible older backups")
            } else Text(stringResource(R.string.no_older_backup_history_is_eligible_for), style = MaterialTheme.typography.bodyMedium)
        }
        error?.let { FormError(it) }
    }
    if (confirm && storage != null) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.storage_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                Text("Account: ${storage!!.target.account}\nFolder: ${storage!!.target.folderName}")
                Text(stringResource(R.string.permanently_remove_only_the_reviewed_eligible_history))
                Text(stringResource(R.string.recent_and_daily_history_pending_captures_and))
                Text(stringResource(R.string.cleanup_pauses_if_identity_permissions_or_history))
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { confirm = false; onCleanup() }) { Text(stringResource(R.string.storage_delete_history)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } })
}
