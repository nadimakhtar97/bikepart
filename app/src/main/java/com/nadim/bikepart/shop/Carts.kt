package com.nadim.bikepart.shop

import java.math.BigInteger

data class CartLine(val productId: String, val quantity: Long, val quotedPriceRupees: Long) {
    val totalRupees: Long get() = Math.multiplyExact(quantity, quotedPriceRupees)
}

data class Cart(val id: String, val number: Long, val customer: String, val editVersion: Long, val lines: List<CartLine>) {
    val name: String get() = "Cart $number"
    val totalRupees: Long get() = lines.fold(0L) { total, line -> Math.addExact(total, line.totalRupees) }
}

class StaleCart : IllegalArgumentException("This cart changed. Reload the saved cart before editing again.")

/** Demand is informational, may exceed Long across carts, and never reduces on-hand stock. */
fun ShopSnapshot.otherCartDemand(cartId: String, productId: String): String = carts.asSequence()
    .filter { it.id != cartId }.flatMap { it.lines.asSequence() }.filter { it.productId == productId }
    .fold(BigInteger.ZERO) { total, line -> total + BigInteger.valueOf(line.quantity) }.toString()
