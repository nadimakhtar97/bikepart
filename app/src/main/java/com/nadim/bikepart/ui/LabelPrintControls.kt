package com.nadim.bikepart.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.nadim.bikepart.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import com.nadim.bikepart.printing.*
import com.nadim.bikepart.shop.LabelMedia
import com.nadim.bikepart.ui.theme.ShopSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun LabelPrintControls(media: LabelMedia, copies: Long, sku: String, artwork: android.graphics.Bitmap,
    usbAccess: (Context, () -> Boolean) -> LabelUsbAccess, inspected: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val printer = remember { LabelPrinter(DevicePrintJournal(context)) }
    val scope = rememberCoroutineScope()
    val cancelled = remember { AtomicBoolean(false) }
    val usb = remember { usbAccess(context) { cancelled.get() } }
    var sending by remember { mutableStateOf(false) }
    var confirmed by remember(media, copies) { mutableStateOf(false) }
    var pending by remember { mutableStateOf(printer.pending()) }
    var feedback by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == usb.permissionAction) {
                    feedback = if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                        context.getString(R.string.usb_access_granted_confirm_media_and)
                    else context.getString(R.string.usb_permission_denied_no_labels_sent)
                    confirmed = false
                } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                    cancelled.set(true)
                    confirmed = false
                    feedback = context.getString(R.string.usb_disconnected_inspect_any_output_before)
                }
            }
        }
        val filter = android.content.IntentFilter(usb.permissionAction).apply { addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { cancelled.set(true); context.unregisterReceiver(receiver) }
    }
    ShopSection(stringResource(R.string.label_print_section)) {
        Text(stringResource(R.string.label_print_hardware))
        Text(stringResource(R.string.load_and_calibrate_the_mm_roll_verify, media.widthMm, media.heightMm))
        Text(stringResource(R.string.label_cancel_note))
        pending?.let { description ->
            Text(stringResource(R.string.inspect_previous_output, description), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(stringResource(R.string.check_the_physical_labels_after_interrupted_output))
            ShopSecondaryButton(enabled = !sending, onClick = {
                try { printer.inspected(); pending = null; confirmed = false; inspected() }
                catch (_: Exception) { feedback = context.getString(R.string.could_not_save_inspection_printing_remains) }
            }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.label_inspected_btn)) }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget).toggleable(
            value = confirmed, enabled = !sending && pending == null, role = Role.Checkbox, onValueChange = { confirmed = it }),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = confirmed, enabled = !sending && pending == null, onCheckedChange = null)
            Text(stringResource(R.string.i_confirm_the_loaded_mm_roll_calibrated, media.widthMm, media.heightMm, copies))
        }
        ShopPrimaryButton(stringResource(R.string.label_print_confirmed), enabled = !sending && pending == null && confirmed) {
            sending = true
            cancelled.set(false)
            confirmed = false
            scope.launch {
                try {
                    feedback = withContext(Dispatchers.IO) {
                        val connection = usb.connect()
                        if (connection == null) {
                            context.getString(R.string.usb_permission_requested_nothing_has_been)
                        } else connection.use { transport ->
                            val result = printer.send(context.getString(R.string.copies_2, sku, context.getString(media.label), copies), copies, TsplLabel.encode(media, artwork), transport) { cancelled.get() }
                            context.getString(result.messageRes, result.copiesSent)
                        }
                    }
                } catch (e: Exception) {
                    feedback = e.message ?: context.getString(R.string.printer_unavailable_check_usb_otg_and)
                } finally { pending = printer.pending(); sending = false }
            }
        }
        if (sending) ShopSecondaryButton(onClick = { cancelled.set(true); feedback = context.getString(R.string.cancelling_unsent_copies_output_already_sent) },
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.label_cancel_unsent)) }
        ErrorSnackbar(feedback)
    }
}
