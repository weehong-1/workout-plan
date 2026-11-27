package com.vitrack.app.data.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vitrack.app.MainActivity
import com.vitrack.app.R
import com.vitrack.app.data.food.FoodRepository
import com.vitrack.app.data.settings.ReminderKind
import com.vitrack.app.data.settings.Reminders
import com.vitrack.app.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Keeps the phone's alarms in line with the reminders switched on in Profile. */
fun interface ReminderScheduler {
    fun sync(reminders: Reminders)
}

/** The next time a reminder at [time] fires after [now]: today if still ahead, else tomorrow. */
internal fun nextOccurrence(now: LocalDateTime, time: LocalTime): LocalDateTime {
    val today = now.toLocalDate().atTime(time)
    return if (today.isAfter(now)) today else today.plusDays(1)
}

/**
 * Inexact daily alarms (a 10-minute window), so no exact-alarm permission is
 * needed; each one sets the next when it fires.
 */
class AlarmReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : ReminderScheduler {

    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    override fun sync(reminders: Reminders) {
        ReminderKind.entries.forEach { kind ->
            val intent = pendingIntent(kind)
            if (reminders.isOn(kind)) {
                val at = nextOccurrence(LocalDateTime.now(clock), kind.defaultTime).atZone(clock.zone).toInstant().toEpochMilli()
                alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MILLIS, intent)
            } else {
                alarms.cancel(intent)
            }
        }
    }

    private fun pendingIntent(kind: ReminderKind): PendingIntent = PendingIntent.getBroadcast(
        context,
        kind.ordinal,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND).putExtra(EXTRA_KIND, kind.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_REMIND = "com.vitrack.app.REMIND"
        const val EXTRA_KIND = "kind"
        private const val WINDOW_MILLIS = 10 * 60 * 1000L
    }
}

/** Posts a reminder when its alarm fires, then sets the next one. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var food: FoodRepository
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(AlarmReminderScheduler.EXTRA_KIND)
            ?.let { name -> ReminderKind.entries.firstOrNull { it.name == name } } ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val reminders = settings.reminders.first()
                if (!reminders.isOn(kind)) return@launch
                val today = LocalDate.now(clock)
                // Food: only when the day has no photo yet.
                val skip = kind == ReminderKind.Food && (food.observeCounts(today).first()[today] ?: 0) > 0
                if (!skip) ReminderNotifications.post(context, kind)
                scheduler.sync(reminders)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms don't survive a restart or a clock change: set them again. */
@AndroidEntryPoint
class ReminderRestoreReceiver : BroadcastReceiver() {
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                scheduler.sync(settings.reminders.first())
            } finally {
                pending.finish()
            }
        }
    }
}

object ReminderNotifications {
    private const val CHANNEL = "reminders"
    const val EXTRA_OPEN_TAB = "open_tab"
    const val TAB_WEIGHT = "weight"
    const val TAB_FOOD = "food"

    private class Content(val title: Int, val text: Int, val icon: Int, val tab: String)

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, kind: ReminderKind) {
        if (!canPost(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val content = when (kind) {
            ReminderKind.WeighIn -> Content(R.string.reminder_weigh_title, R.string.reminder_weigh_text, R.drawable.ic_nav_weight, TAB_WEIGHT)
            ReminderKind.Food -> Content(R.string.reminder_food_title, R.string.reminder_food_text, R.drawable.ic_nav_food, TAB_FOOD)
        }
        val open = PendingIntent.getActivity(
            context,
            kind.ordinal,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_TAB, content.tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(content.icon)
            .setContentTitle(context.getString(content.title))
            .setContentText(context.getString(content.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(kind.ordinal + 1, notification)
        } catch (e: SecurityException) {
            // Permission withdrawn between the check and the post: nothing to show.
        }
    }
}
