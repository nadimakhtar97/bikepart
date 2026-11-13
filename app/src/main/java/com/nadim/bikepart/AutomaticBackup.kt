package com.nadim.bikepart

import android.accounts.Account
import android.app.Application
import android.content.Context
import androidx.work.*
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.nadim.bikepart.shop.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Durable attempts are hints: the business revision is always the source of truth. */
object AutomaticBackup {
    const val WORK = "shop-automatic-backup"
    fun request(allowMobileData: Boolean) = OneTimeWorkRequestBuilder<BackupWorker>()
        .setInitialDelay(20, TimeUnit.SECONDS)
        .setConstraints(constraints(allowMobileData))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()

    private fun constraints(mobile: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (mobile) NetworkType.CONNECTED else NetworkType.UNMETERED).build()

    suspend fun reconcile(context: Context, shop: Shop, policyChanged: Boolean = false) {
        val status = shop.driveBackup.status()
        if (status.target == null) return
        val manager = WorkManager.getInstance(context)
        // Periodic reconciliation closes the commit/enqueue and worker-completion races.
        manager.enqueueUniquePeriodicWork("$WORK-reconcile", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BackupWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints(status.allowMobileData)).build())
        if (status.pending || status.coveredRevision != shop.snapshot().revision) {
            manager.enqueueUniqueWork(WORK, if (policyChanged) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request(status.allowMobileData))
        }
    }
}

class ShopApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            // A scheduling/storage failure must never prevent opening the sales UI.
            while (isActive) {
                try {
                    Shop.open(this@ShopApplication).use { shop ->
                        shop.snapshots.map { it.revision }.distinctUntilChanged().collect {
                            try { AutomaticBackup.reconcile(this@ShopApplication, shop) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { /* Startup/periodic reconciliation retries. */ }
                        }
                    }
                } catch (_: ShopReplaced) { /* Reopen the newly activated root immediately. */ }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { delay(30_000) }
            }
        }
    }
}

class BackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Shop.open(applicationContext).use { shop ->
                val status = shop.driveBackup.status()
                val target = status.target ?: return@withContext Result.success()
                if (!status.pending && status.coveredRevision == shop.snapshot().revision) return@withContext Result.success()
                // Recheck saved policy even when an older request was eligible on mobile data.
                val network = applicationContext.getSystemService(android.net.ConnectivityManager::class.java)
                val capabilities = network.getNetworkCapabilities(network.activeNetwork)
                if (!status.allowMobileData && (network.isActiveNetworkMetered ||
                    capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) != true)) return@withContext Result.retry()
                val request = AuthorizationRequest.builder().setAccount(Account(target.account, "com.google"))
                    .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.file"))).build()
                val authorization = suspendCancellableCoroutine { continuation ->
                    Identity.getAuthorizationClient(applicationContext).authorize(request)
                        .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                        .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                }
                if (authorization.hasResolution() || authorization.accessToken == null) {
                    shop.driveBackup.recordFailure("Drive access needs repair. Use Connect or repair Drive, then Back up now.")
                    return@withContext Result.failure()
                }
                // Never open consent UI from background work or persist access tokens.
                val transport = GoogleDriveTransport(requireNotNull(authorization.accessToken)) {
                    if (!status.allowMobileData) {
                        val current = network.getNetworkCapabilities(network.activeNetwork)
                        if (network.isActiveNetworkMetered || current?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) != true)
                            throw java.io.IOException("Wi-Fi is unavailable. Automatic backup will retry on Wi-Fi.")
                    }
                }
                shop.driveBackup.publish(transport, target) { revision, time ->
                    setProgressAsync(workDataOf("revision" to revision, "capturedAt" to time))
                }
                if (shop.driveBackup.status().coveredRevision != shop.snapshot().revision) Result.retry() else Result.success()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            try { Shop.open(applicationContext).use { it.driveBackup.recordFailure(
                "Backup failed. Check network, phone and Drive storage, or Connect or repair Drive; then retry.") } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Preserve the business database even if operational storage is full. */ }
            Result.retry()
        }
    }
}
