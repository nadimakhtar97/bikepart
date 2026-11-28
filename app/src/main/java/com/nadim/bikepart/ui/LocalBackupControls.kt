package com.nadim.bikepart.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.nadim.bikepart.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.nadim.bikepart.shop.LocalCapture
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
internal fun LocalBackupControls(capture: LocalCapture?, currentRevision: Long, busy: Boolean, error: String?, saved: Boolean,
    onCapture: () -> Unit, onSave: (android.net.Uri, LocalCapture) -> Unit, recovery: Boolean = false) {
    // Preserve the selected capture across activity recreation while the document picker is open.
    var selectedPath by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var selectedRevision by rememberSaveable { mutableLongStateOf(0) }
    var selectedTime by rememberSaveable { mutableLongStateOf(0) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val path = selectedPath
        if (uri != null && path != null) onSave(uri, LocalCapture(File(path), selectedId, selectedRevision, selectedTime))
        selectedPath = null
    }
    ShopSection(if (recovery) stringResource(R.string.local_backup_recovery_title) else stringResource(R.string.local_backup_title)) {
        Text(if (recovery) stringResource(R.string.local_backup_recovery_desc) else stringResource(R.string.local_backup_desc), style = MaterialTheme.typography.bodyMedium)
        Text(if (busy && !recovery) stringResource(R.string.local_backup_preparing) else capture?.let {
            stringResource(R.string.revision, if (recovery) stringResource(R.string.recovery_capture) else stringResource(R.string.local_capture_complete), it.revision, DateFormat.getDateTimeInstance().format(Date(it.capturedAt)))
        } ?: stringResource(R.string.local_backup_no_capture), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (!recovery && capture != null && currentRevision > capture.revision) Text(stringResource(R.string.local_backup_newer_changes), style = MaterialTheme.typography.bodyMedium)
        if (error != null) ErrorSnackbar(error)
        if (saved) Text(if (recovery) stringResource(R.string.local_recovery_copy_saved) else stringResource(R.string.local_backup_copy_saved), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (!recovery) ShopPrimaryButton(stringResource(R.string.local_backup_capture_btn), enabled = !busy, onClick = onCapture)
        if (capture != null) ShopSecondaryButton(enabled = !busy && selectedPath == null, onClick = {
            selectedPath = capture.file.path; selectedId = capture.id; selectedRevision = capture.revision; selectedTime = capture.capturedAt
            save.launch("shop-backup-${capture.id}.zip")
        }) { Text(if (recovery) stringResource(R.string.local_save_recovery_copy) else stringResource(R.string.local_save_copy)) }
    }
}
