package com.nadim.bikepart.shop

/** Receipt text is captured at checkout, never resolved against the live catalog. */
data class SaleLine(val productId: String, val sku: String, val title: String, val category: String,
    val description: String, val fitment: String, val quantity: Long, val quotedPriceRupees: Long,
    val previousBalance: Long, val resultingBalance: Long, val movement: Long) {
    val totalRupees: Long get() = Math.multiplyExact(quantity, quotedPriceRupees)
}

data class Sale(val id: String, val cartId: String, val cartNumber: Long, val customer: String,
    val recordedAt: Long, val revision: Long, val totalRupees: Long, val cashRupees: Long,
    val changeRupees: Long, val lines: List<SaleLine>)

data class StockShortage(val productId: String, val title: String, val requested: Long, val available: Long)
class CheckoutShortage(val shortages: List<StockShortage>) : IllegalArgumentException(
    shortages.joinToString("\n") { "${it.title}: requested ${indianNumber(it.requested)}, available ${indianNumber(it.available)}" } +
        "\nNo sale saved. Edit the cart quantities explicitly before retrying.")

internal fun SaleRow.toSale(lines: List<SaleLineRow>) = Sale(id, cartId, cartNumber, customer, recordedAt, revision,
    totalRupees, cashRupees, changeRupees, lines.map { SaleLine(it.productId, it.sku, it.title, it.category,
        it.description, it.fitment, it.quantity, it.quotedPriceRupees, it.previousBalance, it.resultingBalance, it.movement) })
