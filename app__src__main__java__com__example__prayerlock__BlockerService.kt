package com.example.prayerlock

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat

class BlockerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var current: String? = null
    private var lastQuery = System.currentTimeMillis() - 3_600_000
    private var lastLaunch = 0L

    private val tick = object : Runnable {
        override fun run() { check(); handler.postDelayed(this, 800) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.channels(this)
        ServiceCompat.startForeground(
            this, 1, Notifier.service(this),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        handler.removeCallbacks(tick); handler.post(tick)
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun foreground(): String? {
        val um = getSystemService(UsageStatsManager::class.java) ?: return current
        val now = System.currentTimeMillis()
        val events = um.queryEvents(lastQuery, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) current = e.packageName
            else if (e.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND && e.packageName == current) current = null
        }
        lastQuery = now - 2000
        return current
    }

    private fun check() {
        val prayer = PrayerEngine.activeLock(this) ?: return
        val pkg = foreground() ?: return
        if (pkg == packageName || pkg !in Prefs(this).blockedApps) return
        val now = System.currentTimeMillis()
        if (now - lastLaunch < 1500) return
        lastLaunch = now
        startActivity(
            Intent(this, LockActivity::class.java).putExtra("p", prayer.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (Prefs(this).lockEnabled) Scheduler.watchdog(this, 3000)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        val prefs = Prefs(this)
        if (prefs.lockEnabled) {
            if (PrayerEngine.activeLock(this) != null) Notifier.warn(this, "تم إيقاف حارس الصلاة أثناء وقت القفل!")
            Scheduler.watchdog(this, 5000)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
