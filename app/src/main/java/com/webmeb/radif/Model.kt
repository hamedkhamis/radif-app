package com.webmeb.radif

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Item(
    val id: Int,
    val title: String,
    val body: String,
    val defH: Int,
    val defM: Int,
    val nags: List<String>
)

object Model {
    const val NAG_MIN = 10
    const val MAX_NAGS = 6

    // شنبه تا چهارشنبه
    val WORKDAYS = setOf(
        Calendar.SATURDAY, Calendar.SUNDAY, Calendar.MONDAY,
        Calendar.TUESDAY, Calendar.WEDNESDAY
    )

    val items = listOf(
        Item(
            1,
            "وقت شروعه. بشین پشت میز.",
            "گوشی رو بذار تو کشو. مهم‌ترین کار رو باز کن و ۱۰ دقیقه، فقط ۱۰ دقیقه.",
            10, 0,
            listOf(
                "هنوز شروع نکردی. تایمر ۱۰ دقیقه رو بزن، همین الان.",
                "۲۰ دقیقه گذشت. گوشی رو بذار زمین و کار اصلی رو باز کن.",
                "نه حال خوب لازمه نه انگیزه. فقط ۱۰ دقیقه شروع کن.",
                "پیام‌ها و اینستا فرار از کاره. برگرد سر کار اصلی.",
                "هر دقیقه که می‌گذره کار سنگین‌تر می‌شه. شروع کن.",
                "آخرین یادآوری. ۱۰ دقیقه، بعدش هر چی خواستی."
            )
        ),
        Item(
            2,
            "تماس‌های بی‌پاسخ",
            "لیست تماس‌های بی‌پاسخ رو باز کن. به هر کدوم زنگ بزن یا همین الان پیام بده.",
            16, 30,
            listOf(
                "هنوز زنگ نزدی. فقط اولین تماس بی‌پاسخ، ۳۰ ثانیه.",
                "حوصله صحبت نداری؟ پیام بده: «الان نمی‌تونم صحبت کنم، پیام بدید.»",
                "تماس بی‌پاسخ هر ساعت سنگین‌تر می‌شه. همین الان.",
                "یه نفر منتظر جوابه. پیام بده و راحت شو.",
                "جواب ناقص بهتر از بی‌جواب. بفرست.",
                "آخرین یادآوری. یه پیام کوتاه کافیه."
            )
        ),
        Item(
            3,
            "روز رو ببند",
            "کار اصلی فردا رو روی کاغذ بنویس. میز رو جمع کن. بعد برو.",
            17, 45,
            listOf(
                "هنوز روز رو نبستی. کار اصلی فردا رو روی کاغذ بنویس.",
                "۳۰ ثانیه کافیه. یه خط بنویس فردا اول چی کار می‌کنی.",
                "میز رو جمع کن و کار فردا رو بنویس، بعد برو.",
                "بدون نوشتن کار فردا، صبح دوباره گیج می‌شی. بنویس.",
                "یه خط. همین.",
                "آخرین یادآوری. بنویس و برو."
            )
        )
    )

    fun byId(id: Int): Item? = items.firstOrNull { it.id == id }

    fun fa(s: String): String =
        s.map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("radif", Context.MODE_PRIVATE)

    fun hour(c: Context, i: Item): Int = sp(c).getInt("h${i.id}", i.defH)
    fun minute(c: Context, i: Item): Int = sp(c).getInt("m${i.id}", i.defM)

    fun setTime(c: Context, i: Item, h: Int, m: Int) {
        sp(c).edit().putInt("h${i.id}", h).putInt("m${i.id}", m).apply()
    }

    private fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    /** "" یعنی هنوز جواب نداده، "done" یعنی انجام شد، "skip" یعنی امروز نشد */
    fun status(c: Context, id: Int): String = sp(c).getString("s${id}_${today()}", "") ?: ""

    fun setStatus(c: Context, id: Int, s: String) {
        sp(c).edit().putString("s${id}_${today()}", s).apply()
    }
}
