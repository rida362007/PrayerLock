package com.example.prayerlock

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List as ListIcon
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Qibla
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.channels(this)
        setContent { AppTheme { MainApp() } }
    }

    override fun onResume() {
        super.onResume()
        PrayerEngine.refreshLocation(this)
        Scheduler.scheduleNext(this)
        Scheduler.ensureService(this)
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1B6B4A), primaryContainer = Color(0xFFD8F0E3),
            secondary = Color(0xFFB8860B), background = Color(0xFFF8FAF8)
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}

@Composable
fun MainApp() {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "الرئيسية" to Icons.Filled.Home, "التطبيقات" to Icons.Filled.Lock,
        "السجل" to Icons.Filled.ListIcon, "القبلة" to Icons.Filled.LocationOn,
        "الإعدادات" to Icons.Filled.Settings
    )
    Scaffold(bottomBar = {
        NavigationBar {
            tabs.forEachIndexed { i, (name, icon) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i },
                    icon = { Icon(icon, name) }, label = { Text(name, fontSize = 11.sp) })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen { tab = 5 }
                1 -> AppsScreen()
                2 -> StatsScreen()
                3 -> QiblaScreen()
                4 -> SettingsScreen()
                else -> AdhkarScreen { tab = 0 }
            }
        }
    }
}

// ---------- الصلاحيات ----------
fun hasUsage(ctx: Context): Boolean {
    val a = ctx.getSystemService(AppOpsManager::class.java)
    @Suppress("DEPRECATION")
    val m = if (Build.VERSION.SDK_INT >= 29)
        a.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
    else a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
    return m == AppOpsManager.MODE_ALLOWED
}

fun hasNotif(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

fun hasLocation(ctx: Context) =
    ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
fun PermRow(text: String, button: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), fontSize = 13.sp)
            Button(onClick = onClick) { Text(button, fontSize = 12.sp) }
        }
    }
}

