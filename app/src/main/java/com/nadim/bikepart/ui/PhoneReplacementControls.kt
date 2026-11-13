package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
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
    ShopSection("Active shop phone") {
        Text(when (status?.phoneState) {
            ActivePhoneState.Active -> "This phone is the confirmed active backup writer."
            ActivePhoneState.Mismatch -> "Another phone is active. Stop backups here and use that phone, or review replacement after retiring the old installation."
            ActivePhoneState.Retired -> "This installation is retired. Use the replacement phone for shop work."
            else -> "Active-phone identity is unconfirmed. Check the marker before backup or cleanup."
        }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        status?.writer?.let { Text("Writer generation: $it", style = MaterialTheme.typography.bodyMedium) }
        Text(if (status?.automaticDeletionPaused != false) "Automatic backup deletion is paused while phone identity or takeover is uncertain."
            else "Phone identity is confirmed. Eligible cleanup must check it again before deletion.", style = MaterialTheme.typography.bodyMedium)
        if (status?.takeoverPending == true) Text("Activation may have reached Drive. Check active phone to reconcile it before retrying replacement. Existing recovery history is preserved.", style = MaterialTheme.typography.bodyMedium)
        Text("Stop using the old phone. Verify the replacement and recovery copies, then uninstall the old app or clear its app data. An offline old phone can still edit locally. Drive cannot revoke just that installation's writer access.", style = MaterialTheme.typography.bodyMedium)
        status?.origin?.let { origin ->
            Text("Restored capture: ${DateFormat.getDateTimeInstance().format(Date(origin.capturedAt))} · revision ${origin.revision}\nDataset: ${origin.datasetId}\nCapture: ${origin.captureId}", style = MaterialTheme.typography.bodyMedium)
            origin.account?.let { Text("Restore source: $it · folder ${origin.folderId}\nManifest: ${origin.manifestId}", style = MaterialTheme.typography.bodyMedium) }
        }
        OutlinedButton(onClick = onCheck, enabled = !busy && status?.target != null && status?.phoneState != ActivePhoneState.Retired) { Text("Check active phone") }
        OutlinedButton(onClick = onReview, enabled = !busy && status?.target != null) { Text("Review replacement phone") }
        preview?.let { selected ->
            Text("Activate for account: ${selected.target.account}\nFolder: ${selected.target.folderName}\nFolder ID: ${selected.target.folderId}\nDataset: ${selected.datasetId}\nPrevious writer: ${selected.previousWriter}", style = MaterialTheme.typography.bodyMedium)
            if (selected.origin == null) Text("No restore provenance is recorded for this local shop. Confirm that this is the intended dataset before replacing its backup writer.", style = MaterialTheme.typography.bodyMedium)
            Text("Activation creates a fresh writer generation. A new verified backup is still required; the restored point does not establish this phone's coverage.", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)
                .toggleable(value = retired, enabled = !busy, role = Role.Checkbox, onValueChange = { retired = it }),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = retired, onCheckedChange = null)
                Text("I have retired the old installation", style = MaterialTheme.typography.bodyMedium)
            }
            ShopPrimaryButton("Activate replacement phone", enabled = !busy && retired, onClick = onActivate)
            OutlinedButton(enabled = !busy, onClick = { model.cancelPhoneReplacement() }) { Text("Cancel replacement review") }
        }
        OutlinedButton(enabled = !busy && status?.target != null && status?.phoneState != ActivePhoneState.Retired, onClick = { retireThis = true }) { Text("Retire this installation") }
    }
    if (retireThis) AlertDialog(onDismissRequest = { retireThis = false }, title = { Text("Retire this installation?") },
        text = { Text("Backups from this installation will stop. Verify the replacement and recovery copies, stop using this phone for shop work, then uninstall the app or clear its data. Offline local edits cannot be remotely prevented.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { retireThis = false; model.retirePhone() }) { Text("Retire this phone now") } },
        dismissButton = { TextButton(onClick = { retireThis = false }) { Text("Cancel") } })
}
