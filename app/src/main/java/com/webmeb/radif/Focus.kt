package com.webmeb.radif

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context

object Focus {
    const val FOCUS_MIN = 25
    const val BREAK_MIN = 5

    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)

    /** شروع ۲۵ دقیقه تمرکز روی یک تسک */
    fun start(c: Context, t: Task) {
        val cur = Store.focusTask(c)
        if (cur != 0 && cur != t.id) {
            Store.get(c, cur)?.let { cancel(c, it) }
        }
        cancel(c, t)
        Scheduler.stopNags(c, t.id)

        val end = System.currentTimeMillis() + FOCUS_MIN * 60_000L
        Store.setFocus(c, t.id, end)
        Scheduler.setExact(c, Notifier.actionPI(c, t.id, Notifier.ACTION_FOCUS_END, 7), end)
        Notifier.focusRunning(c, t, end)
    }

    /** پایان طبیعی ۲۵ دقیقه (آلارم) */
    fun end(c: Context, t: Task) {
        if (Store.focusTask(c) != t.id) return
        Store.clearFocus(c)
        Store.addFocus(c)
        nm(c).cancel(Notifier.focusNotifId(t.id))
        Notifier.focusEnd(c, t)
    }

    fun breakStart(c: Context, t: Task) {
        nm(c).cancel(t.id)
        val end = System.currentTimeMillis() + BREAK_MIN * 60_000L
        Scheduler.setExact(c, Notifier.actionPI(c, t.id, Notifier.ACTION_BREAK_END, 8), end)
        Notifier.breakRunning(c, t, end)
    }

    fun breakEnd(c: Context, t: Task) {
        nm(c).cancel(Notifier.breakNotifId(t.id))
        Notifier.breakEnd(c, t)
    }

    /** لغو تایمر و استراحت این تسک */
    fun cancel(c: Context, t: Task) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(Notifier.actionPI(c, t.id, Notifier.ACTION_FOCUS_END, 7))
        am.cancel(Notifier.actionPI(c, t.id, Notifier.ACTION_BREAK_END, 8))
        nm(c).cancel(Notifier.focusNotifId(t.id))
        nm(c).cancel(Notifier.breakNotifId(t.id))
        if (Store.focusTask(c) == t.id) Store.clearFocus(c)
    }
}
