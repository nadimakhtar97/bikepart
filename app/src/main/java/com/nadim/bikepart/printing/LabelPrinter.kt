package com.nadim.bikepart.printing

import java.io.IOException

/** External USB boundary: one call submits exactly one label, with no retries. */
fun interface CopyTransport { fun send(bytes: ByteArray) }

interface PrintJournal {
    fun pending(): String?
    fun record(description: String)
    fun clear()
}

/** This device-local journal is independent of shop stock, revisions and cash checkout. */
class LabelPrinter(private val journal: PrintJournal) {
    companion object { private val active = java.util.concurrent.atomic.AtomicBoolean(false) }
    fun pending(): String? = journal.pending()

    @Synchronized fun inspected() {
        check(active.compareAndSet(false, true)) { "Wait for unsent work to stop before recording inspection." }
        try { journal.clear() } finally { active.set(false) }
    }

    @Synchronized fun send(description: String, copies: Long, artwork: ByteArray,
        transport: CopyTransport, cancelled: () -> Boolean): String {
        require(copies > 0)
        if (!active.compareAndSet(false, true)) return "Another label send is stopping. Wait and inspect output."
        try {
            if (journal.pending() != null) return "Inspect previous output before printing again."
            if (cancelled()) return "Cancelled before sending."
            // Persist before the first byte: process death must never imply safe automatic replay.
            journal.record(description)
            var sent = 0L
            try {
                while (sent < copies && !cancelled()) {
                    transport.send(artwork)
                    sent++
                }
            } catch (_: IOException) {
                return "USB transport failed after $sent copies sent. Output may be partial. Inspect labels and power-cycle the printer before selecting remaining copies."
            } catch (_: SecurityException) {
                return "USB access was lost. Output may be partial. Inspect labels and power-cycle the printer before selecting remaining copies."
            }
            return if (sent < copies) "Cancelled after $sent copies sent. Already sent output cannot be retracted. Inspect labels before selecting remaining copies."
            else "$sent copies sent over USB; physical output is unconfirmed. Inspect labels before printing again."
        } finally { active.set(false) }
    }
}
