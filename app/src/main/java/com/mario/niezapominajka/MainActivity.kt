package com.mario.niezapominajka

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mario.niezapominajka.ui.theme.NiezapominajkaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import android.app.AlarmManager
import androidx.lifecycle.lifecycleScope

private val Context.dataStore by preferencesDataStore(name = "reminders")
private val REMINDERS_KEY = stringPreferencesKey("reminders")

data class Reminder(
    val id: Long,
    val text: String,
    val date: Long,
    val hour: Int,
    val minute: Int,
    val soundEnabled: Boolean,
    val voiceFilePath: String? = null,
    val audioUri: String? = null,
    val repeatType: String = "NONE",
    val monthlyDay: Int = 0,
    val speakText: Boolean = false
)


fun createNotificationChannel(context: Context) {
    val channel = NotificationChannel(
        "reminders",
        "Przypomnienia",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Powiadomienia z aplikacji Niezapominajka"
    }

    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    notificationManager.createNotificationChannel(channel)
}


fun canUseFullScreenIntent(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 34) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

        notificationManager.canUseFullScreenIntent()
    } else {
        true
    }
}

fun canScheduleExactAlarms(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.canScheduleExactAlarms()
    } else {
        true
    }
}

fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= 34) {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            android.net.Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }
}



fun getNextReminderDate(reminder: Reminder): Long? {
    if (reminder.repeatType == "NONE") {
        return null
    }

    if (reminder.repeatType !in setOf("DAILY", "WEEKLY", "MONTHLY")) {
        return null
    }

    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = reminder.date
        set(java.util.Calendar.HOUR_OF_DAY, reminder.hour)
        set(java.util.Calendar.MINUTE, reminder.minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }

    val targetDay = if (reminder.monthlyDay in 1..31) {
        reminder.monthlyDay
    } else {
        calendar.get(java.util.Calendar.DAY_OF_MONTH)
    }

    fun advance() {
        when (reminder.repeatType) {
            "DAILY" -> {
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }

            "WEEKLY" -> {
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 7)
            }

            "MONTHLY" -> {
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.add(java.util.Calendar.MONTH, 1)

                val maxDay =
                    calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)

                calendar.set(
                    java.util.Calendar.DAY_OF_MONTH,
                    minOf(targetDay, maxDay)
                )
            }
        }
    }

    advance()

    var attempts = 0
    val now = System.currentTimeMillis()

    while (calendar.timeInMillis <= now && attempts < 500) {
        advance()
        attempts++
    }

    return if (calendar.timeInMillis > now) {
        calendar.timeInMillis
    } else {
        null
    }
}

fun scheduleReminder(
context: Context,
reminder: Reminder
) {
val alarmManager =
context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

val calendar = java.util.Calendar.getInstance().apply {
    timeInMillis = reminder.date
    set(java.util.Calendar.HOUR_OF_DAY, reminder.hour)
    set(java.util.Calendar.MINUTE, reminder.minute)
    set(java.util.Calendar.SECOND, 0)
    set(java.util.Calendar.MILLISECOND, 0)
}

if (reminder.repeatType != "NONE") {
    val targetDay = if (reminder.monthlyDay in 1..31) {
        reminder.monthlyDay
    } else {
        calendar.get(java.util.Calendar.DAY_OF_MONTH)
    }

    var attempts = 0

    while (calendar.timeInMillis <= System.currentTimeMillis() && attempts < 500) {
        when (reminder.repeatType) {
            "DAILY" -> calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)

            "WEEKLY" -> calendar.add(java.util.Calendar.DAY_OF_MONTH, 7)

            "MONTHLY" -> {
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.add(java.util.Calendar.MONTH, 1)

                val maxDay = calendar.getActualMaximum(
                    java.util.Calendar.DAY_OF_MONTH
                )

                calendar.set(
                    java.util.Calendar.DAY_OF_MONTH,
                    minOf(targetDay, maxDay)
                )
            }

            else -> break
        }

        attempts++
    }

    if (calendar.timeInMillis <= System.currentTimeMillis()) {
        android.util.Log.e(
            "Niezapominajka",
            "Nie udało się wyznaczyć przyszłego terminu alarmu."
        )
        return
    }
}

