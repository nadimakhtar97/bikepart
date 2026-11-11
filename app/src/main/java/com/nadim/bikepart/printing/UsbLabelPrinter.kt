package com.nadim.bikepart.printing

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.usb.*
import android.util.AtomicFile
import com.nadim.bikepart.shop.LabelMedia
import java.io.File
import java.io.IOException

class DevicePrintJournal(context: Context) : PrintJournal {
    companion object { private val lock = Any() }
    private val file = AtomicFile(File(context.noBackupFilesDir, "label-output"))
    override fun pending(): String? = synchronized(lock) {
        try { file.openRead().use { it.readBytes().toString(Charsets.UTF_8) } }
        catch (_: java.io.FileNotFoundException) { null }
    }
    override fun record(description: String) = synchronized(lock) {
        val stream = file.startWrite()
        try { stream.write(description.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    override fun clear() = synchronized(lock) { file.delete(); check(pending() == null) { "Could not clear print recovery record" } }
}

/** TSPL BITMAP uses MSB first, with 1 = white, 0 = ink. Never interpolate product text. */
object TsplLabel {
    fun encode(media: LabelMedia, pixels: IntArray): ByteArray {
        val width = media.widthMm * 8
        val height = media.heightMm * 8
        require(pixels.size == width * height)
        val raster = ByteArray(width / 8 * height) { 0xff.toByte() }
        pixels.forEachIndexed { index, color ->
            val luminance = (color shr 16 and 255) * 299 + (color shr 8 and 255) * 587 + (color and 255) * 114
            if (luminance < 128000) {
                val byte = index / 8
                raster[byte] = (raster[byte].toInt() and (0x80 shr (index % 8)).inv()).toByte()
            }
        }
        // Preserve calibrated sensor/gap/ribbon settings. Each copy is a complete independent label.
        val header = "SIZE ${media.widthMm} mm,${media.heightMm} mm\r\nDIRECTION 1\r\nREFERENCE 0,0\r\nCLS\r\nBITMAP 0,0,${width / 8},$height,0,"
        return header.toByteArray(Charsets.US_ASCII) + raster + "\r\nPRINT 1,1\r\n".toByteArray(Charsets.US_ASCII)
    }

    fun encode(media: LabelMedia, bitmap: Bitmap): ByteArray {
        require(bitmap.width == media.widthMm * 8 && bitmap.height == media.heightMm * 8)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return encode(media, pixels)
    }
}

interface LabelUsbAccess {
    val permissionAction: String
    /** Null means permission was requested; callers must require a new explicit send. */
    fun connect(): LabelConnection?
}

interface LabelConnection : CopyTransport, AutoCloseable

class UsbLabelPrinter(private val context: Context, private val cancelled: () -> Boolean = { false }) : LabelUsbAccess {
    private val manager = context.getSystemService(UsbManager::class.java)
    override val permissionAction = "${context.packageName}.LABEL_USB_PERMISSION"

    override fun connect(): LabelConnection? {
        val device = device()
        if (!hasPermission(device)) { requestPermission(device); return null }
        return open(device, cancelled)
    }

    fun device(): UsbDevice {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST))
            throw IOException("This phone does not support USB host/OTG.")
        val devices = manager.deviceList.values.filter {
            it.productName?.uppercase()?.replace(Regex("[^A-Z0-9]"), "")?.let { name -> name == "TE244" || name == "TSCTE244" } == true
        }
        if (devices.size != 1) throw IOException("Connect exactly one TE244 using USB/OTG and turn it on. No supported TE244 is available.")
        return devices.single()
    }

    fun hasPermission(device: UsbDevice) = manager.hasPermission(device)

    fun requestPermission(device: UsbDevice) {
        val intent = Intent(permissionAction).setPackage(context.packageName)
        manager.requestPermission(device, PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    }

    fun open(device: UsbDevice, cancelled: () -> Boolean = { false }): UsbConnection {
        if (!hasPermission(device)) throw IOException("USB permission is required. Allow access, then confirm printing again.")
        val interfaces = (0 until device.interfaceCount).map { device.getInterface(it) }
        val candidates = interfaces.flatMap { usbInterface ->
            (0 until usbInterface.endpointCount).map { usbInterface.getEndpoint(it) }
                .filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }
                .map { usbInterface to it }
        }
        if (candidates.size != 1) throw IOException("Unsupported TE244 USB transport. Record firmware and USB descriptors for setup review.")
        val (usbInterface, endpoint) = candidates.single()
        val connection = manager.openDevice(device) ?: throw IOException("TE244 unavailable. Reconnect USB/OTG and check permission.")
        if (!connection.claimInterface(usbInterface, true)) {
            connection.close()
            throw IOException("TE244 USB interface is busy or unavailable.")
        }
        return UsbConnection(connection, usbInterface, endpoint, cancelled)
    }
}

class UsbConnection(private val connection: UsbDeviceConnection, private val usbInterface: UsbInterface,
    private val endpoint: UsbEndpoint, private val cancelled: () -> Boolean) : LabelConnection {
    override fun send(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            if (cancelled()) throw IOException("Cancelled USB transfer; output may be partial")
            val count = minOf(4096, bytes.size - offset)
            val written = connection.bulkTransfer(endpoint, bytes, offset, count, 2000)
            // A short write is uncertain. Never retry even its remainder automatically.
            if (written != count) throw IOException("Incomplete USB transfer")
            offset += written
        }
    }
    override fun close() { connection.releaseInterface(usbInterface); connection.close() }
}
