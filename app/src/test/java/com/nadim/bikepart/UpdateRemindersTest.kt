package com.nadim.bikepart.updates

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class UpdateRemindersTest {
    private class InMemoryStorage : ReminderStorage {
        private val data = mutableMapOf<String, String>()
        override fun getString(key: String): String? = data[key]
        override fun putString(key: String, value: String) { data[key] = value }
    }

    private var currentDate = LocalDate.of(2026, 11, 15)
    private val storage = InMemoryStorage()
    private val reminders = UpdateReminders(storage, clock = { currentDate })

    @Test
    fun allowsAutomaticCheckOncePerCalendarDay() {
        assertTrue(reminders.shouldCheckAutomatically())
        reminders.recordCheckAttempt()
        assertFalse(reminders.shouldCheckAutomatically())

        // Same day: still false
        assertFalse(reminders.shouldCheckAutomatically())

        // Next calendar day: allowed again
        currentDate = currentDate.plusDays(1)
        assertTrue(reminders.shouldCheckAutomatically())
    }

    @Test
    fun checkAttemptPersistedEvenAfterFailureToPreventRetryStorms() {
        assertTrue(reminders.shouldCheckAutomatically())
        reminders.recordCheckAttempt() // Simulating check started/failed
        assertFalse(reminders.shouldCheckAutomatically())

        // Recreating reminders instance with same storage
        val newInstance = UpdateReminders(storage, clock = { currentDate })
        assertFalse(newInstance.shouldCheckAutomatically())
    }

    @Test
    fun laterDismissalSuppressesReminderForSameReleaseOnSameDay() {
        val release1 = AvailableUpdate(1L, 10L, "1.1.0", 5_000_000L, "Summary", "url", "hash", 26)
        assertTrue(reminders.shouldShowReminder(release1))

        reminders.dismissReminder(release1)
        assertFalse(reminders.shouldShowReminder(release1))

        // Same day, same release -> still suppressed
        val newInstance = UpdateReminders(storage, clock = { currentDate })
        assertFalse(newInstance.shouldShowReminder(release1))

        // Next day, same release -> allowed again
        currentDate = currentDate.plusDays(1)
        assertTrue(reminders.shouldShowReminder(release1))
    }

    @Test
    fun newlyDiscoveredHigherReleaseHasOwnReminderIdentity() {
        val release1 = AvailableUpdate(1L, 10L, "1.1.0", 5_000_000L, "Summary", "url", "hash", 26)
        reminders.dismissReminder(release1)
        assertFalse(reminders.shouldShowReminder(release1))

        val release2 = AvailableUpdate(2L, 11L, "1.2.0", 5_500_000L, "New Summary", "url", "hash", 26)
        // Higher release should be shown immediately even on same day
        assertTrue(reminders.shouldShowReminder(release2))
    }
}
