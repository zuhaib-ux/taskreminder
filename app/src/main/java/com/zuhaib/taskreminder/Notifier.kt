package com.zuhaib.taskreminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    private const val CHANNEL = "task_reminders_v1"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Task Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Aapke set kiye hue tasks ki yaad dihani"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(ch)
    }

    fun show(ctx: Context, r: Reminder, idx: Int) {
        ensureChannel(ctx)

        val open = PendingIntent.getActivity(
            ctx, r.id, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val done = PendingIntent.getBroadcast(
            ctx, r.id, Intent(ctx, DoneReceiver::class.java).putExtra("id", r.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val left = r.repeats - idx - 1
        val sub = if (left > 0) "Yaad dihani ${idx + 1}/${r.repeats} • agli ${r.gapMin} min baad"
                  else "Aakhri yaad dihani (${idx + 1}/${r.repeats})"

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ ${r.name}")
            .setContentText(sub)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${r.name}\n$sub"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, "✓ Ho gaya", done)
            .build()

        val nmc = NotificationManagerCompat.from(ctx)
        if (!nmc.areNotificationsEnabled()) return
        try {
            nmc.cancel(r.id)          // purani hata kar nayi dikhao taake dobara pop-up ho
            nmc.notify(r.id, n)
        } catch (_: SecurityException) { }
    }

    fun clear(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)
}
