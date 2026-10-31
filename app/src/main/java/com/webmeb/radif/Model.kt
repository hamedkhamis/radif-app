package com.webmeb.radif

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object Model {
    const val NAG_MIN = 10

    // شنبه تا چهارشنبه
    val WORKDAYS = setOf(
        Calendar.SATURDAY, Calendar.SUNDAY, Calendar.MONDAY,
        Calendar.TUESDAY, Calendar.WEDNESDAY
    )

    fun fa(s: String): String =
        s.map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

    fun timeText(h: Int, m: Int): String =
        fa(String.format(Locale.US, "%02d:%02d", h, m))
}

object Dates {
    fun key(offset: Int = 0): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, offset)
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(c.time)
    }

    fun cal(key: String): Calendar {
        val c = Calendar.getInstance()
        c.set(key.substring(0, 4).toInt(), key.substring(4, 6).toInt() - 1, key.substring(6, 8).toInt())
        return c
    }
}

/**
 * rep: 0 یک بار، 1 هر روز، 2 شنبه تا چهارشنبه، 3 روزهای دلخواه (mask)
 * nag: 0 فقط یک بار، 1 تا ۳ تکرار، 2 تا ۶ تکرار (هر ۱۰ دقیقه)
 */
data class Task(
    var id: Int,
    var title: String,
    var note: String = "",
    var h: Int = -1,
    var m: Int = 0,
    var rep: Int = 0,
    var mask: Int = 0,
    var onceDate: String = "",
    var doneDate: String = "",
    var main: Boolean = false,
    var nag: Int = 2,
    var created: Long = System.currentTimeMillis()
) {
    val maxNags: Int get() = when (nag) { 0 -> 0; 1 -> 3; else -> 6 }

    fun allowsDay(dow: Int): Boolean = when (rep) {
        1 -> true
        2 -> Model.WORKDAYS.contains(dow)
        3 -> (mask and (1 shl (dow - 1))) != 0
        else -> true
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("note", note)
        .put("h", h).put("m", m).put("rep", rep).put("mask", mask)
        .put("onceDate", onceDate).put("doneDate", doneDate)
        .put("main", main).put("nag", nag).put("created", created)

    companion object {
        fun fromJson(o: JSONObject): Task = Task(
            o.getInt("id"), o.getString("title"), o.optString("note", ""),
            o.optInt("h", -1), o.optInt("m", 0), o.optInt("rep", 0), o.optInt("mask", 0),
            o.optString("onceDate", ""), o.optString("doneDate", ""),
            o.optBoolean("main", false), o.optInt("nag", 2), o.optLong("created", 0L)
        )
    }
}

data class Note(val ts: Long, val text: String)

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("radif2", Context.MODE_PRIVATE)

    // ---------- تسک‌ها ----------
    fun tasks(c: Context): MutableList<Task> {
        val out = mutableListOf<Task>()
        try {
            val a = JSONArray(sp(c).getString("tasks", "[]"))
            for (i in 0 until a.length()) out.add(Task.fromJson(a.getJSONObject(i)))
        } catch (e: Exception) {
        }
        return out
    }

    private fun saveTasks(c: Context, list: List<Task>) {
        val a = JSONArray()
        for (t in list) a.put(t.toJson())
        sp(c).edit().putString("tasks", a.toString()).apply()
    }

    fun get(c: Context, id: Int): Task? = tasks(c).firstOrNull { it.id == id }

    fun upsert(c: Context, t: Task) {
        val l = tasks(c)
        val i = l.indexOfFirst { it.id == t.id }
        if (i >= 0) l[i] = t else l.add(t)
        saveTasks(c, l)
    }

    fun delete(c: Context, id: Int) {
        saveTasks(c, tasks(c).filter { it.id != id })
    }

    fun nextId(c: Context): Int {
        val n = sp(c).getInt("nid", 1)
        sp(c).edit().putInt("nid", n + 1).apply()
        return n
    }

    fun isDueToday(t: Task): Boolean {
        val today = Dates.key(0)
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return when (t.rep) {
            0 -> if (t.doneDate.isEmpty()) (t.onceDate.isEmpty() || t.onceDate <= today) else t.doneDate == today
            else -> t.allowsDay(dow)
        }
    }

    fun dueToday(c: Context): List<Task> = tasks(c).filter { isDueToday(it) }

    // ---------- وضعیت امروز: "" ، "done" ، "skip" ----------
    fun status(c: Context, t: Task): String {
        if (t.rep == 0) return if (t.doneDate.isNotEmpty()) "done" else ""
        return sp(c).getString("st_${t.id}_${Dates.key(0)}", "") ?: ""
    }

    private fun addCount(c: Context, key: String, delta: Int) {
        val v = sp(c).getInt(key, 0) + delta
        sp(c).edit().putInt(key, if (v < 0) 0 else v).apply()
    }

    fun count(c: Context, key: String): Int = sp(c).getInt(key, 0)
    fun doneCount(c: Context, dateKey: String): Int = count(c, "cnt_$dateKey")
    fun focusCount(c: Context, dateKey: String): Int = count(c, "foc_$dateKey")
    fun addFocus(c: Context) = addCount(c, "foc_${Dates.key(0)}", 1)

    fun setDone(c: Context, t: Task, done: Boolean) {
        val was = status(c, t) == "done"
        if (was == done) return
        if (t.rep == 0) {
            t.doneDate = if (done) Dates.key(0) else ""
            upsert(c, t)
        } else {
            sp(c).edit().putString("st_${t.id}_${Dates.key(0)}", if (done) "done" else "").apply()
        }
        addCount(c, "cnt_${Dates.key(0)}", if (done) 1 else -1)
        if (done) {
            Scheduler.stopNags(c, t.id)
            Focus.cancel(c, t)
        }
    }

    /** «امروز نشد»: کار یک‌باره می‌ره برای فردا، کار تکراری امروز رد می‌شه */
    fun skip(c: Context, t: Task) {
        if (t.rep == 0) {
            t.onceDate = Dates.key(1)
            upsert(c, t)
            Scheduler.stopNags(c, t.id)
            Scheduler.scheduleBase(c, t)
        } else {
            sp(c).edit().putString("st_${t.id}_${Dates.key(0)}", "skip").apply()
            Scheduler.stopNags(c, t.id)
        }
        Focus.cancel(c, t)
    }

    fun streak(c: Context): Int {
        var s = 0
        val start = if (doneCount(c, Dates.key(0)) == 0) 1 else 0
        for (i in 0 until 365) {
            val key = Dates.key(-(start + i))
            val dow = Dates.cal(key).get(Calendar.DAY_OF_WEEK)
            if (doneCount(c, key) > 0) {
                s++
            } else if (dow == Calendar.THURSDAY || dow == Calendar.FRIDAY) {
                continue
            } else {
                break
            }
        }
        return s
    }

    // ---------- پارکینگ ----------
    fun notes(c: Context): List<Note> {
        val out = mutableListOf<Note>()
        try {
            val a = JSONArray(sp(c).getString("notes", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Note(o.getLong("ts"), o.getString("text")))
            }
        } catch (e: Exception) {
        }
        return out.sortedByDescending { it.ts }
    }

    private fun saveNotes(c: Context, list: List<Note>) {
        val a = JSONArray()
        for (n in list) a.put(JSONObject().put("ts", n.ts).put("text", n.text))
        sp(c).edit().putString("notes", a.toString()).apply()
    }

    fun addNote(c: Context, text: String) {
        saveNotes(c, notes(c) + Note(System.currentTimeMillis(), text))
    }

    fun removeNote(c: Context, ts: Long) {
        saveNotes(c, notes(c).filter { it.ts != ts })
    }

    // ---------- تمرکز ----------
    fun focusTask(c: Context): Int = sp(c).getInt("focus_task", 0)
    fun focusEnd(c: Context): Long = sp(c).getLong("focus_end", 0L)

    fun setFocus(c: Context, id: Int, end: Long) {
        sp(c).edit().putInt("focus_task", id).putLong("focus_end", end).apply()
    }

    fun clearFocus(c: Context) {
        sp(c).edit().putInt("focus_task", 0).putLong("focus_end", 0L).apply()
    }

    // ---------- کارهای پیش‌فرض ----------
    fun defaults(): List<Task> = listOf(
        Task(0, "شروع کار اصلی", "گوشی رو بذار تو کشو. مهم‌ترین کار رو باز کن و ۱۰ دقیقه، فقط ۱۰ دقیقه.",
            10, 0, rep = 2, main = true),
        Task(0, "برگشت به تماس‌های بی‌پاسخ", "لیست تماس‌های بی‌پاسخ رو باز کن. به هر کدوم زنگ بزن یا همین الان پیام بده.",
            16, 30, rep = 2),
        Task(0, "بستن روز و نوشتن کار فردا", "کار اصلی فردا رو روی کاغذ بنویس. میز رو جمع کن. بعد برو.",
            17, 45, rep = 2),
        Task(0, "گوشی رو بیرون اتاق خواب شارژ بذار", "همین الان گوشی رو ببر بیرون از اتاق خواب و شارژ بزن.",
            23, 30, rep = 1)
    )

    fun addDefaults(c: Context): Int {
        val have = tasks(c).map { it.title }.toSet()
        var n = 0
        for (d in defaults()) {
            if (have.contains(d.title)) continue
            d.id = nextId(c)
            upsert(c, d)
            n++
        }
        return n
    }

    fun seedIfNeeded(c: Context) {
        if (sp(c).getBoolean("seeded", false)) return
        addDefaults(c)
        sp(c).edit().putBoolean("seeded", true).apply()
    }
}
