package com.zuhaib.taskreminder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Reminder(
    val id: Int,
    val name: String,
    val timeMillis: Long,
    val repeats: Int,
    val gapMin: Int,
    var done: Boolean = false
) {
    fun lastFireMillis(): Long = timeMillis + (repeats - 1) * gapMin * 60_000L
}

object Store {
    private const val PREFS = "task_reminder"
    private const val KEY = "list"

    fun all(ctx: Context): MutableList<Reminder> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<Reminder>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Reminder(
                    o.getInt("id"), o.getString("name"), o.getLong("time"),
                    o.getInt("repeats"), o.getInt("gap"), o.optBoolean("done", false)
                )
            )
        }
        return list
    }

    private fun saveAll(ctx: Context, list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("name", it.name); put("time", it.timeMillis)
                put("repeats", it.repeats); put("gap", it.gapMin); put("done", it.done)
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun add(ctx: Context, name: String, time: Long, repeats: Int, gap: Int): Reminder {
        val list = all(ctx)
        // Ek din se purane complete reminders saaf kar do
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60_000L
        list.removeAll { it.done && it.lastFireMillis() < cutoff }
        val id = (list.maxOfOrNull { it.id } ?: 0) + 1
        val r = Reminder(id, name, time, repeats, gap)
        list.add(r)
        saveAll(ctx, list)
        return r
    }

    fun get(ctx: Context, id: Int): Reminder? = all(ctx).firstOrNull { it.id == id }

    fun markDone(ctx: Context, id: Int) {
        val list = all(ctx)
        list.firstOrNull { it.id == id }?.done = true
        saveAll(ctx, list)
    }

    fun remove(ctx: Context, id: Int) {
        val list = all(ctx)
        list.removeAll { it.id == id }
        saveAll(ctx, list)
    }
}
