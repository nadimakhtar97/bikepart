package com.nadim.bikepart.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    onCapture: () -> Unit, onSave: (android.net.Uri, LocalCapture) -> Unit) {
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
    ShopSection("Local backup") {
        Text("Saved on this phone. This does not confirm a Drive backup.", style = MaterialTheme.typography.bodyMedium)
        Text(if (busy) "Preparing local backup…" else capture?.let {
            "Local capture complete · revision ${it.revision} · ${DateFormat.getDateTimeInstance().format(Date(it.capturedAt))}"
        } ?: "No complete local capture yet.", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (capture != null && currentRevision > capture.revision) Text("Newer shop changes are waiting for a local capture.", style = MaterialTheme.typography.bodyMedium)
        if (error != null) FormError(error)
        if (saved) Text("Backup copy saved.", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        ShopPrimaryButton("Capture local backup", enabled = !busy, onClick = onCapture)
        if (capture != null) OutlinedButton(enabled = !busy && selectedPath == null, onClick = {
            selectedPath = capture.file.path; selectedId = capture.id; selectedRevision = capture.revision; selectedTime = capture.capturedAt
            save.launch("shop-backup-${capture.id}.zip")
        }) { Text("Save a copy…") }
    }
}
