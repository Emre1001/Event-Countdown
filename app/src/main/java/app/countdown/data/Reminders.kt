package app.countdown.data

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
import app.countdown.MainActivity
import app.countdown.R
import app.countdown.model.EventMode

/** Benachrichtigung, wenn ein Countdown abgelaufen ist (bei Wiederholung jedes Mal). */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val EXTRA_ID = "event_id"

    private fun pending(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        context, id.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction("app.countdown.REMIND.$id").putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(context: Context) {
        EventRepository.init(context)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        EventRepository.state.value.forEach { e ->
            val pi = pending(context, e.id)
            val t = e.effectiveTarget(now)
            if (!e.reminder || e.mode != EventMode.COUNTDOWN || t <= now) {
                alarm.cancel(pi)
                return@forEach
            }
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
            if (exact) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        }
    }

    fun cancel(context: Context, id: String) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context, id))
    }

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    internal fun notify(context: Context, id: String) {
        EventRepository.init(context)
        AppSettings.init(context)
        val ctx = AppSettings.wrap(context)
        val event = EventRepository.get(id) ?: return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, id.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_EVENT_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_countdown)
            .setContentTitle(event.title.ifBlank { ctx.getString(R.string.untitled) })
            .setContentText(ctx.getString(R.string.reminder_now))
            .setColor(event.style.accent.first)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id.hashCode(), n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("event_id")?.let { Reminders.notify(context, it) }
        Reminders.schedule(context) // nächster Termin bei Wiederholung
    }
}