// ---------- الرئيسية ----------
@Composable
fun HomeScreen(onAdhkar: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var refresh by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(o); onDispose { lifecycle.removeObserver(o) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        PrayerEngine.refreshLocation(ctx); refresh++
    }
    val r = refresh // لإعادة التركيب عند العودة من الإعدادات
    val times = PrayerEngine.times(ctx)
    val next = PrayerEngine.next(ctx)
    val locked = PrayerEngine.activeLock(ctx)
    val diff = ((next.second.time - now) / 1000).coerceAtLeast(0)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("الصلاة القادمة: ${next.first.ar}", color = Color.White, fontSize = 18.sp)
                    Text("%02d:%02d:%02d".format(diff / 3600, (diff / 60) % 60, diff % 60),
                        color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text(prefs.cityName, color = Color(0xCCFFFFFF), fontSize = 13.sp)
                }
            }
        }
        if (locked != null) item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                Text("🔒 القفل مفعّل الآن لصلاة ${locked.ar}", Modifier.padding(14.dp), color = Color(0xFFB71C1C))
            }
        }
        if (!hasUsage(ctx)) item {
            PermRow("مطلوب: صلاحية الوصول لاستخدام التطبيقات لمعرفة التطبيق المفتوح", "منح") {
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        if (!Settings.canDrawOverlays(ctx)) item {
            PermRow("مطلوب: الظهور فوق التطبيقات لعرض شاشة القفل", "منح") {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }
        }
        if (!hasNotif(ctx)) item {
            PermRow("مطلوب: الإشعارات للأذان والتنبيهات", "منح") {
                if (Build.VERSION.SDK_INT >= 33) launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
        if (!hasLocation(ctx) && prefs.autoLocation) item {
            PermRow("مطلوب: الموقع لحساب أوقات الصلاة تلقائياً", "منح") {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }
        }
        if (prefs.blockedApps.isEmpty()) item {
            PermRow("لم تحدد أي تطبيق للقفل بعد. اذهب إلى تبويب «التطبيقات».", "حسناً") {}
        }
        times.forEach { (p, t) ->
            item(key = p.name) {
                val prayed = prefs.isPrayed(dayKey(), p)
                Card(colors = CardDefaults.cardColors(
                    containerColor = if (p == next.first) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(p.ar, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(fmt(t), fontSize = 18.sp)
                        Checkbox(checked = prayed, enabled = now >= t.time && locked != p,
                            onCheckedChange = { prefs.setPrayed(dayKey(), p, it); refresh++ })
                    }
                }
            }
            if (p == Prayer.FAJR) item(key = "sunrise") {
                Text("الشروق  ${fmt(PrayerEngine.sunrise(ctx))}", Modifier.padding(horizontal = 16.dp),
                    color = Color.Gray, fontSize = 14.sp)
            }
        }
        item { OutlinedButton(onClick = onAdhkar, Modifier.fillMaxWidth()) { Text("📿 أذكار بعد الصلاة") } }
    }
}

// ---------- التطبيقات ----------
data class AppInfo(val label: String, val pkg: String)

@Composable
fun AppsScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var selected by remember { mutableStateOf(prefs.blockedApps) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) {
            val pm = ctx.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(i, 0)
                .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.pkg != ctx.packageName }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(12.dp),
            label = { Text("ابحث عن تطبيق") }, singleLine = true)
        Text("التطبيقات المقفلة وقت الصلاة: ${selected.size}", Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        LazyColumn {
            items(apps.filter { it.label.contains(q, true) }, key = { it.pkg }) { a ->
                val icon = remember(a.pkg) {
                    runCatching { ctx.packageManager.getApplicationIcon(a.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                }
                Row(Modifier.fillMaxWidth().clickable {
                    selected = if (a.pkg in selected) selected - a.pkg else selected + a.pkg
                    prefs.blockedApps = selected
                }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) Image(icon, null, Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(a.label, Modifier.weight(1f))
                    Checkbox(checked = a.pkg in selected, onCheckedChange = null)
                }
            }
        }
    }
}

// ---------- السجل والإحصائيات ----------
@Composable
fun StatsScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val now = Date()
    val days = (0..6).map { n -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -n) }.time }
    val total = days.sumOf { d -> Prayer.values().count { prefs.isPrayed(dayKey(d), it) } }
    val fmtDay = SimpleDateFormat("EEEE d/M", Locale("ar"))
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("صلوات آخر 7 أيام: $total / 35", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { total / 35f }, Modifier.fillMaxWidth())
                }
            }
        }
        items(days) { d ->
            val times = PrayerEngine.times(ctx, d)
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text(fmtDay.format(d), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Prayer.values().forEach { p ->
                            val done = prefs.isPrayed(dayKey(d), p)
                            val future = times[p]?.after(now) == true
                            Box(Modifier.weight(1f).background(
                                when { done -> Color(0xFF1B6B4A); future -> Color(0xFFE0E0E0); else -> Color(0xFFE57373) },
                                RoundedCornerShape(8.dp)).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(p.ar, fontSize = 11.sp, color = if (future) Color.DarkGray else Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- القبلة ----------
@Composable
fun QiblaScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val qibla = remember { Qibla(Coordinates(prefs.lat, prefs.lon)).direction.toFloat() }
    var azimuth by remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val s = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (s == null) hasSensor = false
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val m = FloatArray(9); val o = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(m, e.values)
                SensorManager.getOrientation(m, o)
                azimuth = ((Math.toDegrees(o[0].toDouble()) + 360) % 360).toFloat()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (s != null) sm.registerListener(l, s, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("اتجاه القبلة", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("${qibla.toInt()}° من الشمال الحقيقي — ${prefs.cityName}", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        Canvas(Modifier.size(260.dp)) {
            val c = center; val r = size.minDimension / 2
            drawCircle(Color(0xFFD8F0E3), r)
            drawCircle(Color(0xFF1B6B4A), r, style = Stroke(6f))
            rotate(qibla - azimuth, c) {
                val path = Path().apply {
                    moveTo(c.x, c.y - r * 0.8f); lineTo(c.x - r * 0.18f, c.y + r * 0.2f)
                    lineTo(c.x + r * 0.18f, c.y + r * 0.2f); close()
                }
                drawPath(path, Color(0xFF1B6B4A))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(if (hasSensor) "ضع الهاتف أفقياً، والسهم يشير إلى القبلة" else "الجهاز لا يحتوي مستشعر اتجاه",
            textAlign = TextAlign.Center, color = Color.Gray)
    }
}

// ---------- الإعدادات ----------
@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
        content()
    } }
}

@Composable
fun <T> Picker(label: String, current: String, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) { Text("$label: $current") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (k, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { onPick(k); open = false }) }
        }
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var lock by remember { mutableStateOf(prefs.lockEnabled) }
    var mins by remember { mutableIntStateOf(prefs.lockMinutes) }
    var auto by remember { mutableStateOf(prefs.autoLocation) }
    var city by remember { mutableStateOf(prefs.cityName) }
    var method by remember { mutableStateOf(prefs.method) }
    var mode by remember { mutableIntStateOf(prefs.adhanMode) }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        PrayerEngine.refreshLocation(ctx); city = prefs.cityName; Scheduler.scheduleNext(ctx)
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard("القفل") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تفعيل قفل التطبيقات وقت الصلاة", Modifier.weight(1f))
                    Switch(lock, {
                        lock = it; prefs.lockEnabled = it
                        if (it) Scheduler.ensureService(ctx) else ctx.stopService(Intent(ctx, BlockerService::class.java))
                    })
                }
                Text("مدة القفل إن لم تضغط «صليت»: $mins دقيقة")
                Slider(mins.toFloat(), { mins = it.toInt() }, valueRange = 5f..120f, steps = 22,
                    onValueChangeFinished = { prefs.lockMinutes = mins })
            }
        }
        item {
            SectionCard("الأذان والإشعارات") {
                listOf("صوت الأذان" to 0, "اهتزاز فقط" to 1, "صامت" to 2).forEach { (n, v) ->
                    Row(Modifier.fillMaxWidth().clickable { mode = v; prefs.adhanMode = v },
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(mode == v, { mode = v; prefs.adhanMode = v }); Text(n)
                    }
                }
                Text("لاستخدام صوت أذان حقيقي: ضع ملف adhan.mp3 داخل app/src/main/res/raw", fontSize = 11.sp, color = Color.Gray)
            }
        }
        item {
            SectionCard("الموقع وطريقة الحساب") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تحديد الموقع تلقائياً", Modifier.weight(1f))
                    Switch(auto, {
                        auto = it; prefs.autoLocation = it
                        if (it) locLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                    })
                }
                if (auto) {
                    Text("الموقع الحالي: $city", color = Color.Gray)
                    OutlinedButton(onClick = {
                        locLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                        PrayerEngine.refreshLocation(ctx); city = prefs.cityName
                    }) { Text("تحديث الموقع") }
                } else {
                    Picker("المدينة", city, CITIES.map { it to it.first }) { c ->
                        prefs.lat = c.second; prefs.lon = c.third; prefs.cityName = c.first
                        city = c.first; Scheduler.scheduleNext(ctx)
                    }
                }
                Picker("طريقة الحساب", METHODS.firstOrNull { it.first == method }?.second ?: "", METHODS) {
                    method = it; prefs.method = it; Scheduler.scheduleNext(ctx)
                }
            }
        }
    }
}

// ---------- الأذكار ----------
@Composable
fun AdhkarScreen(onBack: () -> Unit) {
    val items = listOf(
        "أستغفر الله" to 3,
        "اللهم أنت السلام ومنك السلام، تباركت يا ذا الجلال والإكرام" to 1,
        "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير" to 1,
        "سبحان الله" to 33, "الحمد لله" to 33, "الله أكبر" to 33,
        "آية الكرسي (البقرة: 255)" to 1,
        "سورة الإخلاص، والفلق، والناس (ثلاثاً بعد الفجر والمغرب)" to 1
    )
    val left = remember { mutableStateListOf(*items.map { it.second }.toTypedArray()) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TextButton(onClick = onBack) { Text("← رجوع") } }
        items(items.size) { i ->
            val done = left[i] == 0
            Card(Modifier.fillMaxWidth().clickable { if (left[i] > 0) left[i] = left[i] - 1 },
                colors = CardDefaults.cardColors(containerColor = if (done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(items[i].first, Modifier.weight(1f), fontSize = 17.sp)
                    Text(if (done) "✓" else "${left[i]}", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { Text("اضغط على الذكر لإنقاص العدّاد", color = Color.Gray, fontSize = 12.sp) }
    }
}