val intent = android.content.Intent(
    context,
    ReminderReceiver::class.java
).apply {
    putExtra("reminder_text", reminder.text)
    putExtra("sound_enabled", reminder.soundEnabled)
    putExtra("speak_text", reminder.speakText)
    putExtra("voice_file_path", reminder.voiceFilePath)
    putExtra("audio_uri", reminder.audioUri)
    putExtra("reminder_id", reminder.id)
    putExtra("repeat_type", reminder.repeatType)
    putExtra("monthly_day", reminder.monthlyDay)
    putExtra("reminder_date", calendar.timeInMillis)
    putExtra("reminder_hour", reminder.hour)
    putExtra("reminder_minute", reminder.minute)
}

val pendingIntent = android.app.PendingIntent.getBroadcast(
    context,
    reminder.id.hashCode(),
    intent,
    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
        android.app.PendingIntent.FLAG_IMMUTABLE
)


if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {  
    if (alarmManager.canScheduleExactAlarms()) {  
        alarmManager.setExactAndAllowWhileIdle(  
            android.app.AlarmManager.RTC_WAKEUP,  
            calendar.timeInMillis,  
            pendingIntent  
        )  
    } else {  
        val settingsIntent = android.content.Intent(  
            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM  
        ).apply {  
            data = android.net.Uri.parse("package:${context.packageName}")  
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)  
        }  

        context.startActivity(settingsIntent)  
    }  
} else {  
    alarmManager.setExactAndAllowWhileIdle(  
        android.app.AlarmManager.RTC_WAKEUP,  
        calendar.timeInMillis,  
        pendingIntent  
    )  
}

}


fun cancelReminder(
    context: Context,
    reminder: Reminder
) {
    val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

    val intent = android.content.Intent(
        context,
        ReminderReceiver::class.java
    )

    val pendingIntent = android.app.PendingIntent.getBroadcast(
        context,
        reminder.id.hashCode(),
        intent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or
            android.app.PendingIntent.FLAG_IMMUTABLE
    )

    alarmManager.cancel(pendingIntent)
    pendingIntent.cancel()
}

class MainActivity : ComponentActivity() {
private var lastExactAlarmPermission: Boolean? = null
    private var refreshVersion by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        createNotificationChannel(this)

