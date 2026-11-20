package com.nadim.bikepart.ui

import android.accounts.Account
import android.accounts.AccountManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.nadim.bikepart.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.nadim.bikepart.ShopViewModel
import com.nadim.bikepart.DriveAction
import com.nadim.bikepart.shop.ActivePhoneState
import java.text.DateFormat
import java.util.Date

@Composable
internal fun DriveBackupControls(model: ShopViewModel, revision: Long) {
    val context = LocalContext.current
    val status by model.driveStatus.collectAsState()
    val busy by model.driveBusy.collectAsState()
    val uploading by model.driveUploading.collectAsState()
    val capture by model.backgroundCapture.collectAsState()
    val storageProgress by model.storageProgress.collectAsState()
    val error by model.driveError.collectAsState()
    val folders by model.driveFolders.collectAsState()
    val account by model.driveAccount.collectAsState()
    var selectedAccount by rememberSaveable { mutableStateOf("") }
    var action by rememberSaveable { mutableStateOf(DriveAction.Connect) }
    var folderName by rememberSaveable { mutableStateOf("Bikepart shop backups") }
    val client = remember(context) { Identity.getAuthorizationClient(context) }
    fun accept(result: com.google.android.gms.auth.api.identity.AuthorizationResult) {
        val token = result.accessToken
        if (token == null) model.driveFailure("Google did not grant Drive access. Try connecting again.")
        else model.authorizedDrive(selectedAccount, token, action,
            fullMetadataAccess = result.grantedScopes.contains("https://www.googleapis.com/auth/drive.metadata.readonly"))
    }
    val resolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        try { accept(client.getAuthorizationResultFromIntent(result.data)) }
        catch (_: Exception) { model.driveFailure("Drive authorization was cancelled or failed. Reconnect to try again.") }
    }
    fun authorize(email: String, requested: DriveAction) {
        selectedAccount = email; action = requested
        model.driveAuthorizing()
        val scopes = mutableListOf(Scope("https://www.googleapis.com/auth/drive.file"))
        if (requested == DriveAction.InspectStorage || requested == DriveAction.CleanupStorage)
            scopes += Scope("https://www.googleapis.com/auth/drive.metadata.readonly")
        val request = AuthorizationRequest.builder().setAccount(Account(email, "com.google"))
            .setRequestedScopes(scopes).build()
        client.authorize(request).addOnSuccessListener { result ->
            if (result.hasResolution()) resolution.launch(IntentSenderRequest.Builder(requireNotNull(result.pendingIntent)).build()) else accept(result)
        }.addOnFailureListener { model.driveFailure("Google authorization failed. Check Google Play services and reconnect.") }
    }
    val chooseAccount = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (email != null) authorize(email, DriveAction.Connect) else model.driveFailure("No Google account selected.")
    }
    ShopSection(stringResource(R.string.drive_backup_section)) {
        Text(status?.target?.let { "Account: ${it.account}\nFolder: ${it.folderName}\nFolder ID: ${it.folderId}" } ?: "Choose the owner's Google account and a dedicated visible Drive folder.", style = MaterialTheme.typography.bodyMedium)
        Text(when {
            status?.coveredRevision != null -> "Verified backup · revision ${status!!.coveredRevision} · captured ${DateFormat.getDateTimeInstance().format(Date(status!!.capturedAt!!))}"
            else -> "No verified Drive backup for this connection."
        }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        Text(when {
            status?.phoneState == ActivePhoneState.Retired -> "Installation retired · use the replacement phone"
            status?.phoneState == ActivePhoneState.Mismatch || status?.takeoverPending == true -> "Phone identity needs owner action"
            storageProgress != null -> "Checking backup storage or cleaning reviewed history…"
            uploading || busy -> "Uploading or preparing backup…"
            status?.failure != null -> "Backup failed · action required or retry pending"
            status?.coveredRevision == revision -> "Up to date"
            else -> "Pending backup"
        }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        if (status?.coveredRevision?.let { it < revision } != false) Text(stringResource(R.string.shop_changes_are_waiting_for_backup_changes), style = MaterialTheme.typography.bodyMedium)
        val currentCapture = capture ?: status?.pendingRevision?.let { it to requireNotNull(status?.pendingCapturedAt) }
        if (currentCapture != null) {
            val (capturedRevision, time) = currentCapture
            Text("${if (busy || uploading) "Uploading capture" else "Pending backup"} · revision $capturedRevision · captured ${DateFormat.getDateTimeInstance().format(Date(time))}.", style = MaterialTheme.typography.bodyMedium)
            if (revision > capturedRevision) Text(stringResource(R.string.newer_shop_changes_are_waiting_for_another), style = MaterialTheme.typography.bodyMedium)
        }
        (error ?: status?.failure)?.let { FormError(it) }
        Text(stringResource(R.string.drive_auto_backup_note), style = MaterialTheme.typography.bodyMedium)
        ShopSecondaryButton(enabled = !busy && !uploading, onClick = { model.setBackupMobileData(status?.allowMobileData != true) }) {
            Text(if (status?.allowMobileData == true) "Use Wi-Fi only" else "Allow mobile data for automatic backups")
        }
        ShopSecondaryButton(enabled = !busy, onClick = {
            chooseAccount.launch(AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null))
        }) { Text(stringResource(R.string.drive_connect_repair)) }
        if (account != null) {
            Text(stringResource(R.string.choose_a_folder_for_only_folders_granted, account.toString()), style = MaterialTheme.typography.bodyMedium)
            folders.forEach { folder -> ShopSecondaryButton(enabled = !busy, onClick = { model.selectDriveFolder(folder) }) { Text(stringResource(R.string.formatted_text_2, folder.name, folder.id)) } }
            ShopTextField(
                folderName,
                { folderName = it },
                stringResource(R.string.drive_new_folder_label),
                enabled = !busy
            )
            ShopSecondaryButton(enabled = !busy && folderName.isNotBlank(), onClick = { model.createDriveFolder(folderName) }) { Text(stringResource(R.string.drive_create_folder)) }
        }
        ShopPrimaryButton(stringResource(R.string.drive_back_up_now), enabled = !busy && status?.target != null && status?.phoneState != ActivePhoneState.Retired && status?.phoneState != ActivePhoneState.Mismatch && status?.takeoverPending != true,
            onClick = { authorize(status!!.target!!.account, DriveAction.Publish) })
        Text(stringResource(R.string.drive_manual_edit_note), style = MaterialTheme.typography.bodyMedium)
    }
    PhoneReplacementControls(model, onReview = { authorize(status!!.target!!.account, DriveAction.ReviewPhone) },
        onActivate = { authorize(status!!.target!!.account, DriveAction.ActivatePhone) },
        onCheck = { authorize(status!!.target!!.account, DriveAction.CheckPhone) })
    BackupStorageControls(model, onInspect = { authorize(status!!.target!!.account, DriveAction.InspectStorage) },
        onCleanup = { authorize(status!!.target!!.account, DriveAction.CleanupStorage) })
    RestorePreviewControls(model,
        onRefresh = { authorize(status!!.target!!.account, DriveAction.ListRestorePoints) },
        onValidate = { authorize(status!!.target!!.account, DriveAction.ValidateRestore) })
}

