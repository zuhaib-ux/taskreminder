package com.zuhaib.taskreminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val cal: Calendar = Calendar.getInstance().apply {
        add(Calendar.MINUTE, 5); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    private val repeatOptions = arrayOf(1, 2, 3, 4, 5)
    private val gapOptions = arrayOf(1, 2, 5, 10, 15, 30)

    private lateinit var nameInput: EditText
    private lateinit var dateBtn: MaterialButton
    private lateinit var timeBtn: MaterialButton
    private lateinit var repeatSpin: Spinner
    private lateinit var gapSpin: Spinner
    private lateinit var listBox: LinearLayout
    private lateinit var batteryBtn: MaterialButton

    private val dateFmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val fullFmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Task Reminder"
        Notifier.ensureChannel(this)
        setContentView(buildUi())
        askNotificationPermission()
        refreshButtons()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
        batteryBtn.visibility = if (isBatteryOptimized()) View.VISIBLE else View.GONE
    }

    // ---------------- UI ----------------

    private fun label(text: String, size: Float = 14f, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }

        root.addView(label("Task ka naam", bold = true))
        nameInput = EditText(this).apply {
            hint = "Maslan: Doctor ko call karna"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        root.addView(nameInput)

        root.addView(label("Kab yaad dilaun?", bold = true))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dateBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        timeBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        row.addView(dateBtn, lp)
        row.addView(timeBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)
        dateBtn.setOnClickListener { pickDate() }
        timeBtn.setOnClickListener { pickTime() }

        root.addView(label("Kitni baar notification aaye?", bold = true))
        repeatSpin = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                repeatOptions.map { if (it == 1) "1 baar" else "$it baar" })
            setSelection(2) // default 3
        }
        root.addView(repeatSpin)

        root.addView(label("Har notification ke beech kitna gap?", bold = true))
        gapSpin = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                gapOptions.map { "$it minute" })
            setSelection(2) // default 5 min
        }
        root.addView(gapSpin)

        val saveBtn = MaterialButton(this).apply {
            text = "Reminder set karo"
            textSize = 16f
            setOnClickListener { save() }
        }
        root.addView(saveBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(20) })

        batteryBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "⚠ Battery restriction hatao (zaroori)"
            setOnClickListener { askBatteryExemption() }
        }
        root.addView(batteryBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        root.addView(label("Aane wale reminders", 16f, true).apply { setPadding(0, dp(24), 0, dp(4)) })
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)

        return ScrollView(this).apply { addView(root) }
    }

    private fun refreshButtons() {
        dateBtn.text = "📅 " + dateFmt.format(cal.time)
        timeBtn.text = "🕒 " + timeFmt.format(cal.time)
    }

    private fun pickDate() {
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d); refreshButtons()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            .apply { datePicker.minDate = System.currentTimeMillis() - 1000 }
            .show()
    }

    private fun pickTime() {
        TimePickerDialog(this, { _, h, min ->
            cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
            cal.set(Calendar.SECOND, 0); refreshButtons()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
    }

    private fun save() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            nameInput.error = "Task ka naam likho"; return
        }
        val time = cal.timeInMillis
        if (time <= System.currentTimeMillis()) {
            Toast.makeText(this, "Yeh time guzar chuka hai — aage ka time chuno", Toast.LENGTH_LONG).show()
            return
        }
        val repeats = repeatOptions[repeatSpin.selectedItemPosition]
        val gap = gapOptions[gapSpin.selectedItemPosition]
        val r = Store.add(this, name, time, repeats, gap)
        Scheduler.schedule(this, r)
        nameInput.setText("")
        Toast.makeText(this, "✓ ${fullFmt.format(Date(time))} par yaad dilaunga ($repeats baar)", Toast.LENGTH_LONG).show()
        refreshList()
    }

    private fun refreshList() {
        listBox.removeAllViews()
        val items = Store.all(this).filter { !it.done }.sortedBy { it.timeMillis }
        if (items.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "Abhi koi reminder set nahi."; alpha = 0.6f; setPadding(0, dp(8), 0, 0)
            })
            return
        }
        items.forEach { r ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(4), dp(10))
                setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            }
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(this).apply {
                text = r.name; textSize = 16f; setTypeface(typeface, Typeface.BOLD)
                setTextColor(0xFF222222.toInt())
            })
            texts.addView(TextView(this).apply {
                text = "${fullFmt.format(Date(r.timeMillis))} • ${r.repeats} baar, har ${r.gapMin} min"
                textSize = 13f; setTextColor(0xFF555555.toInt())
            })
            card.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val del = ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_delete)
                background = null
                contentDescription = "Delete"
                setOnClickListener { confirmDelete(r) }
            }
            card.addView(del)
            listBox.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) })
        }
    }

    private fun confirmDelete(r: Reminder) {
        AlertDialog.Builder(this)
            .setTitle("Reminder hatana hai?")
            .setMessage(r.name)
            .setPositiveButton("Haan") { _, _ ->
                Scheduler.cancel(this, r.id); Notifier.clear(this, r.id)
                Store.remove(this, r.id); refreshList()
            }
            .setNegativeButton("Nahi", null)
            .show()
    }

    // ---------------- Permissions ----------------

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun isBatteryOptimized(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return !pm.isIgnoringBatteryOptimizations(packageName)
    }

    @SuppressLint("BatteryLife")
    private fun askBatteryExemption() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
