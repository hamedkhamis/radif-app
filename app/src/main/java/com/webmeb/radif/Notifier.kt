package com.webmeb.radif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

object Notifier {
    private const val CHANNEL = "radif_push"
    const val ACTION_DONE = "com.webmeb.radif.DONE"
    const val ACTION_SKIP = "com.webmeb.radif.SKIP"

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "یادآوری ردیف", NotificationManager.IMPORTANCE_HIGH)
        ch.enableVibration(true)
        ch.vibrationPattern = longArrayOf(0, 400, 200, 400)
        ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(ch)
    }

    private fun action(c: Context, id: Int, act: String, code: Int): PendingIntent {
        val i = Intent(c, ActionReceiver::class.java).setAction(act).putExtra("id", id)
        return PendingIntent.getBroadcast(
            c, id * 10 + code, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun show(c: Context, item: Item, attempt: Int) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        ensureChannel(nm)

        val text = if (attempt == 0) item.body
        else item.nags[(attempt - 1).coerceIn(0, item.nags.size - 1)]

        val open = PendingIntent.getActivity(
            c, item.id * 10, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val icon = Icon.createWithResource(c, R.drawable.ic_stat)

        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(item.title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(icon, "انجام شد", action(c, item.id, ACTION_DONE, 1)).build())
            .addAction(Notification.Action.Builder(icon, "امروز نشد", action(c, item.id, ACTION_SKIP, 2)).build())
            .build()
        nm.notify(item.id, n)
    }
}
