package com.zuhaib.taskreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Time aane par notification dikhata hai */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        val idx = intent.getIntExtra("idx", 0)
        val r = Store.get(ctx, id) ?: return
        if (r.done) return
        Notifier.show(ctx, r, idx)
        if (idx >= r.repeats - 1) Store.markDone(ctx, id)
    }
}

/** Notification ka "Ho gaya" button — baqi reminders band */
class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        Store.markDone(ctx, id)
        Scheduler.cancel(ctx, id)
        Notifier.clear(ctx, id)
    }
}

/** Phone restart ke baad reminders dobara set karta hai */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.rescheduleAll(ctx)
    }
}
