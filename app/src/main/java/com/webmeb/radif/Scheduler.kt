package com.webmeb.radif

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Scheduler {

    /** آلارم یادآوری تسک: کد = id*10 + attempt (۰ تا ۶) */
    private fun pi(c: Context, id: Int, attempt: Int): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java)
            .putExtra("id", id)
            .putExtra("attempt", attempt)
        return PendingIntent.getBroadcast(
            c, id * 10 + attempt, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun setExact(c: Context, p: PendingIntent, at: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    /** نزدیک‌ترین زمان یادآوری بعد از after، یا null */
    fun nextTrigger(t: Task, after: Long): Long? {
        if (t.h < 0) return null
        if (t.rep == 0) {
            if (t.doneDate.isNotEmpty() || t.onceDate.isEmpty()) return null
            val c = Dates.cal(t.onceDate)
            c.set(Calendar.HOUR_OF_DAY, t.h)
            c.set(Calendar.MINUTE, t.m)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            return if (c.timeInMillis > after) c.timeInMillis else null
        }
        for (off in 0..7) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, off)
            c.set(Calendar.HOUR_OF_DAY, t.h)
            c.set(Calendar.MINUTE, t.m)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis <= after) continue
            if (t.allowsDay(c.get(Calendar.DAY_OF_WEEK))) return c.timeInMillis
        }
        return null
    }

    fun schedule(c: Context, id: Int, at: Long, attempt: Int) {
        setExact(c, pi(c, id, attempt), at)
    }

    /** فقط آلارم اصلی (نه تکرارهای در حال انجام) رو دوباره می‌ذاره */
    fun scheduleBase(c: Context, t: Task) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(pi(c, t.id, 0))
        val at = nextTrigger(t, System.currentTimeMillis())
        if (at != null) schedule(c, t.id, at, 0)
    }

    fun rescheduleAll(c: Context) {
        for (t in Store.tasks(c)) scheduleBase(c, t)
    }

    fun cancelAll(c: Context, id: Int) {
        val am = c.getSystemService(AlarmManager::class.java)
        for (a in 0..6) am.cancel(pi(c, id, a))
        c.getSystemService(NotificationManager::class.java).cancel(id)
    }

    /** تکرارهای امروز لغو می‌شن و نوتیف بسته می‌شه */
    fun stopNags(c: Context, id: Int) {
        val am = c.getSystemService(AlarmManager::class.java)
        for (a in 1..6) am.cancel(pi(c, id, a))
        c.getSystemService(NotificationManager::class.java).cancel(id)
    }
}
