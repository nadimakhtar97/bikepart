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
            busy -> "Working with Google Drive…"
            status?.coveredRevision != null -> "Verified backup · revision ${status!!.coveredRevision} · ${DateFormat.getDateTimeInstance().format(Date(status!!.successAt!!))}"
            else -> "No verified Drive backup for this connection."
        }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        if (status?.coveredRevision?.let { it < revision } != false) Text("Shop changes are waiting for backup. Changes held only on a lost phone cannot be recovered.", style = MaterialTheme.typography.bodyMedium)
        if (status?.pending == true) Text("An interrupted backup is waiting. Back up now retries the captured revision first.", style = MaterialTheme.typography.bodyMedium)
        error?.let { FormError(it) }
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
        Text("Manual Drive-file edits do not edit the shop. Keep the previous phone retired; this app checks the active-phone marker before publication.", style = MaterialTheme.typography.bodyMedium)
    }
}
