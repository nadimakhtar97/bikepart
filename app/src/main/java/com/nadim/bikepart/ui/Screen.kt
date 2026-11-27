package com.nadim.bikepart.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed class Screen : NavKey, java.io.Serializable {
    @Serializable
    data object Home : Screen()
    @Serializable
    data object Stock : Screen()
    @Serializable
    data object Carts : Screen()
    @Serializable
    data object More : Screen()
    @Serializable
    data object AppearanceSettings : Screen()
    @Serializable
    data object DeveloperOption : Screen()
    @Serializable
    data object Backups : Screen()
    @Serializable
    data object Scan : Screen()
    @Serializable
    data object ReceiveProducts : Screen()
    @Serializable
    data object CatalogSearch : Screen()
    @Serializable
    data object Arrival : Screen()
    @Serializable
    data object Cart : Screen()
    @Serializable
    data object Checkout : Screen()
    @Serializable
    data object Sales : Screen()
    @Serializable
    data object Receipt : Screen()
    @Serializable
    data object CartProducts : Screen()
    @Serializable
    data object Create : Screen()
    @Serializable
    data object Edit : Screen()
    @Serializable
    data object TemplateForm : Screen()
    @Serializable
    data object Templates : Screen()
    @Serializable
    data object TemplateDetail : Screen()
    @Serializable
    data object Correction : Screen()
    @Serializable
    data object ArchivedProducts : Screen()
    @Serializable
    data object Labels : Screen()
    @Serializable
    data object Detail : Screen()
    @Serializable
    data object Fields : Screen()
    @Serializable
    data object FindingLookups : Screen()
    @Serializable
    data object Categories : Screen()

    @Serializable
    data class CategoryDetail(val categoryId: String) : Screen()
}
