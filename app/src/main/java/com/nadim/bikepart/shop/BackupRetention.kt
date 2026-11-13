package com.nadim.bikepart.shop

import java.time.Instant
import java.time.ZoneId

/** A complete, owner-readable folder inventory; transports must fail rather than return partial pages. */
data class DriveInventory(val entries: List<DriveStoredFile>, val ownerCanListAll: Boolean)
data class DriveStoredFile(val id: String, val name: String, val size: Long?, val canDelete: Boolean, val version: String? = null)

@ConsistentCopyVisibility
data class BackupStorage internal constructor(
    val bytes: Long,
    val completePoints: Int,
    val retainedPoints: Int,
    val eligible: List<DrivePoint>,
    val reclaimableBytes: Long,
    val cleanupPending: Boolean,
    internal val target: DriveTarget,
    internal val fingerprint: String,
)

internal data class StoredPoint(val point: DrivePoint, val captureId: String, val hash: String,
    val files: Map<String, String>, val manifest: String, val versions: Map<String, String?>)

internal object BackupRetention {
    private val india = ZoneId.of("Asia/Kolkata")

    fun retained(points: List<StoredPoint>, now: Long): Set<String> {
        val ordered = points.sortedWith(compareByDescending<StoredPoint> { it.point.capturedAt }.thenBy { it.point.manifestId })
        val today = Instant.ofEpochMilli(now).atZone(india).toLocalDate()
        val daily = ordered.filter {
            val day = Instant.ofEpochMilli(it.point.capturedAt).atZone(india).toLocalDate()
            day >= today.minusDays(29) && day <= today
        }.distinctBy { Instant.ofEpochMilli(it.point.capturedAt).atZone(india).toLocalDate() }
        return (ordered.distinctBy { it.captureId }.take(7) + daily).map { it.captureId }.toSet()
    }
}
