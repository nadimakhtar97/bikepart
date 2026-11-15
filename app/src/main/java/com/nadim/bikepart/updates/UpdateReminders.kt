package com.nadim.bikepart.updates

import android.content.Context
import java.time.LocalDate

interface ReminderStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class SharedPreferencesReminderStorage(private val context: Context) : ReminderStorage {
    private val prefs by lazy { context.getSharedPreferences("bikepart_update_reminders", Context.MODE_PRIVATE) }
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

class UpdateReminders(
    private val storage: ReminderStorage,
    private val clock: () -> LocalDate = { LocalDate.now() }
) {
    companion object {
        private const val KEY_LAST_CHECK_DATE = "last_check_date"
        private const val KEY_DISMISSED_DATE_PREFIX = "dismissed_date_"
    }

    fun shouldCheckAutomatically(): Boolean {
        val today = clock().toString()
        val lastCheck = storage.getString(KEY_LAST_CHECK_DATE)
        return lastCheck != today
    }

    fun recordCheckAttempt() {
        val today = clock().toString()
        storage.putString(KEY_LAST_CHECK_DATE, today)
    }

    fun shouldShowReminder(release: AvailableUpdate): Boolean {
        val today = clock().toString()
        val dismissedDate = storage.getString(KEY_DISMISSED_DATE_PREFIX + release.versionCode)
        return dismissedDate != today
    }

    fun dismissReminder(release: AvailableUpdate) {
        val today = clock().toString()
        storage.putString(KEY_DISMISSED_DATE_PREFIX + release.versionCode, today)
    }
}
