package com.webmeb.radif

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var list: LinearLayout

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Scheduler.scheduleAll(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = ScrollView(this)
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setBackgroundColor(Color.parseColor("#F1F5F6"))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(40), dp(20), dp(40))
        root.addView(col)

        col.addView(text("ردیف", 30f, true, "#13262B"))
        col.addView(text("شنبه تا چهارشنبه، سه یادآوری. تا جواب ندی ول نمی‌کنه.", 15f, false, "#5B7076"))

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        col.addView(list, lp(top = 20))

        col.addView(text("تنظیمات لازم، فقط یک بار", 17f, true, "#13262B"), lp(top = 28))
        col.addView(btn("۱. اجازه نوتیفیکیشن") { askNotif() }, lp(top = 8))
        col.addView(btn("۲. اجازه آلارم دقیق") { askExact() }, lp(top = 8))
        col.addView(btn("۳. حذف محدودیت باتری") { askBattery() }, lp(top = 8))
        col.addView(btn("تست همین الان") { Notifier.show(this, Model.items[0], 0) }, lp(top = 16))
        col.addView(
            text(
                "اگه گوشی شیائومی داری: تنظیمات، برنامه‌ها، ردیف، شروع خودکار رو روشن کن و صرفه‌جویی باتری رو روی «بدون محدودیت» بذار. برنامه رو توی لیست اخیر قفل کن.",
                14f, false, "#5B7076"
            ), lp(top = 16)
        )
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun lp(top: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        p.topMargin = dp(top)
        return p
    }

    private fun text(s: String, size: Float, bold: Boolean, color: String): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(Color.parseColor(color))
        t.textDirection = View.TEXT_DIRECTION_RTL
        t.gravity = Gravity.START
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun btn(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }

    private fun timeText(h: Int, m: Int): String = Model.fa(String.format("%02d:%02d", h, m))

    private fun render() {
        list.removeAllViews()
        for (item in Model.items) {
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(dp(16), dp(16), dp(16), dp(16))
            val bg = GradientDrawable()
            bg.setColor(Color.WHITE)
            bg.cornerRadius = dp(14).toFloat()
            card.background = bg

            card.addView(text(item.title, 18f, true, "#13262B"))

            val st = Prefs.status(this, item.id)
            val (label, color) = when (st) {
                "done" -> "امروز: انجام شد" to "#2F7D4F"
                "skip" -> "امروز: نشد" to "#9A4F08"
                else -> "امروز: منتظر" to "#5B7076"
            }
            card.addView(text(label, 14f, false, color), lp(top = 4))

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL

            val h = Prefs.hour(this, item)
            val m = Prefs.minute(this, item)
            val timeBtn = btn(timeText(h, m)) {
                TimePickerDialog(this, { _, hh, mm ->
                    Prefs.setTime(this, item, hh, mm)
                    Scheduler.scheduleAll(this)
                    render()
                }, Prefs.hour(this, item), Prefs.minute(this, item), true).show()
            }
            val doneBtn = btn("انجام شد") {
                Prefs.setStatus(this, item.id, "done")
                Scheduler.stopNags(this, item.id)
                render()
            }
            val w = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(timeBtn, w)
            row.addView(doneBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(row, lp(top = 8))

            list.addView(card, lp(top = 12))
        }
    }

    private fun askNotif() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            Toast.makeText(this, "در این نسخه اندروید لازم نیست.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askExact() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
                return
            }
        }
        Toast.makeText(this, "از قبل فعاله.", Toast.LENGTH_SHORT).show()
    }

    private fun askBattery() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "از قبل فعاله.", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        )
    }
}
