package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.nadim.bikepart.ShopViewModel
import com.nadim.bikepart.shop.ActivePhoneState
import com.nadim.bikepart.ui.theme.ShopSpacing
import java.text.DateFormat
import java.util.Date

@Composable
internal fun PhoneReplacementControls(model: ShopViewModel, onReview: () -> Unit, onActivate: () -> Unit, onCheck: () -> Unit) {
    val status by model.driveStatus.collectAsState()
    val busy by model.driveBusy.collectAsState()
    val preview by model.phoneReplacement.collectAsState()
    var retired by rememberSaveable(preview?.markerHash, preview?.localWriter) { mutableStateOf(false) }
    var retireThis by rememberSaveable { mutableStateOf(false) }
    ShopSection(stringResource(R.string.phone_active_section)) {
        Text(when (status?.phoneState) {
            ActivePhoneState.Active -> "This phone is the confirmed active backup writer."
            ActivePhoneState.Mismatch -> "Another phone is active. Stop backups here and use that phone, or review replacement after retiring the old installation."
            ActivePhoneState.Retired -> "This installation is retired. Use the replacement phone for shop work."
            else -> "Active-phone identity is unconfirmed. Check the marker before backup or cleanup."
        }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        status?.writer?.let { Text(stringResource(R.string.writer_generation, it), style = MaterialTheme.typography.bodyMedium) }
        Text(if (status?.automaticDeletionPaused != false) "Automatic backup deletion is paused while phone identity or takeover is uncertain."
            else "Phone identity is confirmed. Eligible cleanup must check it again before deletion.", style = MaterialTheme.typography.bodyMedium)
        if (status?.takeoverPending == true) Text(stringResource(R.string.activation_may_have_reached_drive_check_active), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.stop_using_the_old_phone_verify_the), style = MaterialTheme.typography.bodyMedium)
        status?.origin?.let { origin ->
            Text("Restored capture: ${DateFormat.getDateTimeInstance().format(Date(origin.capturedAt))} · revision ${origin.revision}\nDataset: ${origin.datasetId}\nCapture: ${origin.captureId}", style = MaterialTheme.typography.bodyMedium)
            origin.account?.let { Text("Restore source: $it · folder ${origin.folderId}\nManifest: ${origin.manifestId}", style = MaterialTheme.typography.bodyMedium) }
        }
        ShopSecondaryButton(onClick = onCheck, enabled = !busy && status?.target != null && status?.phoneState != ActivePhoneState.Retired) { Text(stringResource(R.string.phone_check_active)) }
        ShopSecondaryButton(onClick = onReview, enabled = !busy && status?.target != null) { Text(stringResource(R.string.phone_review_replacement)) }
        preview?.let { selected ->
            Text("Activate for account: ${selected.target.account}\nFolder: ${selected.target.folderName}\nFolder ID: ${selected.target.folderId}\nDataset: ${selected.datasetId}\nPrevious writer: ${selected.previousWriter}", style = MaterialTheme.typography.bodyMedium)
            if (selected.origin == null) Text(stringResource(R.string.no_restore_provenance_is_recorded_for_this), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.activation_creates_a_fresh_writer_generation_a), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)
                .toggleable(value = retired, enabled = !busy, role = Role.Checkbox, onValueChange = { retired = it }),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = retired, onCheckedChange = null)
                Text(stringResource(R.string.phone_retired_checkbox), style = MaterialTheme.typography.bodyMedium)
            }
            ShopPrimaryButton(stringResource(R.string.phone_activate_replacement), enabled = !busy && retired, onClick = onActivate)
            ShopSecondaryButton(enabled = !busy, onClick = { model.cancelPhoneReplacement() }) { Text(stringResource(R.string.phone_cancel_replacement)) }
        }
        ShopSecondaryButton(enabled = !busy && status?.target != null && status?.phoneState != ActivePhoneState.Retired, onClick = { retireThis = true }) { Text(stringResource(R.string.phone_retire_this)) }
    }
    if (retireThis) AlertDialog(onDismissRequest = { retireThis = false }, title = { Text(stringResource(R.string.phone_retire_dialog_title)) },
        text = { Text(stringResource(R.string.phone_retire_dialog_body)) },
        confirmButton = { TextButton(enabled = !busy, onClick = { retireThis = false; model.retirePhone() }) { Text(stringResource(R.string.phone_retire_now)) } },
        dismissButton = { TextButton(onClick = { retireThis = false }) { Text(stringResource(R.string.cancel)) } })
}
