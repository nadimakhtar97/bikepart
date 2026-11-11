package com.nadim.bikepart.shop

enum class CorrectionKind(val label: String) {
    PhysicalCount("Physical count"), Damage("Damage"), Loss("Loss")
}

data class StockCorrection(
    val operationId: String, val productId: String, val kind: CorrectionKind,
    val quantity: Long, val reason: String, val previousBalance: Long,
    val resultingBalance: Long, val movement: Long?, val recordedAt: Long, val revision: Long,
)

internal fun CorrectionRow.toCorrection() = StockCorrection(operationId, productId, CorrectionKind.valueOf(kind), quantity, reason, previousBalance, resultingBalance, movement, recordedAt, revision)
