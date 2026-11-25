package com.mario.niezapominajka

import java.util.Calendar

/**
 * Przesuwa termin miesięczny, zachowując pierwotny dzień docelowy.
 *
 * Przykład dla monthlyDay=31: 31 stycznia -> 28/29 lutego -> 31 marca.
 * Calendar jest modyfikowany w miejscu; godzina i minuta pozostają bez zmian.
 */
internal fun advanceMonthlyOccurrence(
    calendar: Calendar,
    monthlyDay: Int
) {
    val targetDay = monthlyDay.takeIf { it in 1..31 }
        ?: calendar.get(Calendar.DAY_OF_MONTH)

    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.add(Calendar.MONTH, 1)

    val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    calendar.set(Calendar.DAY_OF_MONTH, minOf(targetDay, lastDay))
}
