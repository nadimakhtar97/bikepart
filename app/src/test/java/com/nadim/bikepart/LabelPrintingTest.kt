package com.nadim.bikepart

import com.nadim.bikepart.printing.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LabelPrintingTest {
    @Test fun cancellationStopsUnsentCopiesAndRestartKeepsRecoveryGate() {
        var marker: String? = null
        val journal = object : PrintJournal {
            override fun pending() = marker
            override fun record(description: String) { marker = description }
            override fun clear() { marker = null }
        }
        var stop = false
        var copiesSent = 0
        val printer = LabelPrinter(journal)
        val result = printer.send("SKU · 10 copies", 10, byteArrayOf(9), CopyTransport { copiesSent++; stop = true }) { stop }
        assertEquals(1, copiesSent)
        assertTrue(result.contains("cannot be retracted"))
        val restarted = LabelPrinter(journal)
        restarted.send("remaining", 9, byteArrayOf(9), CopyTransport { copiesSent++ }) { false }
        assertEquals(1, copiesSent)
        restarted.inspected()
        assertNull(restarted.pending())
    }

    @Test fun bitmapUsesDocumentedWhitePolarityAndSingleCopyCommand() {
        val pixels = IntArray(400 * 240) { -1 }
        pixels[0] = 0xff000000.toInt()
        pixels[7] = 0xff000000.toInt()
        val bytes = TsplLabel.encode(com.nadim.bikepart.shop.LabelMedia.Package, pixels)
        val header = "SIZE 50 mm,30 mm\r\nDIRECTION 1\r\nREFERENCE 0,0\r\nCLS\r\nBITMAP 0,0,50,240,0,"
        assertEquals(header, bytes.copyOfRange(0, header.length).toString(Charsets.US_ASCII))
        assertEquals(0x7e, bytes[header.length].toInt() and 255)
        assertEquals(255, bytes[header.length + 1].toInt() and 255)
        assertTrue(bytes.toString(Charsets.ISO_8859_1).endsWith("\r\nPRINT 1,1\r\n"))
    }

    @Test fun interruptedOutputBlocksResendingUntilStaffInspectAndChooseRemainingCopies() {
        var marker: String? = null
        val printer = LabelPrinter(object : PrintJournal {
            override fun pending() = marker
            override fun record(description: String) { marker = description }
            override fun clear() { marker = null }
        })
        var transfers = 0
        val broken = CopyTransport { transfers++; throw IOException("Disconnected") }
        assertTrue(printer.send("SKU 123 · 3 copies", 3, byteArrayOf(1), broken) { false }.contains("Inspect"))
        assertNotNull(marker)
        printer.send("SKU 123 · 3 copies", 3, byteArrayOf(1), broken) { false }
        assertEquals(1, transfers)
        printer.inspected()
        val output = mutableListOf<Byte>()
        val result = printer.send("SKU 123 · 2 remaining", 2, byteArrayOf(7), CopyTransport { output.addAll(it.toList()) }) { false }
        assertEquals(listOf<Byte>(7, 7), output)
        assertTrue(result.contains("2 copies sent"))
        assertNotNull(marker) // USB acceptance is not physical output confirmation.
    }
}
