package com.nadim.bikepart.shop

import java.math.BigDecimal

enum class StockHealth(val label: String) { InStock("In stock"), LowStock("Low stock"), OutOfStock("Out of stock") }

val Product.stockHealth: StockHealth get() = when {
    quantity == 0L -> StockHealth.OutOfStock
    lowStockThreshold?.let { quantity <= it } == true -> StockHealth.LowStock
    else -> StockHealth.InStock
}

data class DecimalFilter(val definitionId: String, val exact: String)
data class ProductFilter(
    val query: String = "", val categoryId: String? = null, val makeId: String? = null,
    val modelId: String? = null, val locationId: String? = null, val health: StockHealth? = null,
    val unknownFitment: Boolean = false, val noLocation: Boolean = false, val decimal: DecimalFilter? = null,
)

/** Prepared normalized documents for literal substring search; numeric values compare exactly.
 * Rebuild for a new snapshot so results always contain its current balances and descriptions.
 */
class CatalogSearch(snapshot: ShopSnapshot) {
    private data class Document(val product: Product, val text: String, val decimals: Map<String, BigDecimal>, val makes: Set<String>)
    private val documents: List<Document>

    init {
        val categories = snapshot.categories.associateBy { it.id }
        val definitions = snapshot.attributes.associateBy { it.id }
        val options = snapshot.options.associateBy { it.id }
        val models = snapshot.models.associateBy { it.id }
        val makes = snapshot.makes.associateBy { it.id }
        val locations = snapshot.locations.associateBy { it.id }
        documents = snapshot.products.map { product ->
            val text = buildList {
                add(product.title); add(product.sku); add(product.description)
                categories[product.categoryId]?.let { add(it.name) }
                product.attributes.forEach { (id, value) ->
                    definitions[id]?.let { add(it.name); add(it.unit) }
                    when (value) {
                        is AttributeValue.Text -> add(value.text)
                        is AttributeValue.Choices -> value.optionIds.forEach { options[it]?.let { option -> add(option.name) } }
                        is AttributeValue.Decimal -> Unit
                    }
                }
                product.modelIds.forEach { id -> models[id]?.let { add(it.name); makes[it.makeId]?.let { make -> add(make.name) } } }
                product.locationIds.forEach { locations[it]?.let { location -> add(location.name) } }
            }.joinToString("\n")
            val decimals = product.attributes.mapNotNull { (id, value) -> (value as? AttributeValue.Decimal)?.let { id to exactDecimal(it.exact) } }.toMap()
            Document(product, LookupNames.key(text), decimals, product.modelIds.mapNotNull { models[it]?.makeId }.toSet())
        }
    }

    fun find(filter: ProductFilter = ProductFilter()): List<Product> {
        val terms = LookupNames.key(filter.query).split(' ').filter { it.isNotEmpty() }.map { term ->
            val number = runCatching { exactDecimal(term) }.getOrNull()
            Triple(term, number, if (number != null) Regex("(?<![\\p{L}\\p{N}.])" + Regex.escape(term) + "(?![\\p{L}\\p{N}.])") else null)
        }
        val decimal = filter.decimal?.let { exactDecimal(it.exact) }
        return documents.filter { doc ->
            val product = doc.product
            !product.archived &&
            (filter.categoryId == null || product.categoryId == filter.categoryId) &&
                (filter.makeId == null || filter.makeId in doc.makes) &&
                (filter.modelId == null || filter.modelId in product.modelIds) &&
                (filter.locationId == null || filter.locationId in product.locationIds) &&
                (filter.health == null || product.stockHealth == filter.health) &&
                (!filter.unknownFitment || product.modelIds.isEmpty()) &&
                (!filter.noLocation || product.locationIds.isEmpty()) &&
                (decimal == null || doc.decimals[filter.decimal.definitionId]?.compareTo(decimal) == 0) &&
                terms.all { (term, number, boundary) -> (boundary?.containsMatchIn(doc.text) ?: doc.text.contains(term)) || number != null && doc.decimals.values.any { it.compareTo(number) == 0 } }
        }.map { it.product }
    }
}
