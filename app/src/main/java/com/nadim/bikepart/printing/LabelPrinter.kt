package com.nadim.bikepart.printing


import com.nadim.bikepart.R
import java.io.IOException

/** External USB boundary: one call submits exactly one label, with no retries. */
fun interface CopyTransport { fun send(bytes: ByteArray) }

interface PrintJournal {
    fun pending(): String?
    fun record(description: String)
    fun clear()
}

data class PrintResult(@param:androidx.annotation.StringRes val messageRes: Int, val copiesSent: Long = 0)

/** This device-local journal is independent of shop stock, revisions and cash checkout. */
class LabelPrinter(private val journal: PrintJournal) {
    companion object { private val active = java.util.concurrent.atomic.AtomicBoolean(false) }
    fun pending(): String? = journal.pending()

    @Synchronized fun inspected() {
        check(active.compareAndSet(false, true))
        try { journal.clear() } finally { active.set(false) }
    }

    @Synchronized fun send(description: String, copies: Long, artwork: ByteArray,
        transport: CopyTransport, cancelled: () -> Boolean): PrintResult {
        require(copies > 0)
        if (!active.compareAndSet(false, true)) return PrintResult(R.string.another_label_send_is_stopping_wait)
        try {
            if (journal.pending() != null) return PrintResult(R.string.inspect_previous_output_before_printing_again)
            if (cancelled()) return PrintResult(R.string.cancelled_before_sending)
            // Persist before the first byte: process death must never imply safe automatic replay.
            journal.record(description)
            var sent = 0L
            try {
                while (sent < copies && !cancelled()) {
                    transport.send(artwork)
                    sent++
                }
            } catch (_: IOException) {
                return PrintResult(R.string.usb_transport_failed_after_copies_sent, sent)
            } catch (_: SecurityException) {
                return PrintResult(R.string.usb_access_was_lost_output_may)
            }
            return if (sent < copies) PrintResult(R.string.cancelled_after_copies_sent_already_sent, sent)
            else PrintResult(R.string.copies_sent_over_usb_physical_output, sent)
        } finally { active.set(false) }
    }
}
