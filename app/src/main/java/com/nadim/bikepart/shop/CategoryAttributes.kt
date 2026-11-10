package com.nadim.bikepart.shop

import com.ibm.icu.lang.UCharacter
import com.ibm.icu.text.Normalizer2
import java.math.BigDecimal

/** Lookup normalization v1: ICU4J 76.1 / Unicode 16 NFKC, whitespace folding and default case folding. */
object LookupNames {
    const val VERSION = 1
    fun display(value: String): String = Normalizer2.getNFKCInstance().normalize(value)
        .replace(Regex("[\\u0009-\\u000D\\u0020\\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+"), " ").trim(' ')
    fun key(value: String): String = UCharacter.foldCase(display(value), true)
}

enum class AttributeType(val label: String) {
    Text("Text"), Decimal("Decimal number"), SingleChoice("Single choice"), MultipleChoice("Multiple choice");
    val isChoice: Boolean get() = this == SingleChoice || this == MultipleChoice
}
data class CategoryAttribute(val id: String, val categoryId: String, val name: String,
    val type: AttributeType, val required: Boolean, val unit: String, val productId: String? = null, val retired: Boolean = false)
data class AttributeOption(val id: String, val definitionId: String, val name: String, val retired: Boolean = false)

sealed interface AttributeValue {
    data class Text(val text: String) : AttributeValue
    data class Decimal(val exact: String) : AttributeValue
    data class Choices(val optionIds: Set<String>) : AttributeValue
}

/** Plain ASCII decimal, at most 18 integral and 12 fractional digits; no rounding. */
fun exactDecimal(input: String): BigDecimal {
    val text = input.trim()
    require(text.matches(Regex("[+-]?[0-9]{1,18}(\\.[0-9]{1,12})?"))) {
        "Enter a plain decimal with up to 18 whole and 12 decimal digits (no exponent)"
    }
    return BigDecimal(text)
}

/** Missing measurements sort last, with stable identity ordering for ties. */
fun productsByDecimal(products: List<Product>, definitionId: String): List<Product> = products.sortedWith(
    compareBy<Product, BigDecimal?>(nullsLast()) {
        (it.attributes[definitionId] as? AttributeValue.Decimal)?.let { value -> exactDecimal(value.exact) }
    }.thenBy { it.id }
)

fun CategoryAttribute.appliesTo(categoryId: String, productId: String?): Boolean =
    if (this.productId == null) this.categoryId == categoryId else this.productId == productId

fun ShopSnapshot.missingRequired(productId: String): Set<String> {
    val product = products.single { it.id == productId }
    return attributes.filter { !it.retired && it.required && it.appliesTo(product.categoryId, productId) }.filter {
        when (val value = product.attributes[it.id]) {
            null -> true
            is AttributeValue.Text -> value.text.isBlank()
            is AttributeValue.Choices -> value.optionIds.isEmpty()
            is AttributeValue.Decimal -> false
        }
    }.map { it.id }.toSet()
}