        if (Build.VERSION.SDK_INT >= 34) {
            if (!canUseFullScreenIntent(this)) {
                android.util.Log.w(
                    "Niezapominajka",
                    "Brak dostępu do powiadomień pełnoekranowych"
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

        setContent {
            NiezapominajkaTheme {
                NiezapominajkaApp(refreshVersion = refreshVersion)
            }
        }
    }

    override fun onResume() {
    super.onResume()

    refreshVersion++

    val exactAlarmPermission = canScheduleExactAlarms(this)
    val permissionWasGranted =
        lastExactAlarmPermission != true && exactAlarmPermission

    lastExactAlarmPermission = exactAlarmPermission

    if (permissionWasGranted) {
        lifecycleScope.launch {
            val reminders = loadReminders(this@MainActivity)

            reminders.forEach { reminder ->
                scheduleReminder(this@MainActivity, reminder)
            }
        }
    }
}
}

@Composable
fun NiezapominajkaApp(
    refreshVersion: Int = 0
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var showAddScreen by remember { mutableStateOf(false) }
    var reminderToEdit by remember { mutableStateOf<Reminder?>(null) }
    var reminders by remember { mutableStateOf(listOf<Reminder>()) }

    LaunchedEffect(refreshVersion) {
        reminders = loadReminders(context)
    }

    if (showAddScreen || reminderToEdit != null) {
        AddReminderScreen(
            reminderToEdit = reminderToEdit,
            onBack = {
                showAddScreen = false
                reminderToEdit = null
            },
            onSave = { reminder ->
                val oldReminder = reminderToEdit

                val updatedReminders = if (oldReminder != null) {
                    cancelReminder(context, oldReminder)

                    reminders.map {
                        if (it.id == oldReminder.id) reminder else it
                    }
                } else {
                    reminders + reminder
                }

                reminders = updatedReminders

                scope.launch {
                    saveReminders(context, updatedReminders)
                }

                scheduleReminder(context, reminder)

                showAddScreen = false
                reminderToEdit = null
            }
        )
    } else {
        HomeScreen(
            reminders = reminders,
            fullScreenIntentAllowed = canUseFullScreenIntent(context),
            exactAlarmsAllowed = canScheduleExactAlarms(context),
            onAddReminder = {
                reminderToEdit = null
                showAddScreen = true
            },
            onEditReminder = { reminder ->
                reminderToEdit = reminder
            },
            onDeleteReminder = { reminder ->
                cancelReminder(context, reminder)

                val updatedReminders =
                    reminders.filter { it.id != reminder.id }

                reminders = updatedReminders

                scope.launch {
                    saveReminders(context, updatedReminders)
                }
            }
        )
    }
}


@Composable
fun HomeScreen(
    reminders: List<Reminder>,
    fullScreenIntentAllowed: Boolean,
    exactAlarmsAllowed: Boolean,
    onAddReminder: () -> Unit,
    onEditReminder: (Reminder) -> Unit,
    onDeleteReminder: (Reminder) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

Text(
    text = "Diagnostyka dokładnych alarmów: ${
        canScheduleExactAlarms(context)
    }",
    modifier = Modifier.padding(8.dp)
)
    var reminderToDelete by remember { mutableStateOf<Reminder?>(null) }
    var searchText by remember { mutableStateOf("") }

    val sortedReminders = remember(reminders) {
        reminders.sortedWith(
            compareBy<Reminder> {
                val calendar = java.util.Calendar.getInstance().apply {
                    timeInMillis = it.date
                    set(java.util.Calendar.HOUR_OF_DAY, it.hour)
                    set(java.util.Calendar.MINUTE, it.minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                calendar.timeInMillis <= System.currentTimeMillis()
            }.thenBy {
                val calendar = java.util.Calendar.getInstance().apply {
                    timeInMillis = it.date
                    set(java.util.Calendar.HOUR_OF_DAY, it.hour)
                    set(java.util.Calendar.MINUTE, it.minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                calendar.timeInMillis
            }
        )
    }

    val filteredReminders = remember(sortedReminders, searchText) {
        sortedReminders.filter {
            it.text.contains(searchText.trim(), ignoreCase = true)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            Box(modifier = Modifier.padding(end = 12.dp)) {
                FloatingActionButton(onClick = onAddReminder) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(
                        id = R.drawable.niezapominajka_icon
                    ),
                    contentDescription = "Ikona Niezapominajki",
                    modifier = Modifier.size(60.dp)
                )

                Column {
                    Text(
                        text = "Niezapominajka",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Nie zapomnij o tym, co ważne.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            if (!fullScreenIntentAllowed) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Wymagane uprawnienie",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Aby alarm mógł otwierać ekran na pełnym ekranie, włącz odpowiedni dostęp w ustawieniach telefonu."
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                openFullScreenIntentSettings(context)
                            }
                        ) {
                            Text("Włącz alarmy pełnoekranowe")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (!exactAlarmsAllowed) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Wymagane uprawnienie",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aby przypomnienia uruchamiały się o zaplanowanej godzinie, włącz dostęp do dokładnych alarmów w ustawieniach telefonu."
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val intent = android.content.Intent(
                                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            }
                        ) {
                            Text("Włącz dokładne alarmy")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }


            Text(
                text = "Twoje przypomnienia",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Szukaj przypomnienia") },
                singleLine = true,
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        TextButton(
                            onClick = { searchText = "" }
                        ) {
                            Text("Wyczyść")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (filteredReminders.isEmpty()) {
                    Text(
                        text = if (searchText.isBlank()) {
                            "Nie masz jeszcze żadnych przypomnień."
                        } else {
                            "Brak przypomnień pasujących do wyszukiwania."
                        }
                    )
                } else {
                    val upcomingReminders = filteredReminders.filter { reminder ->
                        val calendar = java.util.Calendar.getInstance().apply {
                            timeInMillis = reminder.date
                            set(java.util.Calendar.HOUR_OF_DAY, reminder.hour)
                            set(java.util.Calendar.MINUTE, reminder.minute)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        calendar.timeInMillis > System.currentTimeMillis()
                    }

                    val pastReminders = filteredReminders.filter { reminder ->
                        val calendar = java.util.Calendar.getInstance().apply {
                            timeInMillis = reminder.date
                            set(java.util.Calendar.HOUR_OF_DAY, reminder.hour)
                            set(java.util.Calendar.MINUTE, reminder.minute)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        calendar.timeInMillis <= System.currentTimeMillis()
                    }

                    upcomingReminders.forEach { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            onEdit = { onEditReminder(reminder) },
                            onDelete = { reminderToDelete = reminder }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (pastReminders.isNotEmpty()) {
                        Text(
                            text = "Minione przypomnienia",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                top = if (upcomingReminders.isNotEmpty()) 12.dp else 0.dp,
                                bottom = 12.dp
                            )
                        )

                        pastReminders.forEach { reminder ->
                            ReminderCard(
                                reminder = reminder,
                                onEdit = { onEditReminder(reminder) },
                                onDelete = { reminderToDelete = reminder }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        if (reminderToDelete != null) {
            AlertDialog(
                onDismissRequest = {
                    reminderToDelete = null
                },
                title = { Text("Usuń przypomnienie") },
                text = { Text("Czy na pewno usunąć?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            reminderToDelete?.let(onDeleteReminder)
                            reminderToDelete = null
                        }
                    ) {
                        Text("Usuń")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { reminderToDelete = null }
                    ) {
                        Text("Anuluj")
                    }
                }
            )
        }
    }
}
@Composable
fun ReminderCard(
    reminder: Reminder,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val reminderDateTime = java.util.Calendar.getInstance().apply {
        timeInMillis = reminder.date
        set(java.util.Calendar.HOUR_OF_DAY, reminder.hour)
        set(java.util.Calendar.MINUTE, reminder.minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }

    val isPast =
        reminderDateTime.timeInMillis <= System.currentTimeMillis()

    val dateFormat = SimpleDateFormat(
        "dd.MM.yyyy",
        Locale.getDefault()
    )

    val time = String.format(
        Locale.getDefault(),
        "%02d:%02d",
        reminder.hour,
        reminder.minute
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isPast) {
                    Modifier.alpha(0.6f)
                } else {
                    Modifier
                }
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isPast) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPast) 0.dp else 1.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = reminder.text,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(6.dp))


            Text(
                text = time,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = dateFormat.format(Date(reminder.date)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (reminder.repeatType != "NONE") {
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = when (reminder.repeatType) {
                        "DAILY" -> "🔄 Powtarzaj codziennie"
                        "WEEKLY" -> "🔄 Powtarzaj co tydzień"
                        "MONTHLY" -> "🔄 Powtarzaj co miesiąc"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    reminder.speakText -> "🗣️ Odczyt treści na głos"
                    reminder.soundEnabled -> "🔊 Dźwięk i wibracja"
                    else -> "📳 Tylko wibracja"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isPast) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Termin minął",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("✏️ Edytuj")
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("🗑 Usuń")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderScreen(
    reminderToEdit: Reminder?,
    onBack: () -> Unit,
    onSave: (Reminder) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedAudioUri by remember(reminderToEdit?.id) {
        mutableStateOf(
            reminderToEdit?.audioUri?.let {
                android.net.Uri.parse(it)
            }
        )
    }

    var selectedAudioName by remember(reminderToEdit?.id) {
        mutableStateOf(
            reminderToEdit?.audioUri?.let {
                android.net.Uri.parse(it).lastPathSegment
            }
        )
    }

    val audioPickerLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Niektóre źródła plików nie pozwalają na trwałe uprawnienie.
                }

                selectedAudioUri = uri
                selectedAudioName = uri.lastPathSegment
            }
        }

    var reminderText by remember(reminderToEdit?.id) {
        mutableStateOf(reminderToEdit?.text ?: "")
    }

    var soundEnabled by remember(reminderToEdit?.id) {
        mutableStateOf(reminderToEdit?.soundEnabled ?: true)
    }
    var speakText by remember(reminderToEdit?.id) {
        mutableStateOf(reminderToEdit?.speakText ?: false)
    }
    var isSpeakingPreview by remember {
        mutableStateOf(false)
    }

    val textToSpeech = remember(context) {
        android.speech.tts.TextToSpeech(context, null)
    }

    var repeatType by remember(reminderToEdit?.id) {
        mutableStateOf(reminderToEdit?.repeatType ?: "NONE")
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var selectedDate by remember(reminderToEdit?.id) {
        mutableStateOf(reminderToEdit?.date)
    }

    var selectedHour by remember(reminderToEdit?.id) {
        mutableStateOf(reminderToEdit?.hour ?: 12)
    }

    var selectedMinute by remember(reminderToEdit?.id) {
        mutableStateOf(reminderToEdit?.minute ?: 0)
    }

    val dateFormat = SimpleDateFormat(
        "dd.MM.yyyy",
        Locale.getDefault()
    )

    var showPastDateError by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }

    var microphonePermissionGranted by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    var recordedVoiceFile by remember(reminderToEdit?.id) {
        mutableStateOf(
            reminderToEdit?.voiceFilePath?.let {
                java.io.File(it).takeIf { file -> file.exists() }
            }
        )
    }

    val voiceRecorder = remember { VoiceRecorder(context) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var defaultAlarmPlayer by remember {
        mutableStateOf<MediaPlayer?>(null)
    }
    val microphonePermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
            microphonePermissionGranted = granted
        }

    val hasChanges = if (reminderToEdit == null) {
        reminderText.isNotBlank() ||
            selectedDate != null ||
            selectedHour != 12 ||
            selectedMinute != 0 ||
            !soundEnabled
    } else {
        reminderText != reminderToEdit.text ||
            selectedDate != reminderToEdit.date ||
            selectedHour != reminderToEdit.hour ||
            selectedMinute != reminderToEdit.minute ||
            soundEnabled != reminderToEdit.soundEnabled ||
            repeatType != reminderToEdit.repeatType
    }

    fun saveCurrentReminder() {
        val dateValue = selectedDate ?: return

        val selectedDateTime = java.util.Calendar.getInstance().apply {
            timeInMillis = dateValue
            set(java.util.Calendar.HOUR_OF_DAY, selectedHour)
            set(java.util.Calendar.MINUTE, selectedMinute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        val now = java.util.Calendar.getInstance()

        if (selectedDateTime.timeInMillis > now.timeInMillis) {
            showPastDateError = false

            val selectedCalendar = java.util.Calendar.getInstance().apply {
                timeInMillis = dateValue
            }

            val selectedDay =
                selectedCalendar.get(java.util.Calendar.DAY_OF_MONTH)

            val monthlyDay = if (repeatType == "MONTHLY") {
                if (
                    reminderToEdit != null &&
                    dateValue == reminderToEdit.date &&
                    reminderToEdit.monthlyDay in 1..31
                ) {
                    reminderToEdit.monthlyDay
                } else {
                    selectedDay
                }
            } else {
                0
            }

            onSave(
                Reminder(
                    id = reminderToEdit?.id ?: System.currentTimeMillis(),
                    text = reminderText,
                    date = dateValue,
                    hour = selectedHour,
                    minute = selectedMinute,
                    soundEnabled = soundEnabled,
                    voiceFilePath = recordedVoiceFile?.absolutePath,
                    audioUri = selectedAudioUri?.toString(),
                    repeatType = repeatType,
                    monthlyDay = monthlyDay,
                    speakText = speakText
                )
            )
        } else {
            showPastDateError = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            TextButton(
                onClick = {
                    if (hasChanges) {
                        showSaveDialog = true
                    } else {
                        onBack()
                    }
                }
            ) {
                Text("← Wróć")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (reminderToEdit == null) {
                    "Nowe przypomnienie"
                } else {
                    "Edytuj przypomnienie"
                },
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = reminderText,
                onValueChange = { reminderText = it },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                label = { Text("Co mam przypomnieć?") },
                placeholder = { Text("np. Wziąć lek") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { showDatePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = if (selectedDate == null) {
                        "Wybierz datę"
                    } else {
                        "Data: ${dateFormat.format(Date(selectedDate!!))}"
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { showTimePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = String.format(
                        Locale.getDefault(),
                        "Godzina: %02d:%02d",
                        selectedHour,
                        selectedMinute
                    )
                )
            }

            Text(
                text = "Powtarzanie",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            var repeatMenuExpanded by remember {
                mutableStateOf(false)
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { repeatMenuExpanded = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        when (repeatType) {
                            "DAILY" -> "Codziennie"
                            "WEEKLY" -> "Co tydzień"
                            "MONTHLY" -> "Co miesiąc"
                            else -> "Nie powtarzaj"
                        }
                    )
                }

                DropdownMenu(
                    expanded = repeatMenuExpanded,
                    onDismissRequest = { repeatMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Nie powtarzaj") },
                        onClick = {
                            repeatType = "NONE"
                            repeatMenuExpanded = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Codziennie") },
                        onClick = {
                            repeatType = "DAILY"
                            repeatMenuExpanded = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Co tydzień") },
                        onClick = {
                            repeatType = "WEEKLY"
                            repeatMenuExpanded = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Co miesiąc") },
                        onClick = {
                            repeatType = "MONTHLY"
                            repeatMenuExpanded = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Rodzaj alarmu",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
    checked = speakText,
    onCheckedChange = { checked ->
        speakText = checked

        if (checked) {
            soundEnabled = true
        }
    }
)

                Text("Odczytaj treść przypomnienia na głos")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Button(
                    onClick = { soundEnabled = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (soundEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text("🔊 Dźwięk + wibracja")
                }


                Button(
                    onClick = {
                        soundEnabled = false
                        speakText = false
                        isSpeakingPreview = false
                        textToSpeech.stop()
                        mediaPlayer?.let {
                            try {
                                it.stop()
                            } catch (_: Exception) {
                            }
                            it.release()
                        }
                        mediaPlayer = null

                        defaultAlarmPlayer?.let {
                            try {
                                it.stop()
                            } catch (_: Exception) {
                            }
                            it.release()
                        }
                        defaultAlarmPlayer = null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!soundEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text("📳 Tylko wibracja")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (soundEnabled) {
                Button(
                    onClick = {
                        audioPickerLauncher.launch(arrayOf("audio/*"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("📁 Wybierz plik audio")
                }
            }

            if (soundEnabled || speakText) {
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {


                        if (speakText) {
                            if (textToSpeech.isSpeaking || isSpeakingPreview) {
                                textToSpeech.stop()
                                isSpeakingPreview = false
                            } else {
                                val result = textToSpeech.setLanguage(Locale.getDefault())

                                if (
                                    result != android.speech.tts.TextToSpeech.LANG_MISSING_DATA &&
                                    result != android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
                                ) {
                                    textToSpeech.setOnUtteranceProgressListener(
                                        object : android.speech.tts.UtteranceProgressListener() {
                                            override fun onStart(utteranceId: String?) {
                                                isSpeakingPreview = true
                                            }

                                            override fun onDone(utteranceId: String?) {
                                                isSpeakingPreview = false
                                            }

                                            override fun onError(utteranceId: String?) {
                                                isSpeakingPreview = false
                                            }
                                        }
                                    )

                                    val resultSpeak = textToSpeech.speak(
                                        reminderText,
                                        android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "reminder_preview"
                                    )

                                    if (resultSpeak == android.speech.tts.TextToSpeech.ERROR) {
                                        isSpeakingPreview = false
                                    }
                                }
                            }
                        } else if (selectedAudioUri != null) {


                            if (mediaPlayer?.isPlaying == true) {
                                mediaPlayer?.stop()
                                mediaPlayer?.release()
                                mediaPlayer = null
                            } else {
                                try {
                                    defaultAlarmPlayer?.release()
                                    defaultAlarmPlayer = null
                                    mediaPlayer?.release()

                                    mediaPlayer = MediaPlayer().apply {
                                        setDataSource(context, selectedAudioUri!!)
                                        setOnCompletionListener {
                                            release()
                                            mediaPlayer = null
                                        }
                                        prepare()
                                        start()
                                    }
                                } catch (_: Exception) {
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                }
                            }
                        } else {
                            if (defaultAlarmPlayer != null) {
                                try {
                                    defaultAlarmPlayer?.stop()
                                } catch (_: Exception) {
                                }
                                defaultAlarmPlayer?.release()
                                defaultAlarmPlayer = null
                            } else {
                                try {
                                    mediaPlayer?.release()
                                    mediaPlayer = null

                                    val alarmUri =
                                        android.media.RingtoneManager.getDefaultUri(
                                            android.media.RingtoneManager.TYPE_ALARM
                                        ) ?: android.media.RingtoneManager.getDefaultUri(
                                            android.media.RingtoneManager.TYPE_NOTIFICATION
                                        )

                                    if (alarmUri != null) {
                                        defaultAlarmPlayer = MediaPlayer().apply {
                                            setDataSource(context, alarmUri)
                                            setOnCompletionListener {
                                                release()
                                                defaultAlarmPlayer = null
                                            }
                                            prepare()
                                            start()
                                        }
                                    }
                                } catch (_: Exception) {
                                    defaultAlarmPlayer?.release()
                                    defaultAlarmPlayer = null
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        when {
                            speakText && isSpeakingPreview ->
                                "⏹ Zatrzymaj odsłuch"

                            speakText ->
                                "▶ Odsłuchaj odczyt"

                            mediaPlayer?.isPlaying == true ||
                                    defaultAlarmPlayer != null ->
                                "⏹ Zatrzymaj odsłuch"

                            selectedAudioUri != null ->
                                "▶ Odsłuchaj plik audio"

                            else ->
                                "▶ Odsłuchaj zwykły alarm"
                        }
                    )
                }
            }


            if (selectedAudioName != null &&
                selectedAudioUri != null &&
                soundEnabled &&
                !speakText
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Wybrano: $selectedAudioName",
                    style = MaterialTheme.typography.bodyMedium
                )
            }


            if (recordedVoiceFile != null && !isRecording) {
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        mediaPlayer?.release()

                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(recordedVoiceFile!!.absolutePath)
                            prepare()

                            setOnCompletionListener {
                                release()
                                mediaPlayer = null
                            }

                            start()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("▶ Odtwórz nagranie")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { saveCurrentReminder() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
                enabled = reminderText.isNotBlank() && selectedDate != null
            ) {
                Text("Zapisz przypomnienie")
            }

            if (showPastDateError) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Wybrana data i godzina już minęły. Wybierz przyszły termin.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDate = datePickerState.selectedDateMillis
                        showPastDateError = false
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false }
                ) {
                    Text("Anuluj")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedHour = timePickerState.hour
                        selectedMinute = timePickerState.minute
                        showPastDateError = false
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePicker = false }
                ) {
                    Text("Anuluj")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Text(
                    if (reminderToEdit == null) {
                        "Zapisać przypomnienie?"
                    } else {
                        "Zapisać zmiany?"
                    }
                )
            },
            text = {
                Text(
                    if (reminderToEdit == null) {
                        "Przypomnienie nie zostało jeszcze zapisane."
                    } else {
                        "Wprowadzono zmiany w przypomnieniu."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveDialog = false
                        saveCurrentReminder()
                    }
                ) {
                    Text("Zapisz")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSaveDialog = false
                        onBack()
                    }
                ) {
                    Text("Nie zapisuj")
                }
            }
        )
    }



    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null

            defaultAlarmPlayer?.release()
            defaultAlarmPlayer = null

            textToSpeech.stop()
            textToSpeech.shutdown()

            if (isRecording) {
                voiceRecorder.stop()
            }
        }
    }


}

suspend fun saveReminders(
    context: Context,
    reminders: List<Reminder>
) {
    val jsonArray = JSONArray()

    reminders.forEach { reminder ->
        val jsonObject = JSONObject()

        jsonObject.put("id", reminder.id)
        jsonObject.put("text", reminder.text)
        jsonObject.put("date", reminder.date)
        jsonObject.put("hour", reminder.hour)
        jsonObject.put("minute", reminder.minute)
        jsonObject.put("soundEnabled", reminder.soundEnabled)
        jsonObject.put("voiceFilePath", reminder.voiceFilePath)
        jsonObject.put("audioUri", reminder.audioUri)
        jsonObject.put("repeatType", reminder.repeatType)
        jsonObject.put("monthlyDay", reminder.monthlyDay)
        jsonObject.put("speakText", reminder.speakText)

        jsonArray.put(jsonObject)
    }

    context.dataStore.edit { preferences ->
        preferences[REMINDERS_KEY] = jsonArray.toString()
    }
}

suspend fun loadReminders(
    context: Context
): List<Reminder> {
    val preferences = context.dataStore.data.first()
    val json = preferences[REMINDERS_KEY] ?: return emptyList()
    val jsonArray = JSONArray(json)
    val reminders = mutableListOf<Reminder>()

    for (i in 0 until jsonArray.length()) {
        val jsonObject = jsonArray.getJSONObject(i)

        val id = if (jsonObject.has("id")) {
            jsonObject.getLong("id")
        } else {
            System.currentTimeMillis() + i
        }

        val date = jsonObject.getLong("date")
        val repeatType = jsonObject.optString("repeatType", "NONE")

        val storedMonthlyDay = jsonObject.optInt("monthlyDay", 0)

        val monthlyDay = if (
            repeatType == "MONTHLY" &&
            storedMonthlyDay !in 1..31
        ) {
            java.util.Calendar.getInstance().apply {
                timeInMillis = date
            }.get(java.util.Calendar.DAY_OF_MONTH)
        } else {
            storedMonthlyDay
        }

        reminders.add(
            Reminder(
                id = id,
                text = jsonObject.getString("text"),
                date = date,
                hour = jsonObject.getInt("hour"),
                minute = jsonObject.getInt("minute"),
                soundEnabled = jsonObject.optBoolean("soundEnabled", true),

                voiceFilePath = jsonObject.optString("voiceFilePath")
                    .takeUnless { it.isEmpty() || it == "null" },
                audioUri = jsonObject.optString("audioUri")
                    .takeUnless { it.isEmpty() || it == "null" },
                repeatType = repeatType,
                monthlyDay = monthlyDay,
                speakText = jsonObject.optBoolean("speakText", false)
            )
        )
    }

    return reminders
}

@Preview(showBackground = true)
@Composable
fun NiezapominajkaPreview() {
    NiezapominajkaTheme {
        NiezapominajkaApp()
    }
}

