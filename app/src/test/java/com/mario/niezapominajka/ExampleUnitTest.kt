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
}
