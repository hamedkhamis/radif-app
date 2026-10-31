package com.webmeb.radif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

object Notifier {
    private const val CH_PUSH = "radif_push"
    private const val CH_FOCUS = "radif_focus"

    const val ACTION_DONE = "com.webmeb.radif.DONE"
    const val ACTION_SKIP = "com.webmeb.radif.SKIP"
    const val ACTION_FOCUS_START = "com.webmeb.radif.FOCUS_START"
    const val ACTION_FOCUS_CANCEL = "com.webmeb.radif.FOCUS_CANCEL"
    const val ACTION_FOCUS_END = "com.webmeb.radif.FOCUS_END"
    const val ACTION_BREAK_START = "com.webmeb.radif.BREAK_START"
    const val ACTION_BREAK_END = "com.webmeb.radif.BREAK_END"

    fun focusNotifId(id: Int) = 1_000_000 + id
    fun breakNotifId(id: Int) = 2_000_000 + id

    private val NAGS = arrayOf(
        "«%s» هنوز انجام نشده. همین الان شروع کن.",
        "گوشی رو بذار زمین و برو سراغ «%s».",
        "نیاز به حال خوب نیست. فقط ۱۰ دقیقه «%s».",
        "هر دقیقه تأخیر، «%s» رو سنگین‌تر می‌کنه.",
        "یه قدم کوچیک بردار: «%s» رو باز کن.",
        "آخرین یادآوری برای «%s». فقط شروع کن."
    )

    fun text(t: Task, attempt: Int): String {
        if (attempt == 0) {
            return if (t.note.isNotEmpty()) t.note
            else "وقتشه: «${t.title}». ۱۰ دقیقه شروع کن، بعد ادامه بده."
        }
        return NAGS[(attempt - 1).coerceIn(0, NAGS.size - 1)].format(t.title)
    }

    private fun ensureChannels(nm: NotificationManager) {
        if (nm.getNotificationChannel(CH_PUSH) == null) {
            val ch = NotificationChannel(CH_PUSH, "یادآوری ردیف", NotificationManager.IMPORTANCE_HIGH)
            ch.enableVibration(true)
            ch.vibrationPattern = longArrayOf(0, 400, 200, 400)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
        if (nm.getNotificationChannel(CH_FOCUS) == null) {
            val ch = NotificationChannel(CH_FOCUS, "تایمر تمرکز", NotificationManager.IMPORTANCE_LOW)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
    }

    fun actionPI(c: Context, id: Int, action: String, code: Int): PendingIntent {
        val i = Intent(c, ActionReceiver::class.java).setAction(action).putExtra("id", id)
        return PendingIntent.getBroadcast(
            c, id * 10 + code, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun openPI(c: Context, id: Int): PendingIntent = PendingIntent.getActivity(
        c, id * 10, Intent(c, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun act(c: Context, label: String, pi: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_stat), label, pi).build()

    private fun base(c: Context, channel: String, title: String, text: String, id: Int): Notification.Builder =
        Notification.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openPI(c, id))

    private fun nm(c: Context): NotificationManager? {
        val m = c.getSystemService(NotificationManager::class.java)
        if (!m.areNotificationsEnabled()) return null
        ensureChannels(m)
        return m
    }

    /** یادآوری اصلی: قابل کنار زدن نیست و با هر تکرار دوباره هشدار می‌ده */
    fun show(c: Context, t: Task, attempt: Int) {
        val m = nm(c) ?: return
        val n = base(c, CH_PUSH, t.title, text(t, attempt), t.id)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .addAction(act(c, "انجام شد", actionPI(c, t.id, ACTION_DONE, 1)))
            .addAction(act(c, "۲۵ دقیقه تمرکز", actionPI(c, t.id, ACTION_FOCUS_START, 3)))
            .addAction(act(c, "امروز نشد", actionPI(c, t.id, ACTION_SKIP, 2)))
            .build()
        m.notify(t.id, n)
    }

    fun focusRunning(c: Context, t: Task, endAt: Long) {
        val m = nm(c) ?: return
        val n = Notification.Builder(c, CH_FOCUS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("تمرکز: ${t.title}")
            .setContentText("فقط همین یه کار. گوشی رو بذار کنار.")
            .setContentIntent(openPI(c, t.id))
            .setOngoing(true)
            .setUsesChronometer(true)
            .setChronometerCountsDown(true)
            .setWhen(endAt)
            .setShowWhen(true)
            .addAction(act(c, "تموم شد", actionPI(c, t.id, ACTION_DONE, 1)))
            .addAction(act(c, "لغو", actionPI(c, t.id, ACTION_FOCUS_CANCEL, 4)))
            .build()
        m.notify(focusNotifId(t.id), n)
    }

    fun focusEnd(c: Context, t: Task) {
        val m = nm(c) ?: return
        val n = base(c, CH_PUSH, "۲۵ دقیقه تموم شد", "آفرین. «${t.title}» تموم شد یا یه دور دیگه؟", t.id)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(act(c, "انجام شد", actionPI(c, t.id, ACTION_DONE, 1)))
            .addAction(act(c, "۵ دقیقه استراحت", actionPI(c, t.id, ACTION_BREAK_START, 5)))
            .addAction(act(c, "۲۵ دقیقه دیگه", actionPI(c, t.id, ACTION_FOCUS_START, 3)))
            .build()
        m.notify(t.id, n)
    }

    fun breakRunning(c: Context, t: Task, endAt: Long) {
        val m = nm(c) ?: return
        val n = Notification.Builder(c, CH_FOCUS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("استراحت")
            .setContentText("بلند شو، آب بخور. اسکرول نه.")
            .setContentIntent(openPI(c, t.id))
            .setOngoing(true)
            .setUsesChronometer(true)
            .setChronometerCountsDown(true)
            .setWhen(endAt)
            .setShowWhen(true)
            .addAction(act(c, "شروع دوباره", actionPI(c, t.id, ACTION_FOCUS_START, 3)))
            .build()
        m.notify(breakNotifId(t.id), n)
    }

    fun breakEnd(c: Context, t: Task) {
        val m = nm(c) ?: return
        val n = base(c, CH_PUSH, "استراحت تموم شد", "برگرد سر «${t.title}».", t.id)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(act(c, "۲۵ دقیقه تمرکز", actionPI(c, t.id, ACTION_FOCUS_START, 3)))
            .addAction(act(c, "انجام شد", actionPI(c, t.id, ACTION_DONE, 1)))
            .build()
        m.notify(t.id, n)
    }
}
