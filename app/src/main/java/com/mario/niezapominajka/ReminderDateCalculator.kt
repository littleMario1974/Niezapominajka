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

/**
 * Zwraca najbliższe wystąpienie cyklicznego przypomnienia ściśle po nowMillis.
 *
 * date wskazuje datę bazową przypomnienia, a hour/minute jego lokalną godzinę.
 * Funkcja pomija dowolną liczbę terminów, które minęły podczas wyłączenia telefonu.
 */
internal fun nextFutureOccurrence(
    date: Long,
    hour: Int,
    minute: Int,
    repeatType: String,
    monthlyDay: Int,
    nowMillis: Long
): Long? {
    if (date <= 0L || hour !in 0..23 || minute !in 0..59) return null
    if (repeatType !in setOf("DAILY", "WEEKLY", "MONTHLY")) return null

    val calendar = Calendar.getInstance().apply {
        timeInMillis = date
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val targetDay = monthlyDay.takeIf { it in 1..31 }
        ?: calendar.get(Calendar.DAY_OF_MONTH)

    while (calendar.timeInMillis <= nowMillis) {
        when (repeatType) {
            "DAILY" -> calendar.add(Calendar.DAY_OF_MONTH, 1)
            "WEEKLY" -> calendar.add(Calendar.DAY_OF_MONTH, 7)
            "MONTHLY" -> advanceMonthlyOccurrence(calendar, targetDay)
        }
    }

    return calendar.timeInMillis
}
