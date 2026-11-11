package com.nadim.bikepart.shop

enum class BarcodeTargetKind { Product, Template }
data class BarcodeTarget(val kind: BarcodeTargetKind, val id: String)
data class BarcodeAlias(val namespace: String, val code: String, val target: BarcodeTarget, val revision: Long)
data class BarcodeAssignment(val namespace: String, val code: String, val previousTarget: BarcodeTarget?,
    val target: BarcodeTarget, val recordedAt: Long, val revision: Long)
class BarcodeConflict(val assignment: BarcodeAlias) : IllegalArgumentException(
    "This barcode already belongs to another target. Confirm reassignment or cancel.")

internal fun BarcodeAliasRow.toAlias() = BarcodeAlias(namespace, code, BarcodeTarget(BarcodeTargetKind.valueOf(kind), targetId), revision)
internal fun BarcodeAssignmentRow.toAssignment() = BarcodeAssignment(namespace, code,
    previousKind?.let { BarcodeTarget(BarcodeTargetKind.valueOf(it), requireNotNull(previousTargetId)) },
    BarcodeTarget(BarcodeTargetKind.valueOf(kind), targetId), recordedAt, revision)

internal fun requireExternalBarcode(namespace: String, code: String) {
    require(namespace.isNotBlank() && !namespace.equals("bikepart", ignoreCase = true)) { "The app-QR namespace is reserved." }
    require(code.isNotEmpty()) { "Scan an external barcode first." }
    require(!code.startsWith("bikepart:", ignoreCase = true)) { "The app-QR namespace is reserved." }
}

fun ShopSnapshot.barcodeTargetLabel(target: BarcodeTarget): String = when (target.kind) {
    BarcodeTargetKind.Product -> products.find { it.id == target.id }?.let { "${it.title} · ${it.sku}" } ?: target.id
    BarcodeTargetKind.Template -> templates.find { it.id == target.id }?.let { "Template: ${it.catalog.title}" } ?: target.id
}
