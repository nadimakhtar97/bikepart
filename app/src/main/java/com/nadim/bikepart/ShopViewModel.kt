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

class ShopError(val message: String, val field: ProductField? = null, val similar: Boolean = false, val definitionId: String? = null)
enum class DriveAction { Connect, Publish, ListRestorePoints, ValidateRestore, ReviewPhone, ActivatePhone, CheckPhone, InspectStorage, CleanupStorage }
class ShopViewModel(application: android.app.Application) : AndroidViewModel(application) {
    val themePreferences = com.nadim.bikepart.ui.theme.ShopThemePreferences(application)
    private var shop: Shop? = null
    private val readyShop = kotlinx.coroutines.flow.MutableStateFlow<Shop?>(null)
    private var openFailure: Throwable? = null
    val driveStatus = kotlinx.coroutines.flow.MutableStateFlow<DriveBackupStatus?>(null)
    val driveBusy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val backupStorage = kotlinx.coroutines.flow.MutableStateFlow<BackupStorage?>(null)
    val cleanupComplete = kotlinx.coroutines.flow.MutableStateFlow(false)
    val storageProgress = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    fun storageDriveAction(transport: DriveTransport, action: DriveAction) = driveOperation { performStorageAction(transport, action) }
    private suspend fun performStorageAction(transport: DriveTransport, action: DriveAction) {
        val reviewed = backupStorage.value
        backupStorage.value = null; cleanupComplete.value = false
        storageProgress.value = getApplication<android.app.Application>().getString(R.string.verifying_backup_storage_and_protected_recovery)
        try {
            when (action) {
                DriveAction.InspectStorage -> backupStorage.value = shop!!.driveBackup.inspectStorage(transport)
                DriveAction.CleanupStorage -> {
                    storageProgress.value = getApplication<android.app.Application>().getString(R.string.cleaning_reviewed_history_interrupted_work_can)
                    backupStorage.value = shop!!.driveBackup.cleanupStorage(transport, requireNotNull(reviewed) { getApplication<android.app.Application>().getString(R.string.inspect_backup_storage_first) })
                    clearStagedRestore(); selectedRestore.value = null; restorePoints.value = null
                    cleanupComplete.value = true
                }
                else -> error(getApplication<android.app.Application>().getString(R.string.choose_a_backup_storage_action))
            }
        } finally { storageProgress.value = null }
    }
    val phoneReplacement = kotlinx.coroutines.flow.MutableStateFlow<PhoneReplacement?>(null)
    fun cancelPhoneReplacement() { if (!driveBusy.value) phoneReplacement.value = null }
    fun retirePhone() = driveOperation {
        shop!!.driveBackup.retireInstallation(confirmed = true)
        phoneReplacement.value = null
    }
    fun phoneDriveAction(transport: DriveTransport, action: DriveAction) = driveOperation { performPhoneAction(transport, action) }
    private suspend fun performPhoneAction(transport: DriveTransport, action: DriveAction) {
        backupStorage.value = null; cleanupComplete.value = false
        when (action) {
            DriveAction.ReviewPhone -> {
                phoneReplacement.value = null
                phoneReplacement.value = shop!!.driveBackup.previewReplacement(transport)
            }
            DriveAction.ActivatePhone -> {
                shop!!.driveBackup.activateReplacement(transport, requireNotNull(phoneReplacement.value) { getApplication<android.app.Application>().getString(R.string.review_replacement_first) }, oldInstallationRetired = true)
                phoneReplacement.value = null
                AutomaticBackup.reconcile(getApplication(), shop!!, policyChanged = true)
            }
            DriveAction.CheckPhone -> {
                shop!!.driveBackup.requireActivePhone(transport)
                phoneReplacement.value = null
                AutomaticBackup.reconcile(getApplication(), shop!!)
            }
            else -> error(getApplication<android.app.Application>().getString(R.string.choose_a_phone_action))
        }
    }
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
    fun authorizedDrive(account: String, token: String, action: DriveAction, fullMetadataAccess: Boolean = false) {
        val transport = GoogleDriveTransport(getApplication(), token, fullMetadataAccess)
        driveOperation {
            if (action != DriveAction.Connect) {
                require(shop!!.driveBackup.status().target?.account == account) { getApplication<android.app.Application>().getString(R.string.backup_account_changed_reconnect_the_chosen) }
            }
            when (action) {
                DriveAction.Publish -> shop!!.driveBackup.publish(transport) { revision, capturedAt ->
                    driveStatus.value = driveStatus.value?.copy(pending = true, pendingRevision = revision, pendingCapturedAt = capturedAt)
                }
                DriveAction.ListRestorePoints -> discoverRestorePoints(transport)
                DriveAction.ValidateRestore -> prepareSelectedRestore(transport)
                DriveAction.InspectStorage, DriveAction.CleanupStorage -> performStorageAction(transport, action)
                DriveAction.ReviewPhone, DriveAction.ActivatePhone, DriveAction.CheckPhone -> performPhoneAction(transport, action)
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
                require(cartDraft.value?.dirty != true) { getApplication<android.app.Application>().getString(R.string.save_or_reload_the_unfinished_cart) }
                val previous = requireNotNull(shop)
                recoveryCapture.value = previous.activateRestore(requireNotNull(stagedRestore) { getApplication<android.app.Application>().getString(R.string.validate_a_restore_point_first) }, confirmed = true)
                localCapture.value = null
                clearStagedRestore(); selectedRestore.value = null; restorePoints.value = null
                phoneReplacement.value = null
                backupStorage.value = null; cleanupComplete.value = false
                driveAccount.value = null; driveTransport = null; driveFolders.value = emptyList()
                cartAutosave?.cancel(); cartDraft.value = null
                // The observer reopens on the root change; don't report completion until it has done so.
                val reopened = requireNotNull(readyShop.first { it !== previous }) {
                    getApplication<android.app.Application>().getString(R.string.could_not_reopen_the_restored_shop, openFailure?.message.orEmpty())
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
        backupStorage.value = null; cleanupComplete.value = false
        phoneReplacement.value = null
        restorePoints.value = shop!!.driveBackup.restorePoints(transport)
    }
    private suspend fun prepareSelectedRestore(transport: DriveTransport) {
        clearStagedRestore()
        stagedRestore = shop!!.driveBackup.validateRestore(transport, requireNotNull(selectedRestore.value) { getApplication<android.app.Application>().getString(R.string.choose_a_restore_point_first) })
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
        backupStorage.value = null; cleanupComplete.value = false
    }
    private fun driveOperation(block: suspend () -> Unit) {
        driveBusy.value = true; driveError.value = null
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { driveError.value = error.message ?: getApplication<android.app.Application>().getString(R.string.drive_backup_failed_retry_or_repair) }
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
            val committed = state.value?.carts?.find { it.id == draft.cart.id } ?: throw StaleCart(getApplication<android.app.Application>().getString(R.string.this_cart_changed_reload_the_saved))
            // Never rebase a buffered draft over another writer's intervening edit.
            if (committed.editVersion != committedVersion) throw StaleCart(getApplication<android.app.Application>().getString(R.string.this_cart_changed_reload_the_saved))
            val latest = cartDraft.value ?: draft
            val pending = latest.customer != draft.customer || latest.quantities != draft.quantities
            val quantities = latest.quantities.filter { (id, value) ->
                committed.lines.any { it.productId == id } || value != draft.quantities[id]
            }
            cartDraft.value = latest.copy(cart = committed, quantities = quantities, dirty = pending)
            if (pending) {
                cartAutosave?.cancel()
                cartAutosave = viewModelScope.launch { yield(); flushCart() }
            } else finishCartSave()
        }, failed = { afterCartSave = null })
    }

    private fun refreshCartAfterStockChange(snapshot: ShopSnapshot) {
        val draft = cartDraft.value ?: return
        if (draft.dirty) return
        val saved = snapshot.carts.find { it.id == draft.cart.id } ?: return
        val reducedAgain = draft.cart.lines.any { line ->
            val reduced = saved.stockReductions[line.productId] ?: return@any false
            reduced < line.quantity &&
                (saved.lines.find { it.productId == line.productId }?.quantity ?: 0) == reduced
        }
        if (saved.stockReductions != draft.cart.stockReductions || reducedAgain) {
            cartDraft.value = draft.copy(
                cart = saved,
                customer = if (draft.cart.customer == saved.customer) draft.customer else saved.customer,
                quantities = saved.lines.associate { it.productId to it.quantity.toString() },
            )
        }
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
                    catch (_: Exception) { driveError.value = getApplication<android.app.Application>().getString(R.string.could_not_read_backup_status_check) }
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
                        if (!busy.value) refreshCartAfterStockChange(it)
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
            error.value = ShopError(getApplication<android.app.Application>().getString(R.string.could_not_open_shop_reopen_the))
        }
    } }
    fun write(action: suspend Shop.() -> Unit, done: () -> Unit = {}, failed: () -> Unit = {}) {
        if (busy.value || shop == null) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            var successful = false
            try {
                val current = requireNotNull(shop)
                action(current)
                val snapshot = current.snapshot()
                state.value = snapshot
                done()
                refreshCartAfterStockChange(snapshot)
                successful = true
            }
            catch (e: CancellationException) { throw e }
            catch (e: SimilarProduct) { error.value = ShopError(requireNotNull(e.message), similar = true) }
            catch (e: ShopValidation) { error.value = ShopError(requireNotNull(e.message), field = e.field, definitionId = e.definitionId) }
            catch (e: IllegalArgumentException) { error.value = ShopError(e.message ?: getApplication<android.app.Application>().getString(R.string.check_the_entered_values)) }
            catch (e: Exception) { error.value = ShopError(getApplication<android.app.Application>().getString(R.string.could_not_save_try_again)) }
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
            catch (_: Exception) { backupError.value = getApplication<android.app.Application>().getString(R.string.could_not_capture_local_backup_check) }
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
                    LocalBackup.inspect(getApplication<android.app.Application>(), capture.file)
                    requireNotNull(getApplication<android.app.Application>().contentResolver.openOutputStream(uri, "wt")).use { output ->
                        capture.file.inputStream().use { it.copyTo(output) }
                    }
                }
                backupSaved.value = true
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { backupError.value = getApplication<android.app.Application>().getString(R.string.could_not_save_the_copy_the) }
            finally { backupBusy.value = false }
        }
    }
    override fun onCleared() { clearStagedRestore(); shop?.close() }
}
