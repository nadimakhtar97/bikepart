package com.nadim.bikepart.shop

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
            ?: throw IllegalArgumentException("Unsupported or malformed shop QR. Use manual search.")
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
            ?: throw IllegalArgumentException("Unknown barcode. Use manual search or explicitly link it.")
        ItemQr.Identity(datasetId, if (alias.target.kind == BarcodeTargetKind.Product) "product" else "template", alias.target.id)
    }
    require(identity.datasetId == datasetId) { "This QR belongs to another shop dataset. Use manual search." }
    return when (identity.kind) {
        "product" -> ScanTarget.StockedProduct(products.singleOrNull { it.id == identity.id && !it.archived }
            ?: throw IllegalArgumentException("Product is unknown or archived. Use manual search."))
        else -> ScanTarget.Template(templates.singleOrNull { it.id == identity.id && !it.retired }
            ?: throw IllegalArgumentException("Template is unknown or retired. Use manual search."))
    }
}
