package com.webmeb.radif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", 0)
        val attempt = i.getIntExtra("attempt", 0)
        val t = Store.get(c, id) ?: return

        // آلارم بعدی این تسک رو همون لحظه بذار
        if (attempt == 0 && t.rep != 0) {
            val next = Scheduler.nextTrigger(t, System.currentTimeMillis() + 60_000L)
            if (next != null) Scheduler.schedule(c, id, next, 0)
        }

        // امروز جواب داده یا امروز موعدش نیست
        if (Store.status(c, t).isNotEmpty()) return
        if (!Store.isDueToday(t)) return
        // وسط تایمر تمرکز همین تسک مزاحم نشو
        if (Store.focusTask(c) == id) return

        Notifier.show(c, t, attempt)

        if (attempt < t.maxNags) {
            Scheduler.schedule(
                c, id,
                System.currentTimeMillis() + Model.NAG_MIN * 60_000L,
                attempt + 1
            )
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", 0)
        val t = Store.get(c, id) ?: return
        when (i.action) {
            Notifier.ACTION_DONE -> {
                Store.setDone(c, t, true)
                Toast.makeText(c, "ثبت شد. آفرین.", Toast.LENGTH_SHORT).show()
            }
            Notifier.ACTION_SKIP -> {
                Store.skip(c, t)
                Toast.makeText(c, "ثبت شد. دوباره سراغش می‌ریم.", Toast.LENGTH_SHORT).show()
            }
            Notifier.ACTION_FOCUS_START -> Focus.start(c, t)
            Notifier.ACTION_FOCUS_CANCEL -> Focus.cancel(c, t)
            Notifier.ACTION_FOCUS_END -> Focus.end(c, t)
            Notifier.ACTION_BREAK_START -> Focus.breakStart(c, t)
            Notifier.ACTION_BREAK_END -> Focus.breakEnd(c, t)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Scheduler.rescheduleAll(c)
    }
}
