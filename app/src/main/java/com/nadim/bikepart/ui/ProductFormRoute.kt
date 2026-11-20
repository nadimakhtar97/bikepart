package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.CatalogDraft
import com.nadim.bikepart.shop.Product
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.ui.theme.ShopSpacing

internal enum class ProductFormMode { Create, Edit, Template }

internal data class ProductFormRouteContext(
    val shop: ShopSnapshot,
    val product: Product?,
    val busy: Boolean,
    val error: ShopError?,
    val stateHolder: SaveableStateHolder,
    val stateKey: String,
    val routeKey: String,
    val lookups: ProductLookups,
    val findingLookups: FindingLookups,
    val loadPhoto: PhotoLoader,
    val onManageCategory: (String) -> Unit,
    val onChange: () -> Unit,
    val onSave: (ProductFormMode, ProductFormInput) -> Unit,
)

@Composable
internal fun ProductFormRoute(
    context: ProductFormRouteContext,
    mode: ProductFormMode,
    initialCatalog: CatalogDraft? = null,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ShopSpacing.content),
    ) {
        context.stateHolder.SaveableStateProvider(context.stateKey) {
            key(context.routeKey) {
                ProductForm(
                    shop = context.shop,
                    product = if (mode == ProductFormMode.Edit) context.product else null,
                    busy = context.busy,
                    error = context.error,
                    onChange = context.onChange,
                    lookups = context.lookups,
                    findingLookups = context.findingLookups,
                    loadPhoto = context.loadPhoto,
                    onManageCategory = context.onManageCategory,
                    initialCatalog = if (mode == ProductFormMode.Edit) null else initialCatalog,
                    templateMode = mode == ProductFormMode.Template,
                    save = { context.onSave(mode, it) },
                )
            }
        }
    }
}
