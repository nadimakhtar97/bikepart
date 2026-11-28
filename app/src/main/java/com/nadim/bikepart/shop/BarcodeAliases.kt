package com.nadim.bikepart.shop

import com.nadim.bikepart.R

enum class BarcodeTargetKind { Product, Template }
data class BarcodeTarget(val kind: BarcodeTargetKind, val id: String)
data class BarcodeAlias(val namespace: String, val code: String, val target: BarcodeTarget, val revision: Long)
data class BarcodeAssignment(val namespace: String, val code: String, val previousTarget: BarcodeTarget?,
    val target: BarcodeTarget, val recordedAt: Long, val revision: Long)
class BarcodeConflict(val assignment: BarcodeAlias, message: String) : IllegalArgumentException(message)

internal fun BarcodeAliasRow.toAlias() = BarcodeAlias(namespace, code, BarcodeTarget(BarcodeTargetKind.valueOf(kind), targetId), revision)
internal fun BarcodeAssignmentRow.toAssignment() = BarcodeAssignment(namespace, code,
    previousKind?.let { BarcodeTarget(BarcodeTargetKind.valueOf(it), requireNotNull(previousTargetId)) },
    BarcodeTarget(BarcodeTargetKind.valueOf(kind), targetId), recordedAt, revision)

internal fun requireExternalBarcode(context: android.content.Context, namespace: String, code: String) {
    require(namespace.isNotBlank() && !namespace.equals("bikepart", ignoreCase = true)) { context.getString(R.string.the_app_qr_namespace_is_reserved) }
    require(code.isNotEmpty()) { context.getString(R.string.scan_an_external_barcode_first) }
    require(!code.startsWith("bikepart:", ignoreCase = true)) { context.getString(R.string.the_app_qr_namespace_is_reserved) }
}
