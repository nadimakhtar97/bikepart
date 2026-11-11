package com.nadim.bikepart.shop

data class StockArrival(val operationId: String, val productId: String, val quantity: Long,
    val unitCostRupees: Long, val previousBalance: Long, val resultingBalance: Long,
    val recordedAt: Long, val revision: Long)

internal fun ArrivalRow.toArrival() = StockArrival(operationId, productId, quantity, unitCostRupees,
    previousBalance, resultingBalance, recordedAt, revision)
