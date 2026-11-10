package com.nadim.bikepart

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class ShopError(val message: String, val field: ProductField? = null, val similar: Boolean = false, val definitionId: String? = null)
class ShopViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private var shop: Shop? = null
    val state = kotlinx.coroutines.flow.MutableStateFlow<ShopSnapshot?>(null)
    val error = kotlinx.coroutines.flow.MutableStateFlow<ShopError?>(null)
    val busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    init { viewModelScope.launch {
        try {
            val opened = Shop.open(application)
            shop = opened
            opened.snapshots.collect { state.value = it }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = ShopError("Could not open shop. Reopen the app to retry.") }
    } }
    fun write(action: suspend Shop.() -> Unit, done: () -> Unit = {}) {
        if (busy.value || shop == null) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            try { action(requireNotNull(shop)); state.value = requireNotNull(shop).snapshot(); done() }
            catch (e: CancellationException) { throw e }
            catch (e: SimilarProduct) { error.value = ShopError(requireNotNull(e.message), similar = true) }
            catch (e: ShopValidation) { error.value = ShopError(requireNotNull(e.message), field = e.field, definitionId = e.definitionId) }
            catch (e: IllegalArgumentException) { error.value = ShopError(e.message ?: "Check the entered values") }
            catch (e: Exception) { error.value = ShopError("Could not save. Try again.") }
            finally { busy.value = false }
        }
    }
    override fun onCleared() { shop?.close() }
}
