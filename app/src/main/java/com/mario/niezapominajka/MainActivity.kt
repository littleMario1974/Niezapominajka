
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
}

fun showTestNotification(context: Context) {
    val notification = NotificationCompat.Builder(context, "reminders")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Niezapominajka")
        .setContentText("To jest testowe przypomnienie.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    val notificationManager =
        androidx.core.app.NotificationManagerCompat.from(context)

    if (
        androidx.core.app.ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        notificationManager.notify(1001, notification)
    }
}

fun getNextReminderDate(reminder: Reminder): Long? {
    if (reminder.repeatType == "NONE") {
        return null
    }

    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = reminder.date
        set(java.util.Calendar.HOUR_OF_DAY, reminder.hour)
        set(java.util.Calendar.MINUTE, reminder.minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }

    when (reminder.repeatType) {
        "DAILY" -> {
            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }

        "WEEKLY" -> {
            calendar.add(java.util.Calendar.DAY_OF_MONTH, 7)
        }

        "MONTHLY" -> {
            val targetDay = if (reminder.monthlyDay in 1..31) {
                reminder.monthlyDay
            } else {
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            }

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

    return calendar.timeInMillis
}


fun scheduleReminder(
    context: Context,
    reminder: Reminder
) {
    val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

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
        putExtra("reminder_date", reminder.date)
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

    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = reminder.date
        set(java.util.Calendar.HOUR_OF_DAY, reminder.hour)
        set(java.util.Calendar.MINUTE, reminder.minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }

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
    private var refreshVersion by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        createNotificationChannel(this)

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
    onAddReminder: () -> Unit,
    onEditReminder: (Reminder) -> Unit,
    onDeleteReminder: (Reminder) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var reminderToDelete by remember { mutableStateOf<Reminder?>(null) }


    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {


            Row(
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

            Text(
                text = "Twoje przypomnienia",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (reminders.isEmpty()) {
                Text("Nie masz jeszcze żadnych przypomnień.")
            } else {
                reminders
                    .sortedWith(
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
                    .forEach { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            onEdit = { onEditReminder(reminder) },
                            onDelete = { reminderToDelete = reminder }
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                    }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onAddReminder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = "＋ Dodaj przypomnienie",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

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
                                reminderToDelete?.let { reminder ->
                                    onDeleteReminder(reminder)
                                }
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
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
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
                    onCheckedChange = { speakText = it }
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
                    onClick = { soundEnabled = false },
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

            if (selectedAudioName != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Wybrano: $selectedAudioName",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (!microphonePermissionGranted) {
                        microphonePermissionLauncher.launch(
                            android.Manifest.permission.RECORD_AUDIO
                        )
                    } else {
                        if (!isRecording) {
                            recordedVoiceFile = voiceRecorder.start()
                            isRecording = true
                        } else {
                            recordedVoiceFile = voiceRecorder.stop()
                            isRecording = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    if (isRecording) {
                        "⏹ Zatrzymaj nagrywanie"
                    } else {
                        "🎙 Nagraj komunikat"
                    }
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