@Composable
private fun RestorePreviewControls(model: ShopViewModel, onRefresh: () -> Unit, onValidate: () -> Unit) {
    val status by model.driveStatus.collectAsState()
    val busy by model.driveBusy.collectAsState()
    val points by model.restorePoints.collectAsState()
    val selected by model.selectedRestore.collectAsState()
    val validated by model.restoreValidated.collectAsState()
    val complete by model.restoreComplete.collectAsState()
    val shopBusy by model.busy.collectAsState()
    val backupBusy by model.backupBusy.collectAsState()
    var confirm by rememberSaveable { mutableStateOf(false) }
    val error by model.driveError.collectAsState()
    ShopSection(stringResource(R.string.restore_section)) {
        if (complete) Text(stringResource(R.string.shop_restored_a_local_recovery_copy_is),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.inspect_a_complete_drive_restore_point_before), style = MaterialTheme.typography.bodyMedium)
        ShopSecondaryButton(onClick = onRefresh, enabled = !busy && status?.target != null) { Text(stringResource(R.string.restore_find_points)) }
        if (points?.isEmpty() == true) Text(stringResource(R.string.no_complete_supported_restore_points_found_incomplete), style = MaterialTheme.typography.bodyMedium)
        points?.forEach { point ->
            ShopSecondaryButton(enabled = !busy, onClick = { model.selectRestorePoint(point) }, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.semantics { this.selected = selected == point }) {
                Text("${DateFormat.getDateTimeInstance().format(Date(point.point.capturedAt))} · revision ${point.point.revision}\n${point.products} products · ${point.sales} sales · ${point.carts} unfinished carts · ${point.photos} photos")
            }
        }
        selected?.let { point ->
            Text(stringResource(R.string.selected_capture, DateFormat.getDateTimeInstance().format(Date(point.point.capturedAt))), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.local_changes_absent_from_this_restore_point), style = MaterialTheme.typography.bodyMedium)
            ShopPrimaryButton(stringResource(R.string.restore_validate), enabled = !busy, onClick = onValidate)
            error?.let { FormError(it) }
            if (validated) Text(stringResource(R.string.validation_passed_this_copy_is_complete_and),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
            if (validated) ShopPrimaryButton(stringResource(R.string.restore_replace), enabled = !busy && !shopBusy && !backupBusy,
                onClick = { confirm = true })
        }
    }
    if (confirm && validated) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.restore_dialog_title)) },
        text = { Text(stringResource(R.string.restore_dialog_body)) },
        confirmButton = { TextButton(enabled = !busy && !shopBusy && !backupBusy, onClick = { confirm = false; model.activateSelectedRestore() }) { Text(stringResource(R.string.restore_replace_now)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
