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
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.nadim.bikepart.ShopViewModel
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
    var publishing by rememberSaveable { mutableStateOf(false) }
    var folderName by rememberSaveable { mutableStateOf("Bikepart shop backups") }
    val client = remember(context) { Identity.getAuthorizationClient(context) }
    fun accept(result: com.google.android.gms.auth.api.identity.AuthorizationResult) {
        val token = result.accessToken
        if (token == null) model.driveFailure("Google did not grant Drive access. Try connecting again.")
        else model.authorizedDrive(selectedAccount, token, publishing)
    }
    val resolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        try { accept(client.getAuthorizationResultFromIntent(result.data)) }
        catch (_: Exception) { model.driveFailure("Drive authorization was cancelled or failed. Reconnect to try again.") }
    }
    fun authorize(email: String, publish: Boolean) {
        selectedAccount = email; publishing = publish
        model.driveAuthorizing()
        val request = AuthorizationRequest.builder().setAccount(Account(email, "com.google"))
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.file"))).build()
        client.authorize(request).addOnSuccessListener { result ->
            if (result.hasResolution()) resolution.launch(IntentSenderRequest.Builder(requireNotNull(result.pendingIntent)).build()) else accept(result)
        }.addOnFailureListener { model.driveFailure("Google authorization failed. Check Google Play services and reconnect.") }
    }
    val chooseAccount = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (email != null) authorize(email, false) else model.driveFailure("No Google account selected.")
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
        ShopPrimaryButton("Back up now", enabled = !busy && status?.target != null, onClick = { authorize(status!!.target!!.account, true) })
        Text("Manual Drive-file edits do not edit the shop. Retire the previous phone before activating a replacement for shop work and backups.", style = MaterialTheme.typography.bodyMedium)
    }
}
