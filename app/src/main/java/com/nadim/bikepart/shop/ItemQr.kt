package com.nadim.bikepart.shop

import com.nadim.bikepart.R

internal class ScanFailure(@param:androidx.annotation.StringRes val messageRes: Int) : IllegalArgumentException()

/** Stable identities only. Stock, price, SKU and location never enter a shared label. */
object ItemQr {
    data class Identity(val datasetId: String, val kind: String, val id: String)
    private val uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    private val encoding = Regex("bikepart:1:($uuid):(product|template):($uuid)")
    fun product(datasetId: String, productId: String): String = encode(datasetId, "product", productId)
    fun template(datasetId: String, templateId: String): String = encode(datasetId, "template", templateId)
    private fun encode(dataset: String, kind: String, id: String): String =
        "bikepart:1:$dataset:$kind:$id".also { parse(it) }
    fun parse(payload: String): Identity {
        val match = encoding.matchEntire(payload)
            ?: throw ScanFailure(R.string.unsupported_or_malformed_shop_qr_use)
        return Identity(match.groupValues[1], match.groupValues[2], match.groupValues[3])
    }
}

sealed interface ScanTarget {
    data class StockedProduct(val product: Product) : ScanTarget
    data class Template(val template: ProductTemplate) : ScanTarget
}

/** Resolution is read-only and never falls back to titles, SKUs or another dataset. */
fun ShopSnapshot.resolveScan(payload: String, namespace: String = "external"): ScanTarget {
    val identity = if (payload.startsWith("bikepart:", ignoreCase = true)) ItemQr.parse(payload) else {
        val alias = barcodeAliases.singleOrNull { it.namespace == namespace && it.code == payload }
            ?: throw ScanFailure(R.string.unknown_barcode_use_manual_search_or)
        ItemQr.Identity(datasetId, if (alias.target.kind == BarcodeTargetKind.Product) "product" else "template", alias.target.id)
    }
    if (identity.datasetId != datasetId) throw ScanFailure(R.string.this_qr_belongs_to_another_shop)
    return when (identity.kind) {
        "product" -> ScanTarget.StockedProduct(products.singleOrNull { it.id == identity.id && !it.archived }
            ?: throw ScanFailure(R.string.product_is_unknown_or_archived_use))
        else -> ScanTarget.Template(templates.singleOrNull { it.id == identity.id && !it.retired }
            ?: throw ScanFailure(R.string.template_is_unknown_or_retired_use))
    }
}
