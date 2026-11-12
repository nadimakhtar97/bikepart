package com.nadim.bikepart.shop

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Descriptive data only: a draft has no inventory identity, price, balance or locations. */
data class CatalogDraft(
    val title: String = "", val categoryId: String = "", val description: String = "",
    val attributes: Map<String, AttributeValue> = emptyMap(),
    val customFields: List<DraftField> = emptyList(), val modelIds: Set<String> = emptySet(),
    val photo: PhotoReference? = null,
    val historicalAttributes: List<HistoricalAttribute> = emptyList(),
)
data class DraftField(val id: String, val name: String, val type: AttributeType, val required: Boolean,
    val unit: String, val options: List<AttributeOption> = emptyList())
data class ProductTemplate(val id: String, val catalog: CatalogDraft, val retired: Boolean)

internal fun CatalogDraft.requireDistinctCustomIdentities() {
    val identities = customFields.flatMap { field -> listOf(field.id) + field.options.map { it.id } }
    require(identities.distinct().size == identities.size) { "Custom identities must be distinct" }
}

internal fun DraftField.row(categoryId: String, productId: String) = AttributeRow(id, categoryId, name, LookupNames.key(name), type.name, required, unit, productId, "product:$productId")

fun ShopSnapshot.copyCatalog(productId: String): CatalogDraft {
    val product = products.single { it.id == productId }
    val custom = attributes.filter { it.productId == productId && !it.retired }.map { field ->
        DraftField(field.id, field.name, field.type, field.required, field.unit, options.filter { it.definitionId == field.id && !it.retired })
    }
    return selectable(CatalogDraft(product.title, product.categoryId, product.description, product.attributes, custom, product.modelIds, product.photo)).independent()
}

fun ShopSnapshot.prefillTemplate(id: String): CatalogDraft {
    val template = templates.single { it.id == id }
    require(!template.retired) { "That template is retired" }
    return selectable(template.catalog).independent()
}

/** Retired shared meanings remain readable on the source but cannot be newly assigned. */
private fun ShopSnapshot.selectable(draft: CatalogDraft): CatalogDraft {
    val allowed = attributes.filter { it.productId == null && it.categoryId == draft.categoryId && !it.retired }.map { it.id }.toSet() + draft.customFields.map { it.id }
    val activeOptions = options.filter { !it.retired }.map { it.id }.toSet() + draft.customFields.flatMap { it.options }.filter { !it.retired }.map { it.id }
    return draft.copy(attributes = draft.attributes.filterKeys { it in allowed }.mapValues { (_, value) ->
        if (value is AttributeValue.Choices) AttributeValue.Choices(value.optionIds.intersect(activeOptions)) else value
    }, modelIds = draft.modelIds.filter { id -> models.any { it.id == id && !it.retired && makes.any { make -> make.id == it.makeId && !make.retired } } }.toSet(), historicalAttributes = emptyList())
}

internal fun CatalogDraft.independent(): CatalogDraft {
    val ids = customFields.flatMap { listOf(it.id) + it.options.map { option -> option.id } }.associateWith { UUID.randomUUID().toString() }
    return copy(customFields = customFields.map { field -> field.copy(id = ids.getValue(field.id), options = field.options.map { it.copy(id = ids.getValue(it.id), definitionId = ids.getValue(field.id)) }) },
        attributes = attributes.entries.associate { (id, value) -> (ids[id] ?: id) to if (value is AttributeValue.Choices) AttributeValue.Choices(value.optionIds.map { ids[it] ?: it }.toSet()) else value })
}

// Versioned local payload keeps template-owned definitions and domains together, outside inventory.
internal fun CatalogDraft.encode(): String = JSONObject().apply {
    put("version", 1); put("title", title); put("category", categoryId); put("description", description)
    put("models", JSONArray(modelIds.sorted()))
    put("history", JSONArray(historicalAttributes.map { item -> JSONObject().put("id", item.id).put("definition", item.definitionId).put("value", encodeValue(item.value)) }))
    photo?.let { put("photo", JSONObject().put("hash", it.sha256).put("size", it.sizeBytes)) }
    put("fields", JSONArray(customFields.map { field -> JSONObject().apply {
        put("id", field.id); put("name", field.name); put("type", field.type.name); put("required", field.required); put("unit", field.unit)
        put("options", JSONArray(field.options.map { JSONObject().put("id", it.id).put("name", it.name) }))
    } }))
    put("values", JSONObject().apply { attributes.forEach { (id, value) -> put(id, encodeValue(value)) } })
}.toString()

internal fun decodeCatalog(payload: String): CatalogDraft {
    val json = JSONObject(payload)
    require(json.getInt("version") == 1) { "Unsupported template version" }
    val fields = json.getJSONArray("fields")
    val values = json.getJSONObject("values")
    val history = json.optJSONArray("history") ?: JSONArray()
    return CatalogDraft(json.getString("title"), json.getString("category"), json.getString("description"), values.keys().asSequence().associateWith { id ->
        decodeValue(values.getJSONObject(id))
    }, (0 until fields.length()).map { index ->
        val field = fields.getJSONObject(index)
        val id = field.getString("id")
        val options = field.getJSONArray("options")
        DraftField(id, field.getString("name"), AttributeType.valueOf(field.getString("type")), field.getBoolean("required"), field.getString("unit"),
            (0 until options.length()).map { options.getJSONObject(it).let { option -> AttributeOption(option.getString("id"), id, option.getString("name")) } })
    }, json.getJSONArray("models").strings(), json.optJSONObject("photo")?.let { PhotoReference(it.getString("hash"), it.getLong("size")) },
        (0 until history.length()).map { history.getJSONObject(it).let { item -> HistoricalAttribute(item.getString("id"), item.getString("definition"), decodeValue(item.getJSONObject("value"))) } })
}
private fun encodeValue(value: AttributeValue): JSONObject = JSONObject().apply {
    when (value) {
        is AttributeValue.Text -> put("text", value.text)
        is AttributeValue.Decimal -> put("decimal", value.exact)
        is AttributeValue.Choices -> put("choices", JSONArray(value.optionIds.sorted()))
    }
}
private fun decodeValue(value: JSONObject): AttributeValue = when {
    value.has("text") -> AttributeValue.Text(value.getString("text"))
    value.has("decimal") -> AttributeValue.Decimal(value.getString("decimal"))
    else -> AttributeValue.Choices(value.getJSONArray("choices").strings())
}
private fun JSONArray.strings(): Set<String> = (0 until length()).map { getString(it) }.toSet()
