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
    var folderName by rememberSaveable { mutableStateOf(context.getString(R.string.bikepart_shop_backups)) }
    val client = remember(context) { Identity.getAuthorizationClient(context) }
    fun accept(result: com.google.android.gms.auth.api.identity.AuthorizationResult) {
        val token = result.accessToken
        if (token == null) model.driveFailure(context.getString(R.string.google_did_not_grant_drive_access))
        else model.authorizedDrive(selectedAccount, token, action,
            fullMetadataAccess = result.grantedScopes.contains("https://www.googleapis.com/auth/drive.metadata.readonly"))
    }
    val resolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        try { accept(client.getAuthorizationResultFromIntent(result.data)) }
        catch (_: Exception) { model.driveFailure(context.getString(R.string.drive_authorization_was_cancelled_or_failed)) }
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
        }.addOnFailureListener { model.driveFailure(context.getString(R.string.google_authorization_failed_check_google_play)) }
    }
    val chooseAccount = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (email != null) authorize(email, DriveAction.Connect) else model.driveFailure(context.getString(R.string.no_google_account_selected))
    }
    ShopSection(stringResource(R.string.drive_backup_section)) {
        Text(status?.target?.let { stringResource(R.string.account_nfolder_nfolder_id, it.account, it.folderName, it.folderId) } ?: stringResource(R.string.choose_the_owner_s_google_account), style = MaterialTheme.typography.bodyMedium)
        Text(when {
            status?.coveredRevision != null -> stringResource(R.string.verified_backup_revision_captured, requireNotNull(status!!.coveredRevision), DateFormat.getDateTimeInstance().format(Date(status!!.capturedAt!!)))
            else -> stringResource(R.string.drive_no_verified)
        }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        Text(when {
            status?.phoneState == ActivePhoneState.Retired -> stringResource(R.string.installation_retired_use_the_replacement_phone)
            status?.phoneState == ActivePhoneState.Mismatch || status?.takeoverPending == true -> stringResource(R.string.phone_identity_needs_owner_action)
            storageProgress != null -> stringResource(R.string.checking_backup_storage_or_cleaning_reviewed)
            uploading || busy -> stringResource(R.string.uploading_or_preparing_backup)
            status?.failure != null -> stringResource(R.string.backup_failed_action_required_or_retry)
            status?.coveredRevision == revision -> stringResource(R.string.up_to_date)
            else -> stringResource(R.string.pending_backup)
        }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        if (status?.coveredRevision?.let { it < revision } != false) Text(stringResource(R.string.shop_changes_are_waiting_for_backup_changes), style = MaterialTheme.typography.bodyMedium)
        val currentCapture = capture ?: status?.pendingRevision?.let { it to requireNotNull(status?.pendingCapturedAt) }
        if (currentCapture != null) {
            val (capturedRevision, time) = currentCapture
            Text(stringResource(R.string.revision_captured, if (busy || uploading) stringResource(R.string.uploading_capture) else stringResource(R.string.pending_backup), capturedRevision, DateFormat.getDateTimeInstance().format(Date(time))), style = MaterialTheme.typography.bodyMedium)
            if (revision > capturedRevision) Text(stringResource(R.string.newer_shop_changes_are_waiting_for_another), style = MaterialTheme.typography.bodyMedium)
        }
        (error ?: status?.failure)?.let { ErrorSnackbar(it) }
        Text(stringResource(R.string.drive_auto_backup_note), style = MaterialTheme.typography.bodyMedium)
        ShopSecondaryButton(enabled = !busy && !uploading, onClick = { model.setBackupMobileData(status?.allowMobileData != true) }) {
            Text(if (status?.allowMobileData == true) stringResource(R.string.drive_use_wifi_only) else stringResource(R.string.drive_allow_mobile))
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
    ShopSection(stringResource(R.string.restore_section)) {
        if (complete) Text(stringResource(R.string.shop_restored_a_local_recovery_copy_is),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.inspect_a_complete_drive_restore_point_before), style = MaterialTheme.typography.bodyMedium)
        ShopSecondaryButton(onClick = onRefresh, enabled = !busy && status?.target != null) { Text(stringResource(R.string.restore_find_points)) }
        if (points?.isEmpty() == true) Text(stringResource(R.string.no_complete_supported_restore_points_found_incomplete), style = MaterialTheme.typography.bodyMedium)
        points?.forEach { point ->
            ShopSecondaryButton(enabled = !busy, onClick = { model.selectRestorePoint(point) }, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.semantics { this.selected = selected == point }) {
                Text(stringResource(R.string.revision_n_products_sales_unfinished_carts, DateFormat.getDateTimeInstance().format(Date(point.point.capturedAt)), point.point.revision, point.products, point.sales, point.carts, point.photos))
            }
        }
        selected?.let { point ->
            Text(stringResource(R.string.selected_capture, DateFormat.getDateTimeInstance().format(Date(point.point.capturedAt))), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.local_changes_absent_from_this_restore_point), style = MaterialTheme.typography.bodyMedium)
            ShopPrimaryButton(stringResource(R.string.restore_validate), enabled = !busy, onClick = onValidate)
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
