package com.mario.niezapominajka

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderDateCalculatorTest {

    private fun date(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, 9, 45, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun assertDate(calendar: Calendar, year: Int, month: Int, day: Int) {
        assertEquals(year, calendar.get(Calendar.YEAR))
        assertEquals(month, calendar.get(Calendar.MONTH) + 1)
        assertEquals(day, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, calendar.get(Calendar.MINUTE))
    }

    @Test
    fun monthly31stReturnsTo31AfterShortFebruary() {
        val calendar = date(2025, 1, 31)

        advanceMonthlyOccurrence(calendar, 31)
        assertDate(calendar, 2025, 2, 28)

        advanceMonthlyOccurrence(calendar, 31)
        assertDate(calendar, 2025, 3, 31)
    }

    @Test
    fun monthly31stHandlesLeapFebruaryAndReturnsTo31() {
        val calendar = date(2024, 1, 31)

        advanceMonthlyOccurrence(calendar, 31)
        assertDate(calendar, 2024, 2, 29)

        advanceMonthlyOccurrence(calendar, 31)
        assertDate(calendar, 2024, 3, 31)
    }

    @Test
    fun monthly31stUses30InAprilThenReturnsTo31InMay() {
        val calendar = date(2025, 3, 31)

        advanceMonthlyOccurrence(calendar, 31)
        assertDate(calendar, 2025, 4, 30)

        advanceMonthlyOccurrence(calendar, 31)
        assertDate(calendar, 2025, 5, 31)
    }

    @Test
    fun monthly30thUsesLastDayInFebruaryButReturnsTo30() {
        val calendar = date(2025, 1, 30)

        advanceMonthlyOccurrence(calendar, 30)
        assertDate(calendar, 2025, 2, 28)

        advanceMonthlyOccurrence(calendar, 30)
        assertDate(calendar, 2025, 3, 30)
    }
    private fun millis(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 45): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun assertUtcDate(value: Long, year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 45) {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = value
        }
        assertEquals(year, calendar.get(Calendar.YEAR))
        assertEquals(month, calendar.get(Calendar.MONTH) + 1)
        assertEquals(day, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(hour, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(minute, calendar.get(Calendar.MINUTE))
    }

    @Test
    fun dailyReminderSkipsAllOccurrencesMissedDuringDowntime() {
        val next = nextFutureOccurrence(
            date = millis(2025, 1, 1),
            hour = 9,
            minute = 45,
            repeatType = "DAILY",
            monthlyDay = 0,
            nowMillis = millis(2025, 1, 10, 10, 0)
        )

        assertUtcDate(next!!, 2025, 1, 11)
    }

    @Test
    fun weeklyReminderSkipsMultipleMissedWeeks() {
        val next = nextFutureOccurrence(
            date = millis(2025, 1, 1),
            hour = 9,
            minute = 45,
            repeatType = "WEEKLY",
            monthlyDay = 0,
            nowMillis = millis(2025, 1, 20, 10, 0)
        )

        assertUtcDate(next!!, 2025, 1, 22)
    }

    @Test
    fun monthly31stSkipsSeveralMonthsAndReturnsTo31st() {
        val next = nextFutureOccurrence(
            date = millis(2025, 1, 31),
            hour = 9,
            minute = 45,
            repeatType = "MONTHLY",
            monthlyDay = 31,
            nowMillis = millis(2025, 5, 1, 10, 0)
        )

        assertUtcDate(next!!, 2025, 5, 31)
    }

    @Test
    fun futureBaseDateIsKeptWhenItHasNotPassedYet() {
        val next = nextFutureOccurrence(
            date = millis(2025, 1, 15),
            hour = 9,
            minute = 45,
            repeatType = "DAILY",
            monthlyDay = 0,
            nowMillis = millis(2025, 1, 10, 10, 0)
        )

        assertUtcDate(next!!, 2025, 1, 15)
    }

    @Test
    fun unknownRepeatTypeReturnsNull() {
        val next = nextFutureOccurrence(
            date = millis(2025, 1, 1),
            hour = 9,
            minute = 45,
            repeatType = "YEARLY",
            monthlyDay = 0,
            nowMillis = millis(2025, 1, 10)
        )

        assertEquals(null, next)
    }

}
