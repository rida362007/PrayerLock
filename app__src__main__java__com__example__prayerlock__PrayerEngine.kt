package com.example.prayerlock

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.util.*

object PrayerEngine {
    private var cacheKey = ""
    private var cache: PrayerTimes? = null

    private fun pt(ctx: Context, date: Date): PrayerTimes {
        val p = Prefs(ctx)
        val key = "${dayKey(date)}|${p.lat}|${p.lon}|${p.method}"
        if (key == cacheKey && cache != null) return cache!!
        val res = PrayerTimes(
            Coordinates(p.lat, p.lon),
            DateComponents.from(date),
            CalculationMethod.valueOf(p.method).parameters
        )
        cache = res; cacheKey = key
        return res
    }

    fun times(ctx: Context, date: Date = Date()): Map<Prayer, Date> {
        val t = pt(ctx, date)
        return linkedMapOf(
            Prayer.FAJR to t.fajr, Prayer.DHUHR to t.dhuhr, Prayer.ASR to t.asr,
            Prayer.MAGHRIB to t.maghrib, Prayer.ISHA to t.isha
        )
    }

    fun sunrise(ctx: Context, date: Date = Date()): Date = pt(ctx, date).sunrise

    fun next(ctx: Context): Pair<Prayer, Date> {
        val now = Date()
        times(ctx).entries.firstOrNull { it.value.after(now) }?.let { return it.key to it.value }
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time
        return Prayer.FAJR to times(ctx, tomorrow)[Prayer.FAJR]!!
    }

    /** الصلاة التي القفل مفعّل لها الآن (دخل وقتها ولم تُسجَّل ولم تنتهِ مدة القفل) */
    fun activeLock(ctx: Context): Prayer? {
        val prefs = Prefs(ctx)
        if (!prefs.lockEnabled) return null
        val now = System.currentTimeMillis()
        val key = dayKey()
        for ((p, t) in times(ctx)) {
            val end = t.time + prefs.lockMinutes * 60_000L
            if (now >= t.time && now < end && !prefs.isPrayed(key, p)) return p
        }
        return null
    }

    @SuppressLint("MissingPermission")
    fun refreshLocation(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.autoLocation) return
        val granted = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return
        fun save(l: Location) {
            prefs.lat = l.latitude; prefs.lon = l.longitude; prefs.cityName = "موقعك الحالي"
        }
        try {
            val best = lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
            if (best != null) { save(best); return }
            val prov = lm.getProviders(true).firstOrNull() ?: return
            LocationManagerCompat.getCurrentLocation(lm, prov, null, ContextCompat.getMainExecutor(ctx)) { l ->
                if (l != null) { save(l); Scheduler.scheduleNext(ctx) }
            }
        } catch (_: Exception) { }
    }
}
