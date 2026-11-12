package com.nadim.bikepart.ui

import android.accounts.Account
import android.accounts.AccountManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import java.text.DateFormat
import java.util.Date

@Composable
internal fun DriveBackupControls(model: ShopViewModel, revision: Long) {
    val context = LocalContext.current
    val status by model.driveStatus.collectAsState()
    val busy by model.driveBusy.collectAsState()
    val uploading by model.driveUploading.collectAsState()
    val capture by model.backgroundCapture.collectAsState()
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
        else model.authorizedDrive(selectedAccount, token, action)
    }
    val resolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        try { accept(client.getAuthorizationResultFromIntent(result.data)) }
        catch (_: Exception) { model.driveFailure("Drive authorization was cancelled or failed. Reconnect to try again.") }
    }
    fun authorize(email: String, requested: DriveAction) {
        selectedAccount = email; action = requested
        model.driveAuthorizing()
        val request = AuthorizationRequest.builder().setAccount(Account(email, "com.google"))
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.file"))).build()
        client.authorize(request).addOnSuccessListener { result ->
            if (result.hasResolution()) resolution.launch(IntentSenderRequest.Builder(requireNotNull(result.pendingIntent)).build()) else accept(result)
        }.addOnFailureListener { model.driveFailure("Google authorization failed. Check Google Play services and reconnect.") }
    }
    val chooseAccount = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (email != null) authorize(email, DriveAction.Connect) else model.driveFailure("No Google account selected.")
    }
    ShopSection("Google Drive backup") {
        Text(status?.target?.let { "Account: ${it.account}\nFolder: ${it.folderName}\nFolder ID: ${it.folderId}" } ?: "Choose the owner's Google account and a dedicated visible Drive folder.", style = MaterialTheme.typography.bodyMedium)
        Text(when {
            status?.coveredRevision != null -> "Verified backup · revision ${status!!.coveredRevision} · captured ${DateFormat.getDateTimeInstance().format(Date(status!!.capturedAt!!))}"
            else -> "No verified Drive backup for this connection."
        }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        Text(when {
            uploading || busy -> "Uploading or preparing backup…"
            status?.failure != null -> "Backup failed · action required or retry pending"
            status?.coveredRevision == revision -> "Up to date"
            else -> "Pending backup"
        }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        if (status?.coveredRevision?.let { it < revision } != false) Text("Shop changes are waiting for backup. Changes held only on a lost phone cannot be recovered.", style = MaterialTheme.typography.bodyMedium)
        val currentCapture = capture ?: status?.pendingRevision?.let { it to requireNotNull(status?.pendingCapturedAt) }
        if (currentCapture != null) {
            val (capturedRevision, time) = currentCapture
            Text("${if (busy || uploading) "Uploading capture" else "Pending backup"} · revision $capturedRevision · captured ${DateFormat.getDateTimeInstance().format(Date(time))}.", style = MaterialTheme.typography.bodyMedium)
            if (revision > capturedRevision) Text("Newer shop changes are waiting for another backup.", style = MaterialTheme.typography.bodyMedium)
        }
        (error ?: status?.failure)?.let { FormError(it) }
        Text("Automatic backups use Wi-Fi / unmetered networks by default. Android may delay work to save battery. Timing and recovery of changes still on this phone are not guaranteed.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(enabled = !busy && !uploading, onClick = { model.setBackupMobileData(status?.allowMobileData != true) }) {
            Text(if (status?.allowMobileData == true) "Use Wi-Fi only" else "Allow mobile data for automatic backups")
        }
        OutlinedButton(enabled = !busy, onClick = {
            chooseAccount.launch(AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null))
        }) { Text("Connect or repair Drive") }
        if (account != null) {
            Text("Choose a folder for $account. Only folders granted to this app are listed.", style = MaterialTheme.typography.bodyMedium)
            folders.forEach { folder -> OutlinedButton(enabled = !busy, onClick = { model.selectDriveFolder(folder) }) { Text("${folder.name} · ${folder.id}") } }
            OutlinedTextField(value = folderName, onValueChange = { folderName = it }, enabled = !busy, label = { Text("New dedicated folder name") })
            OutlinedButton(enabled = !busy && folderName.isNotBlank(), onClick = { model.createDriveFolder(folderName) }) { Text("Create and use folder") }
        }
        ShopPrimaryButton("Back up now", enabled = !busy && status?.target != null, onClick = { authorize(status!!.target!!.account, DriveAction.Publish) })
        Text("Manual Drive-file edits do not edit the shop. Retire the previous phone before activating a replacement for shop work and backups.", style = MaterialTheme.typography.bodyMedium)
    }
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
    val error by model.driveError.collectAsState()
    ShopSection("Restore preview") {
        Text("Inspect a complete Drive restore point before deciding to replace this phone's shop.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onRefresh, enabled = !busy && status?.target != null) { Text("Find restore points") }
        if (points?.isEmpty() == true) Text("No complete supported restore points found. Incomplete, damaged or inaccessible copies are excluded.", style = MaterialTheme.typography.bodyMedium)
        points?.forEach { point ->
            OutlinedButton(enabled = !busy, onClick = { model.selectRestorePoint(point) }, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.semantics { this.selected = selected == point }) {
                Text("${DateFormat.getDateTimeInstance().format(Date(point.point.capturedAt))} · revision ${point.point.revision}\n${point.products} products · ${point.sales} sales · ${point.carts} unfinished carts · ${point.photos} photos")
            }
        }
        selected?.let { point ->
            Text("Selected capture: ${DateFormat.getDateTimeInstance().format(Date(point.point.capturedAt))}", style = MaterialTheme.typography.bodyMedium)
            Text("Local changes absent from this restore point will not be restored. The current shop remains unchanged during preview and validation.", style = MaterialTheme.typography.bodyMedium)
            ShopPrimaryButton("Validate selected restore point", enabled = !busy, onClick = onValidate)
            error?.let { FormError(it) }
            if (validated) Text("Validation passed. This copy is complete and consistent. Preview only; your current shop is unchanged.",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
