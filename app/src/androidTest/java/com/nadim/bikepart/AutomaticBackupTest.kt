package com.nadim.bikepart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkManager
import com.nadim.bikepart.shop.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import androidx.work.OneTimeWorkRequestBuilder
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AutomaticBackupTest {
    @Test fun realWorkerDefersWithoutWifiAndSalesRemainUsableAcrossReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = WorkManager.getInstance(context)
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
        }
        val wifi = shell("settings get global wifi_on")
        val request = OneTimeWorkRequestBuilder<BackupWorker>().build()
        try {
            shell("svc wifi disable")
            val network = context.getSystemService(android.net.ConnectivityManager::class.java)
            withTimeout(15_000) {
                while (network.getNetworkCapabilities(network.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true) delay(100)
            }
            manager.cancelAllWork().result.get()
            Shop.open(context).use { shop ->
                shop.driveBackup.connect(DriveTarget("offline@example.com", "offline-folder", "Offline test"))
                shop.driveBackup.setAllowMobileData(false)
                shop.driveBackup.recordFailure("Network unavailable. Retry when connected.")
            }
            manager.enqueue(request).result.get()
            withTimeout(30_000) {
                while (manager.getWorkInfoById(request.id).get()!!.runAttemptCount == 0) delay(100)
            }
            withTimeout(30_000) {
                while (manager.getWorkInfoById(request.id).get()!!.state == androidx.work.WorkInfo.State.RUNNING) delay(100)
            }
            assertEquals(androidx.work.WorkInfo.State.ENQUEUED, manager.getWorkInfoById(request.id).get()!!.state)
            Shop.open(context).use { shop ->
                val product = shop.createProduct("Offline worker bolt ${UUID.randomUUID()}", shop.snapshot().categories.first().id, "", "3", "10")
                val cart = shop.createCart()
                shop.addCartProduct(cart, 0, product, "1")
                val saved = shop.snapshot().carts.single { it.id == cart }
                shop.checkout(cart, saved.editVersion, "20")
            }
            Shop.open(context).use { shop ->
                assertTrue(shop.snapshot().sales.isNotEmpty())
                assertNotNull(shop.driveBackup.status().failure)
            }
        } finally {
            manager.cancelWorkById(request.id).result.get()
            if (wifi == "1") shell("svc wifi enable")
        }
    }
    @Test fun startupReconcilesMissedSchedulingAndCoalescesWithoutMutatingTheShop() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "schedule-${UUID.randomUUID()}.db"
        val manager = WorkManager.getInstance(context)
        try {
            Shop.open(context, name).use { shop ->
                shop.driveBackup.connect(DriveTarget("owner@example.com", "folder", "Shop"))
                shop.setAppearance(Appearance.Dark)
                // Simulate process death before the enqueue hint was delivered.
            }
            Shop.open(context, name).use { shop ->
                val before = shop.snapshot()
                AutomaticBackup.reconcile(context, shop)
                val first = manager.getWorkInfosForUniqueWork(AutomaticBackup.WORK).get().filter { !it.state.isFinished }.single()
                AutomaticBackup.reconcile(context, shop)
                val second = manager.getWorkInfosForUniqueWork(AutomaticBackup.WORK).get().filter { !it.state.isFinished }.single()
                assertEquals(first.id, second.id)
                assertEquals(NetworkType.UNMETERED, second.constraints.requiredNetworkType)
                shop.driveBackup.setAllowMobileData(true)
                AutomaticBackup.reconcile(context, shop, policyChanged = true)
                val mobile = manager.getWorkInfosForUniqueWork(AutomaticBackup.WORK).get().filter { !it.state.isFinished }.single()
                assertEquals(NetworkType.CONNECTED, mobile.constraints.requiredNetworkType)
                assertEquals(before, shop.snapshot())
            }
        } finally {
            manager.cancelUniqueWork(AutomaticBackup.WORK).result.get()
            manager.cancelUniqueWork("${AutomaticBackup.WORK}-reconcile").result.get()
            context.deleteDatabase(name)
        }
    }
}
