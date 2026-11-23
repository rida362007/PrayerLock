package com.example.prayerlock

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

enum class Prayer(val ar: String) {
    FAJR("الفجر"), DHUHR("الظهر"), ASR("العصر"), MAGHRIB("المغرب"), ISHA("العشاء")
}

val CITIES = listOf(
    Triple("بسكرة", 34.85, 5.73), Triple("الجزائر العاصمة", 36.75, 3.06),
    Triple("وهران", 35.70, -0.63), Triple("قسنطينة", 36.365, 6.615),
    Triple("عنابة", 36.90, 7.77), Triple("سطيف", 36.19, 5.41),
    Triple("باتنة", 35.555, 6.174), Triple("ورقلة", 31.95, 5.32),
    Triple("غرداية", 32.49, 3.67), Triple("تلمسان", 34.88, -1.31),
    Triple("تونس", 36.81, 10.18), Triple("طرابلس", 32.89, 13.19),
    Triple("القاهرة", 30.04, 31.24), Triple("الدار البيضاء", 33.57, -7.59),
    Triple("مكة المكرمة", 21.42, 39.83), Triple("المدينة المنورة", 24.47, 39.61),
    Triple("الرياض", 24.71, 46.68), Triple("دبي", 25.20, 55.27),
    Triple("عمّان", 31.95, 35.93), Triple("دمشق", 33.51, 36.29),
    Triple("بغداد", 33.31, 44.36), Triple("إسطنبول", 41.01, 28.98),
    Triple("باريس", 48.86, 2.35)
)

val METHODS = listOf(
    "MUSLIM_WORLD_LEAGUE" to "رابطة العالم الإسلامي",
    "EGYPTIAN" to "الهيئة المصرية العامة للمساحة",
    "UMM_AL_QURA" to "أم القرى (مكة)",
    "KARACHI" to "جامعة العلوم الإسلامية - كراتشي",
    "DUBAI" to "دبي",
    "QATAR" to "قطر",
    "KUWAIT" to "الكويت",
    "NORTH_AMERICA" to "أمريكا الشمالية (ISNA)"
)

fun dayKey(d: Date = Date()): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)
fun fmt(d: Date): String = SimpleDateFormat("HH:mm", Locale.US).format(d)

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var blockedApps: Set<String>
        get() = sp.getStringSet("blocked", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("blocked", HashSet(v)).apply() }
    var lockEnabled: Boolean
        get() = sp.getBoolean("lock", true)
        set(v) { sp.edit().putBoolean("lock", v).apply() }
    var lockMinutes: Int
        get() = sp.getInt("mins", 30)
        set(v) { sp.edit().putInt("mins", v).apply() }
    var autoLocation: Boolean
        get() = sp.getBoolean("auto", true)
        set(v) { sp.edit().putBoolean("auto", v).apply() }
    var lat: Double
        get() = (sp.getString("lat", "34.85") ?: "34.85").toDouble()
        set(v) { sp.edit().putString("lat", v.toString()).apply() }
    var lon: Double
        get() = (sp.getString("lon", "5.73") ?: "5.73").toDouble()
        set(v) { sp.edit().putString("lon", v.toString()).apply() }
    var cityName: String
        get() = sp.getString("city", "بسكرة") ?: "بسكرة"
        set(v) { sp.edit().putString("city", v).apply() }
    var method: String
        get() = sp.getString("method", "MUSLIM_WORLD_LEAGUE") ?: "MUSLIM_WORLD_LEAGUE"
        set(v) { sp.edit().putString("method", v).apply() }
    /** 0 = صوت الأذان، 1 = اهتزاز فقط، 2 = صامت */
    var adhanMode: Int
        get() = sp.getInt("adhan", 0)
        set(v) { sp.edit().putInt("adhan", v).apply() }

    fun isPrayed(day: String, p: Prayer): Boolean =
        (sp.getStringSet("prayed", emptySet()) ?: emptySet()).contains("$day|${p.name}")

    fun setPrayed(day: String, p: Prayer, v: Boolean) {
        val s = HashSet(sp.getStringSet("prayed", emptySet()) ?: emptySet())
        if (v) s.add("$day|${p.name}") else s.remove("$day|${p.name}")
        sp.edit().putStringSet("prayed", s).apply()
    }
}
