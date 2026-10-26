package com.zuhaib.taskreminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object Scheduler {
    const val MAX_REPEATS = 5

    private fun alarmIntent(ctx: Context, id: Int, idx: Int): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .putExtra("id", id)
            .putExtra("idx", idx)
        return PendingIntent.getBroadcast(
            ctx, id * 10 + idx, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(ctx: Context, r: Reminder) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val showApp = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        for (idx in 0 until r.repeats) {
            val t = r.timeMillis + idx * r.gapMin * 60_000L
            if (t <= now) continue
            val pi = alarmIntent(ctx, r.id, idx)
            val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (canExact) {
                // AlarmClock sab se reliable hai — Samsung battery saver bhi isay nahi rokta
                am.setAlarmClock(AlarmManager.AlarmClockInfo(t, showApp), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            }
        }
    }

    fun cancel(ctx: Context, id: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (idx in 0 until MAX_REPEATS) am.cancel(alarmIntent(ctx, id, idx))
    }

    fun rescheduleAll(ctx: Context) {
        Store.all(ctx).filter { !it.done }.forEach { schedule(ctx, it) }
    }
}
