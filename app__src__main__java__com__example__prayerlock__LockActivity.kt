package com.example.prayerlock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        val prayer = runCatching { Prayer.valueOf(intent.getStringExtra("p") ?: "") }.getOrDefault(Prayer.FAJR)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            AppTheme {
                LockScreen(prayer,
                    onPrayed = { Prefs(this).setPrayed(dayKey(), prayer, true); finish() },
                    onNotYet = { goHome() })
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

@Composable
fun LockScreen(prayer: Prayer, onPrayed: () -> Unit, onNotYet: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var left by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = PrayerEngine.times(ctx)[prayer]?.time ?: System.currentTimeMillis()
        val end = start + Prefs(ctx).lockMinutes * 60_000L
        while (true) { left = (end - System.currentTimeMillis()).coerceAtLeast(0); delay(1000) }
    }
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🕌", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("حان وقت صلاة ${prayer.ar}", fontSize = 26.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("التطبيق مقفل حتى تصلي", fontSize = 16.sp, color = Color.Gray)
        Text("ينتهي القفل تلقائياً بعد %02d:%02d".format(left / 60000, (left / 1000) % 60), fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(36.dp))
        if (step == 0) {
            Text("هل صليت؟", fontSize = 22.sp)
            Spacer(Modifier.height(20.dp))
            Button(onClick = { step = 1 }, Modifier.fillMaxWidth().height(52.dp)) { Text("نعم، صليت") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onNotYet, Modifier.fillMaxWidth().height(52.dp)) { Text("لا، سأصلي الآن") }
        } else {
            Text("احلف بالله أنك صليت", fontSize = 22.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("﴿وَلَا تَنقُضُوا الْأَيْمَانَ بَعْدَ تَوْكِيدِهَا﴾", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onPrayed, Modifier.fillMaxWidth().height(52.dp)) { Text("أحلف بالله أنني صليت") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onNotYet, Modifier.fillMaxWidth().height(52.dp)) { Text("لم أصلِّ بعد") }
        }
    }
}
