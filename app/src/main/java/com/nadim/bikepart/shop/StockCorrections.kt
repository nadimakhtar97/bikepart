package com.nadim.bikepart.shop

import com.nadim.bikepart.R

enum class CorrectionKind(@param:androidx.annotation.StringRes val label: Int) {
    PhysicalCount(R.string.physical_count), Damage(R.string.damage), Loss(R.string.loss)
}

data class StockCorrection(
    val operationId: String, val productId: String, val kind: CorrectionKind,
    val quantity: Long, val reason: String, val previousBalance: Long,
    val resultingBalance: Long, val movement: Long?, val recordedAt: Long, val revision: Long,
)

internal fun CorrectionRow.toCorrection() = StockCorrection(operationId, productId, CorrectionKind.valueOf(kind), quantity, reason, previousBalance, resultingBalance, movement, recordedAt, revision)
