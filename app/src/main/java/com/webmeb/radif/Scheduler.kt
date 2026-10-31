package com.webmeb.radif

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Scheduler {

    private fun pi(c: Context, id: Int, attempt: Int): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java)
            .putExtra("id", id)
            .putExtra("attempt", attempt)
        return PendingIntent.getBroadcast(
            c, id * 100 + attempt, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** نزدیک‌ترین زمان آینده در روزهای شنبه تا چهارشنبه */
    fun nextBase(c: Context, item: Item): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, Prefs.hour(c, item))
        cal.set(Calendar.MINUTE, Prefs.minute(c, item))
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        while (!Model.WORKDAYS.contains(cal.get(Calendar.DAY_OF_WEEK))) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun schedule(c: Context, id: Int, at: Long, attempt: Int) {
        val am = c.getSystemService(AlarmManager::class.java)
        val p = pi(c, id, attempt)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    fun scheduleAll(c: Context) {
        for (item in Model.items) schedule(c, item.id, nextBase(c, item), 0)
    }

    /** وقتی انجام شد یا رد شد، همه تکرارهای امروز لغو می‌شن */
    fun stopNags(c: Context, id: Int) {
        val am = c.getSystemService(AlarmManager::class.java)
        for (a in 1..Model.MAX_NAGS) am.cancel(pi(c, id, a))
        c.getSystemService(NotificationManager::class.java).cancel(id)
    }
}
