package com.example.prayerlock

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Scheduler {
    fun ensureService(ctx: Context) {
        if (!Prefs(ctx).lockEnabled) return
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, BlockerService::class.java))
        } catch (_: Exception) { }
    }

    private fun pending(ctx: Context, code: Int, action: String, prayer: String? = null): PendingIntent {
        val i = Intent(ctx, AdhanReceiver::class.java).setAction(action)
        if (prayer != null) i.putExtra("p", prayer)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setAlarm(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun scheduleNext(ctx: Context) {
        val (p, t) = PrayerEngine.next(ctx)
        setAlarm(ctx, t.time, pending(ctx, 100, "ADHAN", p.name))
    }

    /** إعادة تشغيل الخدمة إن أُوقفت */
    fun watchdog(ctx: Context, delayMs: Long) {
        setAlarm(ctx, System.currentTimeMillis() + delayMs, pending(ctx, 101, "WATCHDOG"))
    }
}

object Notifier {
    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("svc", "حارس الصلاة", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("warn", "تنبيهات الحماية", NotificationManager.IMPORTANCE_HIGH))
        // قناة الأذان بالصوت: ضع ملف adhan.mp3 في res/raw ليُستخدم، وإلا يُستخدم صوت الإشعارات الافتراضي
        val id = ctx.resources.getIdentifier("adhan", "raw", ctx.packageName)
        val sound: Uri = if (id != 0) Uri.parse("android.resource://${ctx.packageName}/$id")
        else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        NotificationChannel("adhan_sound", "الأذان (صوت)", NotificationManager.IMPORTANCE_HIGH).also {
            it.setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            nm.createNotificationChannel(it)
        }
        NotificationChannel("adhan_vibrate", "الأذان (اهتزاز)", NotificationManager.IMPORTANCE_HIGH).also {
            it.setSound(null, null); it.enableVibration(true); nm.createNotificationChannel(it)
        }
        NotificationChannel("adhan_silent", "الأذان (صامت)", NotificationManager.IMPORTANCE_LOW).also {
            it.setSound(null, null); it.enableVibration(false); nm.createNotificationChannel(it)
        }
    }

    fun service(ctx: Context) = NotificationCompat.Builder(ctx, "svc")
        .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
        .setContentTitle("حارس الصلاة يعمل")
        .setContentText("يراقب التطبيقات المحددة وقت الصلاة")
        .setOngoing(true).build()

    private fun notify(ctx: Context, id: Int, n: android.app.Notification) {
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) { }
    }

    fun adhan(ctx: Context, p: Prayer) {
        channels(ctx)
        val ch = when (Prefs(ctx).adhanMode) { 0 -> "adhan_sound"; 1 -> "adhan_vibrate"; else -> "adhan_silent" }
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        notify(ctx, 200, NotificationCompat.Builder(ctx, ch)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("حان الآن وقت صلاة ${p.ar}")
            .setContentText("حيَّ على الصلاة، حيَّ على الفلاح")
            .setContentIntent(open).setAutoCancel(true).build())
    }

    fun warn(ctx: Context, text: String) {
        channels(ctx)
        notify(ctx, 300, NotificationCompat.Builder(ctx, "warn")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("تنبيه: حارس الصلاة")
            .setContentText(text).setAutoCancel(true).build())
    }
}
