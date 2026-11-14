package com.nadim.bikepart.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                        "USB access granted. Confirm media and copies, then tap Print confirmed labels."
                    else "USB permission denied. No labels sent. Allow USB access to print."
                    confirmed = false
                } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                    cancelled.set(true)
                    confirmed = false
                    feedback = "USB disconnected. Inspect any output before choosing remaining copies and reconnecting."
                }
            }
        }
        val filter = android.content.IntentFilter(usb.permissionAction).apply { addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { cancelled.set(true); context.unregisterReceiver(receiver) }
    }
    ShopSection("USB label printing") {
        Text("TE244 · TSPL · 203 DPI · USB/OTG. Hardware acceptance is pending on the intended phone and printer.")
        Text("Load and calibrate the ${media.widthMm} × ${media.heightMm} mm roll. Verify ribbon/material and printer settings before printing.")
        Text("Cancellation stops unsent copies. It cannot retract a copy already being sent or queued in the printer.")
        pending?.let { description ->
            Text("Inspect previous output: $description", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text("Check the physical labels. After interrupted output, power-cycle the TE244 to discard partial commands. Then enter only the remaining copies (or finish without reprinting).")
            ShopSecondaryButton(enabled = !sending, onClick = {
                try { printer.inspected(); pending = null; confirmed = false; inspected() }
                catch (_: Exception) { feedback = "Could not save inspection. Printing remains blocked." }
            }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("I inspected output and reset the printer") }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget).toggleable(
            value = confirmed, enabled = !sending && pending == null, role = Role.Checkbox, onValueChange = { confirmed = it }),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = confirmed, enabled = !sending && pending == null, onCheckedChange = null)
            Text("I confirm the loaded ${media.widthMm} × ${media.heightMm} mm roll, calibrated TSPL profile and $copies copies.")
        }
        ShopPrimaryButton("Print confirmed labels", enabled = !sending && pending == null && confirmed) {
            sending = true
            cancelled.set(false)
            confirmed = false
            scope.launch {
                try {
                    feedback = withContext(Dispatchers.IO) {
                        val connection = usb.connect()
                        if (connection == null) {
                            "USB permission requested. Nothing has been sent. Confirm printing again after allowing access."
                        } else connection.use { transport ->
                            printer.send("$sku · ${media.label} · $copies copies", copies, TsplLabel.encode(media, artwork), transport) { cancelled.get() }
                        }
                    }
                } catch (e: Exception) {
                    feedback = e.message ?: "Printer unavailable. Check USB/OTG and permission."
                } finally { pending = printer.pending(); sending = false }
            }
        }
        if (sending) ShopSecondaryButton(onClick = { cancelled.set(true); feedback = "Cancelling unsent copies. Output already sent cannot be retracted." },
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Cancel unsent copies") }
        feedback?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}
