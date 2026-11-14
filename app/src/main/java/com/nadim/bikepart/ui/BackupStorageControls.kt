package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    ShopSection("Backup storage") {
        Text("Keep the latest seven complete captures, plus the latest capture on each India calendar day in the current day and previous 29 days. Days without a backup add no recovery point.", style = MaterialTheme.typography.bodyMedium)
        Text("Google will ask for read-only Drive file metadata access to check the complete folder history. If access is declined or uncertain, cleanup stays paused.", style = MaterialTheme.typography.bodyMedium)
        ShopSecondaryButton(onClick = onInspect, enabled = canInspect) { Text("Inspect backup storage") }
        progress?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium) }
        if (complete) Text("Reviewed cleanup finished. Protected recovery history is preserved.",
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        storage?.let { reviewed ->
            Text("Current folder file data: ${bytes(reviewed.bytes)}. Drive revisions and trash may use additional storage.", style = MaterialTheme.typography.bodyMedium)
            Text("${reviewed.completePoints} complete restore points · ${reviewed.retainedPoints} protected · ${reviewed.eligible.size} eligible older points", style = MaterialTheme.typography.bodyMedium)
            Text("Up to ${bytes(reviewed.reclaimableBytes)} can be removed. Shared photos and pending captures stay protected. Files outside verified obsolete backups are left alone.", style = MaterialTheme.typography.bodyMedium)
            reviewed.eligible.forEach { point ->
                Text("Eligible: ${DateFormat.getDateTimeInstance().format(Date(point.capturedAt))} · revision ${point.revision}", style = MaterialTheme.typography.bodyMedium)
            }
            if (reviewed.cleanupPending) Text("A reviewed cleanup was interrupted. Continue to recheck the remaining files and finish safely.", style = MaterialTheme.typography.bodyMedium)
            if (reviewed.eligible.isNotEmpty() || reviewed.cleanupPending) ShopSecondaryButton(enabled = canInspect, onClick = { confirm = true }) {
                Text(if (reviewed.cleanupPending) "Continue reviewed cleanup" else "Remove eligible older backups")
            } else Text("No older backup history is eligible for removal.", style = MaterialTheme.typography.bodyMedium)
        }
        error?.let { FormError(it) }
    }
    if (confirm && storage != null) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Remove older backup history?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                Text("Account: ${storage!!.target.account}\nFolder: ${storage!!.target.folderName}")
                Text("Permanently remove only the reviewed eligible history and its unshared files. Those older captures will no longer be available for recovery.")
                Text("Recent and daily history, pending captures and shared photos stay protected.")
                Text("Cleanup pauses if identity, permissions or history are uncertain.")
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { confirm = false; onCleanup() }) { Text("Delete reviewed history") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
