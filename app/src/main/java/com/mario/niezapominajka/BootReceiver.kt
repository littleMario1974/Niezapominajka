package com.mario.niezapominajka

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Odtwarza zaplanowane przypomnienia, ponieważ Android usuwa alarmy
 * AlarmManager po wyłączeniu i ponownym uruchomieniu urządzenia.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        Log.i(TAG, "Odtwarzanie harmonogramu po zdarzeniu: $action")

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminders = loadReminders(appContext)
                val now = System.currentTimeMillis()
                var scheduled = 0
                var datesUpdated = false

                val normalizedReminders = reminders.map { reminder ->
                    if (reminder.repeatType == "NONE") {
                        reminder
                    } else {
                        val nextDate = nextFutureOccurrence(
                            date = reminder.date,
                            hour = reminder.hour,
                            minute = reminder.minute,
                            repeatType = reminder.repeatType,
                            monthlyDay = reminder.monthlyDay,
                            nowMillis = now
                        )

                        if (nextDate != null && nextDate != reminder.date) {
                            datesUpdated = true
                            reminder.copy(date = nextDate)
                        } else {
                            reminder
                        }
                    }
                }

                // Po dłuższym wyłączeniu telefonu zapisujemy od razu najbliższy
                // przyszły termin, aby kolejne restarty nie liczyły od starej daty.
                if (datesUpdated) {
                    saveReminders(appContext, normalizedReminders)
                }

                normalizedReminders.forEach { reminder ->
                    try {
                        scheduleReminder(
                            context = appContext,
                            reminder = reminder,
                            requestPermissionIfMissing = false
                        )
                        scheduled++
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Nie udało się odtworzyć przypomnienia ID=${reminder.id}.",
                            e
                        )
                    }
                }

                Log.i(
                    TAG,
                    "Odtworzono harmonogram: $scheduled z ${reminders.size} przypomnień; " +
                        "zaktualizowano daty: $datesUpdated."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Nie udało się odtworzyć przypomnień po restarcie.", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "Niezapominajka"
    }
}
