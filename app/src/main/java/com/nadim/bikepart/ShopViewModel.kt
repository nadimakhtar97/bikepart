package com.nadim.bikepart

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.first

data class CartDraft(val cart: Cart, val customer: String = cart.customer,
    val quantities: Map<String, String> = cart.lines.associate { it.productId to it.quantity.toString() }, val dirty: Boolean = false)

data class ShopError(val message: String, val field: ProductField? = null, val similar: Boolean = false, val definitionId: String? = null)
enum class DriveAction { Connect, Publish, ListRestorePoints, ValidateRestore }
class ShopViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private var shop: Shop? = null
    private val readyShop = kotlinx.coroutines.flow.MutableStateFlow<Shop?>(null)
    private var openFailure: Throwable? = null
    val driveStatus = kotlinx.coroutines.flow.MutableStateFlow<DriveBackupStatus?>(null)
    val driveBusy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val driveUploading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val backgroundCapture = kotlinx.coroutines.flow.MutableStateFlow<Pair<Long, Long>?>(null)
    fun setBackupMobileData(allowed: Boolean) = driveOperation {
        shop!!.driveBackup.setAllowMobileData(allowed)
        AutomaticBackup.reconcile(getApplication(), shop!!, policyChanged = true)
    }
    val driveError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val driveFolders = kotlinx.coroutines.flow.MutableStateFlow<List<DriveEntry>>(emptyList())
    val driveAccount = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val restorePoints = kotlinx.coroutines.flow.MutableStateFlow<List<RestorePoint>?>(null)
    val selectedRestore = kotlinx.coroutines.flow.MutableStateFlow<RestorePoint?>(null)
    val restoreValidated = kotlinx.coroutines.flow.MutableStateFlow(false)
    val restoreComplete = kotlinx.coroutines.flow.MutableStateFlow(false)
    val recoveryCapture = kotlinx.coroutines.flow.MutableStateFlow<LocalCapture?>(null)
    private var stagedRestore: ValidatedRestore? = null
    private var driveTransport: GoogleDriveTransport? = null
    fun driveAuthorizing() { driveBusy.value = true; driveError.value = null }
    fun driveFailure(message: String) { driveBusy.value = false; driveError.value = message }
    fun authorizedDrive(account: String, token: String, action: DriveAction) {
        val transport = GoogleDriveTransport(token)
        driveOperation {
            if (action != DriveAction.Connect) {
                require(shop!!.driveBackup.status().target?.account == account) { "Backup account changed. Reconnect the chosen account." }
            }
            when (action) {
                DriveAction.Publish -> shop!!.driveBackup.publish(transport) { revision, capturedAt ->
                    driveStatus.value = driveStatus.value?.copy(pending = true, pendingRevision = revision, pendingCapturedAt = capturedAt)
                }
                DriveAction.ListRestorePoints -> discoverRestorePoints(transport)
                DriveAction.ValidateRestore -> prepareSelectedRestore(transport)
                DriveAction.Connect -> {
                    driveTransport = transport; driveAccount.value = account
                    driveFolders.value = transport.folders()
                }
            }
        }
    }
    fun selectRestorePoint(point: RestorePoint) {
        if (driveBusy.value) return
        clearStagedRestore()
        selectedRestore.value = point
        driveError.value = null
    }
    fun loadRestorePoints(transport: DriveTransport) = driveOperation { discoverRestorePoints(transport) }
    fun validateSelectedRestore(transport: DriveTransport) = driveOperation { prepareSelectedRestore(transport) }
    fun activateSelectedRestore() {
        if (driveBusy.value || busy.value || backupBusy.value || shop == null) return
        busy.value = true
        driveOperation {
            try {
                require(cartDraft.value?.dirty != true) { "Save or reload the unfinished cart before replacing the shop." }
                val previous = requireNotNull(shop)
                recoveryCapture.value = previous.activateRestore(requireNotNull(stagedRestore) { "Validate a restore point first." }, confirmed = true)
                localCapture.value = null
                clearStagedRestore(); selectedRestore.value = null; restorePoints.value = null
                driveAccount.value = null; driveTransport = null; driveFolders.value = emptyList()
                cartAutosave?.cancel(); cartDraft.value = null
                // The observer reopens on the root change; don't report completion until it has done so.
                val reopened = requireNotNull(readyShop.first { it !== previous }) {
                    "Could not reopen the restored shop. Reopen the app to retry: ${openFailure?.message.orEmpty()}"
                }
                try { AutomaticBackup.reconcile(getApplication(), reopened, policyChanged = true) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Startup reconciliation retries the durable uncovered generation. */ }
                restoreComplete.value = true
            } finally { busy.value = false }
        }
    }
    private suspend fun discoverRestorePoints(transport: DriveTransport) {
        clearStagedRestore(); selectedRestore.value = null; restorePoints.value = null
        restorePoints.value = shop!!.driveBackup.restorePoints(transport)
    }
    private suspend fun prepareSelectedRestore(transport: DriveTransport) {
        clearStagedRestore()
        stagedRestore = shop!!.driveBackup.validateRestore(transport, requireNotNull(selectedRestore.value) { "Choose a restore point first." })
        restoreValidated.value = true
    }
    private fun clearStagedRestore() {
        restoreValidated.value = false
        restoreComplete.value = false
        stagedRestore?.close(); stagedRestore = null
    }
    fun selectDriveFolder(folder: DriveEntry) = driveOperation {
        connectDriveFolder(folder)
    }
    fun createDriveFolder(name: String) = driveOperation {
        val folder = requireNotNull(driveTransport).createFolder(name)
        connectDriveFolder(folder)
    }
    private suspend fun connectDriveFolder(folder: DriveEntry) {
        shop!!.driveBackup.connect(DriveTarget(requireNotNull(driveAccount.value), folder.id, folder.name))
        AutomaticBackup.reconcile(getApplication(), shop!!)
        driveAccount.value = null; driveFolders.value = emptyList(); driveTransport = null
        clearStagedRestore(); selectedRestore.value = null; restorePoints.value = null
    }
    private fun driveOperation(block: suspend () -> Unit) {
        driveBusy.value = true; driveError.value = null
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { driveError.value = error.message ?: "Drive backup failed. Retry or repair the connection." }
            finally { driveStatus.value = shop?.driveBackup?.status(); driveBusy.value = false }
        }
    }
    val state = kotlinx.coroutines.flow.MutableStateFlow<ShopSnapshot?>(null)
    val error = kotlinx.coroutines.flow.MutableStateFlow<ShopError?>(null)
    val busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val backupBusy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val localCapture = kotlinx.coroutines.flow.MutableStateFlow<LocalCapture?>(null)
    val backupError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val backupSaved = kotlinx.coroutines.flow.MutableStateFlow(false)
    val exportedCaptureId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
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
                androidx.work.WorkManager.getInstance(application).getWorkInfosByTagFlow("com.nadim.bikepart.BackupWorker").collect { work ->
                    val running = work.firstOrNull { it.state == androidx.work.WorkInfo.State.RUNNING }
                    driveUploading.value = running != null
                    backgroundCapture.value = running?.progress?.let { data ->
                        if (data.keyValueMap.containsKey("revision")) data.getLong("revision", 0) to data.getLong("capturedAt", 0) else null
                    }
                }
            }
            viewModelScope.launch {
                while (true) {
                    try { driveStatus.value = shop?.driveBackup?.status() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { driveError.value = "Could not read backup status. Check phone storage and retry." }
                    delay(2_000)
                }
            }
            viewModelScope.launch {
                val latest = opened.latestLocalBackup()
                if (localCapture.value == null) localCapture.value = latest
                recoveryCapture.value = opened.restoreRecoveryCopy()
            }
            var current = opened
            while (true) {
                try {
                    current.snapshots.collect {
                        state.value = it
                        readyShop.value = current
                        viewModelScope.launch {
                            try { AutomaticBackup.reconcile(application, current) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { /* Startup and periodic work reconcile missed hints. */ }
                        }
                    }
                } catch (_: ShopReplaced) {
                    current.close()
                    current = Shop.open(application)
                    shop = current
                    driveStatus.value = current.driveBackup.status()
                    recoveryCapture.value = current.restoreRecoveryCopy()
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            openFailure = e; readyShop.value = null
            error.value = ShopError("Could not open shop. Reopen the app to retry.")
        }
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
        exportedCaptureId.value = null
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
        exportedCaptureId.value = capture.id
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
    override fun onCleared() { clearStagedRestore(); shop?.close() }
}
