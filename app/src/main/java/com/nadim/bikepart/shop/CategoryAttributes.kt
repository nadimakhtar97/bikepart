package com.nadim.bikepart.shop

import android.icu.lang.UCharacter
import java.math.BigDecimal
import java.text.Normalizer

/** Lookup normalization v1: NFKC, Unicode whitespace folding, full default case folding. */
object LookupNames {
    const val VERSION = 1
    fun display(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replace(Regex("[\\s\\p{Z}\\u0085]+"), " ").trim()
    fun key(value: String): String = UCharacter.foldCase(display(value), true)
}

enum class AttributeType(val label: String) {
    Text("Text"), Decimal("Decimal number"), SingleChoice("Single choice"), MultipleChoice("Multiple choice");
    val isChoice: Boolean get() = this == SingleChoice || this == MultipleChoice
}
data class CategoryAttribute(val id: String, val categoryId: String, val name: String,
    val type: AttributeType, val required: Boolean, val unit: String)
data class AttributeOption(val id: String, val definitionId: String, val name: String)

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
