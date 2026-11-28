package com.nadim.bikepart.shop

import java.math.BigInteger

data class CartLine(val productId: String, val quantity: Long, val quotedPriceRupees: Long) {
    val totalRupees: Long get() = Math.multiplyExact(quantity, quotedPriceRupees)
}

data class Cart(val id: String, val number: Long, val customer: String, val editVersion: Long, val lines: List<CartLine>, val stockReductions: Map<String, Long> = emptyMap()) {
    val name: String get() = "Cart $number"
    val totalRupees: Long get() = lines.fold(0L) { total, line -> Math.addExact(total, line.totalRupees) }
}

class StaleCart(message: String) : IllegalArgumentException(message)

/** Demand is informational, may exceed Long across carts, and never reduces on-hand stock. */
fun ShopSnapshot.otherCartDemand(cartId: String, productId: String): String = carts.asSequence()
    .filter { it.id != cartId }.flatMap { it.lines.asSequence() }.filter { it.productId == productId }
    .fold(BigInteger.ZERO) { total, line -> total + BigInteger.valueOf(line.quantity) }.toString()

internal fun CartRow.readStockReductions(): Map<String, Long> {
    val notice = org.json.JSONObject(stockReductions)
    return notice.keys().asSequence().associateWith { productId ->
        when (val quantity = notice.get(productId)) {
            is Int -> quantity.toLong()
            is Long -> quantity
            else -> throw IllegalArgumentException("Invalid cart stock reduction.")
        }.also { require(it >= 0) { "Invalid cart stock reduction." } }
    }
}
