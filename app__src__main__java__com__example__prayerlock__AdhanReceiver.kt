package com.example.prayerlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AdhanReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == "ADHAN") {
            val p = runCatching { Prayer.valueOf(i.getStringExtra("p") ?: "") }.getOrNull()
            if (p != null) Notifier.adhan(c, p)
        }
        Scheduler.ensureService(c)
        Scheduler.scheduleNext(c)
    }
}
