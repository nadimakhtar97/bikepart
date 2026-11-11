package com.nadim.bikepart

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

data class CartDraft(val cart: Cart, val customer: String = cart.customer,
    val quantities: Map<String, String> = cart.lines.associate { it.productId to it.quantity.toString() }, val dirty: Boolean = false)

data class ShopError(val message: String, val field: ProductField? = null, val similar: Boolean = false, val definitionId: String? = null)
class ShopViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private var shop: Shop? = null
    val state = kotlinx.coroutines.flow.MutableStateFlow<ShopSnapshot?>(null)
    val error = kotlinx.coroutines.flow.MutableStateFlow<ShopError?>(null)
    val busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val backupBusy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val localCapture = kotlinx.coroutines.flow.MutableStateFlow<LocalCapture?>(null)
    val backupError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val backupSaved = kotlinx.coroutines.flow.MutableStateFlow(false)
    val cartDraft = kotlinx.coroutines.flow.MutableStateFlow<CartDraft?>(null)
    private var cartAutosave: Job? = null
    private var afterCartSave: (() -> Unit)? = null

    fun openCart(id: String) {
        cartAutosave?.cancel()
        cartDraft.value = state.value?.carts?.find { it.id == id }?.let { CartDraft(it) }
        error.value = null
    }

    fun editCart(customer: String? = null, productId: String? = null, quantity: String? = null) {
        val draft = cartDraft.value ?: return
        cartDraft.value = draft.copy(customer = customer ?: draft.customer,
            quantities = if (productId != null && quantity != null) draft.quantities + (productId to quantity) else draft.quantities, dirty = true)
        error.value = null
        cartAutosave?.cancel()
        cartAutosave = viewModelScope.launch { delay(450); flushCart() }
    }

    /** Navigation and checkout use this same save barrier; failed drafts stay visible. */
    fun flushCart(done: (() -> Unit)? = null) {
        if (done != null) afterCartSave = done
        cartAutosave?.cancel()
        if (busy.value) return
        val draft = cartDraft.value
        if (draft == null || !draft.dirty) { finishCartSave(); return }
        var committedVersion = draft.cart.editVersion
        write({ committedVersion = saveCart(draft.cart.id, draft.cart.editVersion, draft.customer, draft.quantities) }, {
            val committed = state.value?.carts?.find { it.id == draft.cart.id } ?: throw StaleCart()
            // Never rebase a buffered draft over another writer's intervening edit.
            if (committed.editVersion != committedVersion) throw StaleCart()
            val latest = cartDraft.value ?: draft
            val pending = latest.customer != draft.customer || latest.quantities != draft.quantities
            cartDraft.value = latest.copy(cart = committed, dirty = pending)
            if (pending) {
                cartAutosave?.cancel()
                cartAutosave = viewModelScope.launch { yield(); flushCart() }
            } else finishCartSave()
        }, failed = { afterCartSave = null })
    }

    private fun finishCartSave() {
        val done = afterCartSave
        afterCartSave = null
        done?.invoke()
    }

    fun reloadCart() {
        val id = cartDraft.value?.cart?.id ?: return
        cartAutosave?.cancel()
        write({ Unit }, { openCart(id) })
    }
    init { viewModelScope.launch {
        try {
            val opened = Shop.open(application)
            shop = opened
            viewModelScope.launch {
                val latest = opened.latestLocalBackup()
                if (localCapture.value == null) localCapture.value = latest
            }
            opened.snapshots.collect { state.value = it }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = ShopError("Could not open shop. Reopen the app to retry.") }
    } }
    fun write(action: suspend Shop.() -> Unit, done: () -> Unit = {}, failed: () -> Unit = {}) {
        if (busy.value || shop == null) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            var successful = false
            try { action(requireNotNull(shop)); state.value = requireNotNull(shop).snapshot(); done(); successful = true }
            catch (e: CancellationException) { throw e }
            catch (e: SimilarProduct) { error.value = ShopError(requireNotNull(e.message), similar = true) }
            catch (e: ShopValidation) { error.value = ShopError(requireNotNull(e.message), field = e.field, definitionId = e.definitionId) }
            catch (e: IllegalArgumentException) { error.value = ShopError(e.message ?: "Check the entered values") }
            catch (e: Exception) { error.value = ShopError("Could not save. Try again.") }
            finally { busy.value = false; if (!successful) failed() }
        }
    }
    suspend fun readPhoto(reference: PhotoReference): ByteArray? = shop?.readPhoto(reference)

    fun captureBackup() {
        val opened = shop ?: return
        if (backupBusy.value) return
        backupBusy.value = true
        backupError.value = null
        backupSaved.value = false
        viewModelScope.launch {
            try { localCapture.value = opened.captureLocalBackup() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { backupError.value = "Could not capture local backup. Check phone storage and product photos, then try again." }
            finally { backupBusy.value = false }
        }
    }

    fun saveBackupCopy(uri: android.net.Uri, capture: LocalCapture) {
        if (backupBusy.value) return
        backupBusy.value = true
        backupError.value = null
        backupSaved.value = false
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    LocalBackup.inspect(capture.file)
                    requireNotNull(getApplication<android.app.Application>().contentResolver.openOutputStream(uri, "wt")).use { output ->
                        capture.file.inputStream().use { it.copyTo(output) }
                    }
                }
                backupSaved.value = true
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { backupError.value = "Could not save the copy. The verified local capture is still on this phone. Try another destination." }
            finally { backupBusy.value = false }
        }
    }
    override fun onCleared() { shop?.close() }
}
