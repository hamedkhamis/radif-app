package com.webmeb.radif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", 0)
        val attempt = i.getIntExtra("attempt", 0)
        val item = Model.byId(id) ?: return

        // آلارم فردا رو همون لحظه بذار
        if (attempt == 0) Scheduler.schedule(c, id, Scheduler.nextBase(c, item), 0)

        // اگه امروز جواب داده، دیگه مزاحم نشو
        if (Prefs.status(c, id).isNotEmpty()) return

        Notifier.show(c, item, attempt)

        if (attempt < Model.MAX_NAGS) {
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
        if (Model.byId(id) == null) return
        when (i.action) {
            Notifier.ACTION_DONE -> {
                Prefs.setStatus(c, id, "done")
                Toast.makeText(c, "ثبت شد. آفرین.", Toast.LENGTH_SHORT).show()
            }
            Notifier.ACTION_SKIP -> {
                Prefs.setStatus(c, id, "skip")
                Toast.makeText(c, "ثبت شد. فردا دوباره.", Toast.LENGTH_SHORT).show()
            }
            else -> return
        }
        Scheduler.stopNags(c, id)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Scheduler.scheduleAll(c)
    }
}
