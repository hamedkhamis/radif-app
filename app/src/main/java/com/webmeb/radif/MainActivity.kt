package com.webmeb.radif

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val INK = "#13262B"
    private val MUTED = "#5B7076"
    private val ACCENT = "#0B7A85"

    private lateinit var list: LinearLayout
    private lateinit var summary: TextView
    private lateinit var bar: ProgressBar
    private lateinit var dateTv: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.seedIfNeeded(this)
        Scheduler.rescheduleAll(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val frame = FrameLayout(this)
        frame.layoutDirection = View.LAYOUT_DIRECTION_RTL
        frame.setBackgroundColor(Color.parseColor("#F1F5F6"))

        val sv = ScrollView(this)
        frame.addView(sv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), dp(36), dp(18), dp(120))
        sv.addView(col)

        // سربرگ
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(text("ردیف", 30f, true, INK))
        dateTv = text("", 14f, false, MUTED)
        titles.addView(dateTv)
        head.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val menu = text("⋮", 30f, true, INK)
        menu.setPadding(dp(14), 0, dp(6), 0)
        menu.setOnClickListener { showMenu(it) }
        head.addView(menu)
        col.addView(head)

        // کارت خلاصه
        val sc = LinearLayout(this)
        sc.orientation = LinearLayout.VERTICAL
        sc.setPadding(dp(16), dp(14), dp(16), dp(14))
        sc.background = cardBg()
        summary = text("", 16f, true, INK)
        sc.addView(summary)
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        bar.progressTintList = ColorStateList.valueOf(Color.parseColor(ACCENT))
        sc.addView(bar, lp(top = 10))
        col.addView(sc, lp(top = 16))

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        col.addView(list, lp(top = 4))

        // دکمه +
        val fab = Button(this)
        fab.text = "+"
        fab.textSize = 30f
        fab.setTextColor(Color.WHITE)
        fab.isAllCaps = false
        val circle = GradientDrawable()
        circle.shape = GradientDrawable.OVAL
        circle.setColor(Color.parseColor(ACCENT))
        fab.background = circle
        fab.elevation = dp(6).toFloat()
        fab.setPadding(0, 0, 0, dp(4))
        fab.setOnClickListener { taskDialog(null) }
        val flp = FrameLayout.LayoutParams(dp(62), dp(62))
        flp.gravity = Gravity.BOTTOM or Gravity.START
        flp.setMargins(dp(20), 0, dp(20), dp(28))
        frame.addView(fab, flp)

        setContentView(frame)
    }

    override fun onResume() {
        super.onResume()
        Scheduler.rescheduleAll(this)
        render()
    }

    // ---------------------------------------------------------------- helpers

    private fun lp(top: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        p.topMargin = dp(top)
        return p
    }

    private fun cardBg(): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(Color.WHITE)
        g.cornerRadius = dp(14).toFloat()
        return g
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

    private fun btn(label: String, onClick: (View) -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick(it) }
        return b
    }

    private fun persianDate(): String {
        return try {
            val f = android.icu.text.SimpleDateFormat("EEEE d MMMM", Locale("fa", "IR"))
            f.calendar = android.icu.util.PersianCalendar()
            f.format(Date())
        } catch (e: Exception) {
            ""
        }
    }

    private val dowOrder = intArrayOf(
        Calendar.SATURDAY, Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
        Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
    )
    private val dowLetters = arrayOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    private val dowNames = mapOf(
        Calendar.SATURDAY to "شنبه", Calendar.SUNDAY to "یکشنبه", Calendar.MONDAY to "دوشنبه",
        Calendar.TUESDAY to "سه‌شنبه", Calendar.WEDNESDAY to "چهارشنبه",
        Calendar.THURSDAY to "پنجشنبه", Calendar.FRIDAY to "جمعه"
    )

    private fun repLabel(t: Task): String = when (t.rep) {
        1 -> "هر روز"
        2 -> "شنبه تا چهارشنبه"
        3 -> dowOrder.indices.filter { (t.mask and (1 shl (dowOrder[it] - 1))) != 0 }
            .joinToString(" ") { dowLetters[it] }
        else -> when {
            t.onceDate.isNotEmpty() && t.onceDate > Dates.key(0) -> "فردا"
            t.onceDate.isNotEmpty() && t.onceDate < Dates.key(0) -> "عقب‌افتاده"
            else -> ""
        }
    }

    // ---------------------------------------------------------------- main list

    private fun render() {
        dateTv.text = persianDate()
        list.removeAllViews()

        val due = Store.dueToday(this)
        val closed = due.filter { Store.status(this, it).isNotEmpty() }
        val open = due.filter { Store.status(this, it).isEmpty() }
            .sortedWith(compareBy<Task>({ !it.main }, { if (it.h < 0) 9999 else it.h * 60 + it.m }, { it.created }))

        val doneCount = due.count { Store.status(this, it) == "done" }
        summary.text = Model.fa("امروز: $doneCount از ${due.size}      استریک: ${Store.streak(this)} روز")
        bar.progress = if (due.isEmpty()) 0 else doneCount * 100 / due.size

        // تایمر تمرکز در حال اجرا
        val ft = Store.focusTask(this)
        if (ft != 0 && Store.focusEnd(this) > System.currentTimeMillis()) {
            val t = Store.get(this, ft)
            if (t != null) {
                val box = LinearLayout(this)
                box.orientation = LinearLayout.HORIZONTAL
                box.gravity = Gravity.CENTER_VERTICAL
                box.setPadding(dp(14), dp(10), dp(14), dp(10))
                val g = cardBg()
                g.setColor(Color.parseColor("#DDF0F2"))
                box.background = g
                val c = Calendar.getInstance()
                c.timeInMillis = Store.focusEnd(this)
                box.addView(
                    text("در حال تمرکز: ${t.title} (تا ${Model.timeText(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))})", 14f, true, ACCENT),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                box.addView(btn("لغو") {
                    Focus.cancel(this, t)
                    render()
                })
                list.addView(box, lp(top = 12))
            }
        }

        if (due.isEmpty()) {
            list.addView(text("امروز چیزی نداری. با + یه کار اضافه کن.", 15f, false, MUTED), lp(top = 24))
        }
        for (t in open) list.addView(taskRow(t), lp(top = 10))

        if (closed.isNotEmpty()) {
            list.addView(text("انجام شده", 14f, true, MUTED), lp(top = 22))
            for (t in closed) list.addView(taskRow(t), lp(top = 8))
        }
    }

    private fun taskRow(t: Task): View {
        val st = Store.status(this, t)
        val done = st == "done"
        val closed = st.isNotEmpty()

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(10), dp(10), dp(10), dp(10))
        row.background = cardBg()

        val cb = CheckBox(this)
        cb.isChecked = done
        cb.setOnClickListener {
            Store.setDone(this, t, cb.isChecked)
            render()
        }
        row.addView(cb)

        val mid = LinearLayout(this)
        mid.orientation = LinearLayout.VERTICAL
        val title = text((if (t.main) "★ " else "") + t.title, 17f, !closed, if (closed) MUTED else INK)
        if (closed) title.paintFlags = title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        mid.addView(title)

        val parts = mutableListOf<String>()
        if (t.h >= 0) parts.add(Model.timeText(t.h, t.m))
        val rl = repLabel(t)
        if (rl.isNotEmpty()) parts.add(rl)
        if (st == "skip") parts.add("نشد")
        if (parts.isNotEmpty()) mid.addView(text(parts.joinToString("  ·  "), 13f, false, MUTED))
        row.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (!closed) {
            val f = btn("▶ ۲۵") {
                Focus.start(this, t)
                render()
            }
            f.textSize = 12f
            f.minWidth = 0
            f.minimumWidth = 0
            row.addView(f)
        }

        row.setOnClickListener { taskDialog(t) }
        return row
    }

    // ---------------------------------------------------------------- menu

    private fun showMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, "پارکینگ (" + Model.fa(Store.notes(this).size.toString()) + ")")
        pm.menu.add(0, 2, 1, "آمار")
        pm.menu.add(0, 3, 2, "تنظیمات")
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> parkingDialog()
                2 -> statsDialog()
                3 -> settingsDialog()
            }
            true
        }
        pm.show()
    }

    // ---------------------------------------------------------------- add / edit

    private fun taskDialog(ex: Task?) {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutDirection = View.LAYOUT_DIRECTION_RTL
        col.setPadding(dp(20), dp(12), dp(20), dp(4))

        val et = EditText(this)
        et.hint = "چی باید انجام بدی؟"
        et.setText(ex?.title ?: "")
        et.textDirection = View.TEXT_DIRECTION_RTL
        et.gravity = Gravity.START
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        col.addView(et)

        val more = LinearLayout(this)
        more.orientation = LinearLayout.VERTICAL
        more.visibility = if (ex != null) View.VISIBLE else View.GONE

        val toggle = text("گزینه‌های بیشتر ▾", 14f, true, ACCENT)
        toggle.setPadding(0, dp(12), 0, dp(8))
        toggle.setOnClickListener {
            more.visibility = if (more.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        col.addView(toggle)

        var th = ex?.h ?: -1
        var tm = ex?.m ?: 0

        val cbMain = CheckBox(this)
        cbMain.text = "★ کار اصلی (بالای لیست)"
        cbMain.isChecked = ex?.main == true
        more.addView(cbMain)

        val btnTime = Button(this)
        btnTime.isAllCaps = false
        fun refreshTime() {
            btnTime.text = if (th < 0) "یادآوری: بدون ساعت" else "یادآوری: " + Model.timeText(th, tm)
        }
        refreshTime()
        btnTime.setOnClickListener {
            TimePickerDialog(this, { _, hh, mm ->
                th = hh
                tm = mm
                refreshTime()
            }, if (th < 0) 10 else th, tm, true).show()
        }
        val btnClear = Button(this)
        btnClear.isAllCaps = false
        btnClear.text = "حذف ساعت"
        btnClear.setOnClickListener {
            th = -1
            refreshTime()
        }
        val timeRow = LinearLayout(this)
        timeRow.orientation = LinearLayout.HORIZONTAL
        timeRow.addView(btnTime, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        timeRow.addView(btnClear)
        more.addView(timeRow)

        val repOpts = arrayOf("فقط یک بار، امروز", "فقط یک بار، فردا", "هر روز", "شنبه تا چهارشنبه", "روزهای دلخواه")
        val spRep = Spinner(this)
        spRep.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, repOpts)
        val daysRow = LinearLayout(this)
        daysRow.orientation = LinearLayout.HORIZONTAL
        val dayBoxes = mutableListOf<CheckBox>()
        for (i in dowOrder.indices) {
            val c = CheckBox(this)
            c.text = dowLetters[i]
            c.isChecked = ex != null && ex.rep == 3 && (ex.mask and (1 shl (dowOrder[i] - 1))) != 0
            dayBoxes.add(c)
            daysRow.addView(c, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        spRep.setSelection(
            when {
                ex == null -> 0
                ex.rep == 0 -> if (ex.onceDate > Dates.key(0)) 1 else 0
                ex.rep == 1 -> 2
                ex.rep == 2 -> 3
                else -> 4
            }
        )
        daysRow.visibility = if (spRep.selectedItemPosition == 4) View.VISIBLE else View.GONE
        spRep.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                daysRow.visibility = if (pos == 4) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        more.addView(text("تکرار", 13f, false, MUTED), lp(top = 8))
        more.addView(spRep)
        more.addView(daysRow)

        val nagOpts = arrayOf("یادآوری یک بار", "تا ۳ بار، هر ۱۰ دقیقه", "تا ۶ بار، هر ۱۰ دقیقه (سخت)")
        val spNag = Spinner(this)
        spNag.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, nagOpts)
        spNag.setSelection(ex?.nag ?: 2)
        more.addView(text("فشار", 13f, false, MUTED), lp(top = 8))
        more.addView(spNag)

        col.addView(more)

        val sv = ScrollView(this)
        sv.addView(col)

        val dlg = AlertDialog.Builder(this)
            .setTitle(if (ex == null) "کار جدید" else "ویرایش کار")
            .setView(sv)
            .setPositiveButton(if (ex == null) "افزودن" else "ذخیره", null)
            .setNeutralButton(if (ex == null) "پارکینگ" else "حذف", null)
            .setNegativeButton("بستن", null)
            .create()
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.setOnShowListener {
            et.requestFocus()
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = et.text.toString().trim()
                if (title.isEmpty()) {
                    et.error = "عنوان رو بنویس"
                    return@setOnClickListener
                }
                val t = ex ?: Task(Store.nextId(this), title)
                t.title = title
                t.h = th
                t.m = tm
                t.main = cbMain.isChecked
                t.nag = spNag.selectedItemPosition
                when (spRep.selectedItemPosition) {
                    0 -> { t.rep = 0; t.onceDate = Dates.key(0) }
                    1 -> { t.rep = 0; t.onceDate = Dates.key(1) }
                    2 -> t.rep = 1
                    3 -> t.rep = 2
                    else -> {
                        t.rep = 3
                        var mask = 0
                        for (i in dowOrder.indices) if (dayBoxes[i].isChecked) mask = mask or (1 shl (dowOrder[i] - 1))
                        t.mask = mask
                    }
                }
                if (t.rep == 3 && t.mask == 0) {
                    Toast.makeText(this, "حداقل یه روز انتخاب کن.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (t.rep == 0 && ex != null && ex.doneDate.isNotEmpty()) t.doneDate = ex.doneDate
                Store.upsert(this, t)
                Scheduler.cancelAll(this, t.id)
                Scheduler.scheduleBase(this, t)
                dlg.dismiss()
                render()
            }
            var armed = false
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { v ->
                if (ex == null) {
                    val title = et.text.toString().trim()
                    if (title.isEmpty()) {
                        et.error = "اول بنویس"
                        return@setOnClickListener
                    }
                    Store.addNote(this, title)
                    Toast.makeText(this, "تو پارکینگ ذخیره شد.", Toast.LENGTH_SHORT).show()
                    dlg.dismiss()
                } else {
                    if (!armed) {
                        armed = true
                        (v as Button).text = "مطمئنی؟ دوباره بزن"
                        return@setOnClickListener
                    }
                    Scheduler.cancelAll(this, ex.id)
                    Focus.cancel(this, ex)
                    Store.delete(this, ex.id)
                    dlg.dismiss()
                    render()
                }
            }
        }
        dlg.show()
    }

    // ---------------------------------------------------------------- parking

    private fun parkingDialog() {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutDirection = View.LAYOUT_DIRECTION_RTL
        col.setPadding(dp(20), dp(8), dp(20), dp(8))

        col.addView(text("هر فکر، ایده یا سؤال متفرقه رو اینجا بنویس و برگرد سر کارت. بعداً تصمیم می‌گیری.", 13f, false, MUTED))
        val et = EditText(this)
        et.hint = "بنویس..."
        et.textDirection = View.TEXT_DIRECTION_RTL
        et.gravity = Gravity.START
        col.addView(et)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL

        fun fill() {
            box.removeAllViews()
            val notes = Store.notes(this)
            if (notes.isEmpty()) {
                box.addView(text("پارکینگ خالیه.", 14f, false, MUTED), lp(top = 12))
                return
            }
            for (n in notes) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.addView(text(n.text, 15f, false, INK), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val toTask = btn("تسک") {
                    val t = Task(Store.nextId(this), n.text, onceDate = Dates.key(0))
                    Store.upsert(this, t)
                    Store.removeNote(this, n.ts)
                    fill()
                    render()
                }
                toTask.minWidth = 0
                toTask.minimumWidth = 0
                val del = btn("حذف") {
                    Store.removeNote(this, n.ts)
                    fill()
                }
                del.minWidth = 0
                del.minimumWidth = 0
                row.addView(toTask)
                row.addView(del)
                box.addView(row, lp(top = 6))
            }
        }

        col.addView(btn("ذخیره در پارکینگ") {
            val s = et.text.toString().trim()
            if (s.isNotEmpty()) {
                Store.addNote(this, s)
                et.setText("")
                fill()
            }
        }, lp(top = 8))
        col.addView(box)
        fill()

        val sv = ScrollView(this)
        sv.addView(col)
        AlertDialog.Builder(this)
            .setTitle("پارکینگ")
            .setView(sv)
            .setNegativeButton("بستن", null)
            .show()
    }

    // ---------------------------------------------------------------- stats

    private fun statsDialog() {
        val due = Store.dueToday(this)
        val done = due.count { Store.status(this, it) == "done" }
        val sb = StringBuilder()
        sb.append("امروز: $done از ${due.size}\n")
        sb.append("استریک: ${Store.streak(this)} روز پشت سر هم\n")
        sb.append("دور تمرکز امروز: ${Store.focusCount(this, Dates.key(0))}\n\n")
        sb.append("هفت روز اخیر\n")
        for (off in 0..6) {
            val key = Dates.key(-off)
            val dow = Dates.cal(key).get(Calendar.DAY_OF_WEEK)
            val n = Store.doneCount(this, key)
            val name = (dowNames[dow] ?: "")
            sb.append(name.padEnd(10, ' ')).append("  ").append("■".repeat(minOf(n, 12))).append(" ").append(n).append("\n")
        }
        val tv = text(Model.fa(sb.toString()), 16f, false, INK)
        tv.setPadding(dp(22), dp(12), dp(22), dp(8))
        tv.layoutDirection = View.LAYOUT_DIRECTION_RTL
        AlertDialog.Builder(this)
            .setTitle("آمار")
            .setView(tv)
            .setNegativeButton("بستن", null)
            .show()
    }

    // ---------------------------------------------------------------- settings

    private fun settingsDialog() {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutDirection = View.LAYOUT_DIRECTION_RTL
        col.setPadding(dp(20), dp(8), dp(20), dp(8))

        col.addView(text("تنظیمات لازم، فقط یک بار", 15f, true, INK))
        col.addView(btn("۱. اجازه نوتیفیکیشن") { askNotif() }, lp(top = 6))
        col.addView(btn("۲. اجازه آلارم دقیق") { askExact() }, lp(top = 6))
        col.addView(btn("۳. حذف محدودیت باتری") { askBattery() }, lp(top = 6))
        col.addView(btn("تست همین الان") {
            val t = Task(999999, "تست یادآوری", "اگه این رو می‌بینی، همه چیز درسته.")
            Notifier.show(this, t, 0)
        }, lp(top = 14))
        col.addView(btn("برگرداندن کارهای پیش‌فرض") {
            val n = Store.addDefaults(this)
            Scheduler.rescheduleAll(this)
            Toast.makeText(this, if (n > 0) "$n کار اضافه شد." else "همه‌شون از قبل هست.", Toast.LENGTH_SHORT).show()
            render()
        }, lp(top = 6))
        col.addView(
            text(
                "شیائومی: تنظیمات، برنامه‌ها، ردیف، شروع خودکار رو روشن کن و صرفه‌جویی باتری رو روی «بدون محدودیت» بذار. برنامه رو توی لیست اخیر قفل کن.",
                13f, false, MUTED
            ), lp(top = 14)
        )
        val sv = ScrollView(this)
        sv.addView(col)
        AlertDialog.Builder(this)
            .setTitle("تنظیمات")
            .setView(sv)
            .setNegativeButton("بستن", null)
            .show()
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
