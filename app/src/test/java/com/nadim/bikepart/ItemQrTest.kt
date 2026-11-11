package com.nadim.bikepart

import com.nadim.bikepart.shop.ItemQr
import org.junit.Assert.*
import org.junit.Test

class ItemQrTest {
    private val dataset = "12345678-1234-1234-1234-123456789abc"
    private val product = "abcdefab-1234-1234-1234-123456789abc"

    @Test fun reviewedProductPayloadIsStrictAndDatasetQualified() {
        val payload = "bikepart:1:$dataset:product:$product"
        assertEquals(payload, ItemQr.product(dataset, product))
        assertEquals(product, ItemQr.parse(payload).id)
        listOf(payload.replace(":1:", ":2:"), "$payload:extra", " $payload", payload.replace(dataset, "1-1-1-1-1"),
            payload.replace(":product:", ":unknown:"), payload.uppercase(), "BP-$product").forEach {
            try { ItemQr.parse(it); fail("Accepted $it") } catch (_: IllegalArgumentException) { }
        }
    }
}
