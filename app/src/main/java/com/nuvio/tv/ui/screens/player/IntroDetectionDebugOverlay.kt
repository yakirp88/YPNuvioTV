package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.core.diagnostics.DetectionSnapshot
import kotlinx.coroutines.delay

/** Passive in-player panel: no popup, focus capture, playback pause or automatic upload. */
@Composable
internal fun IntroDetectionDebugOverlay(snapshot: DetectionSnapshot, status: String, probeStatus: String, modifier: Modifier = Modifier) {
    val language = LocalConfiguration.current.locales[0].language
    val he = language == "he" || language == "iw"
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val rows = snapshot.entries.filter { it.event !in setOf("settings", "session", "status") }.takeLast(6)
    Column(modifier.widthIn(max = 440.dp).background(Color.Black.copy(alpha = .86f), RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(if (he) "דיבאג זיהוי מקטעים YP" else "YP segment detection debug", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(IntroDebugText.status(status, he), color = Color(0xFF90CAF9), fontSize = 14.sp)
        if (probeStatus != "not_requested") Text(IntroDebugText.status(probeStatus, he), color = Color(0xFFFFE082), fontSize = 13.sp)
        if (rows.isEmpty()) Text(if (he) "טרם נרשמו פעולות בהרצה זו" else "No actions recorded in this playback yet", color = Color.LightGray, fontSize = 13.sp)
        rows.forEach { row ->
            Text("${row.elapsedMs / 1000}s · ${IntroDebugText.entry(row, he)}", color = Color.White, fontSize = 13.sp, lineHeight = 17.sp)
        }
        val last = snapshot.entries.lastOrNull()
        if (last != null) {
            val age = ((now - snapshot.startedAt - last.elapsedMs) / 1000).coerceAtLeast(0)
            Text(if (he) "העדכון האחרון לפני $age שניות" else "Last update $age seconds ago", color = Color.LightGray, fontSize = 12.sp)
        }
        Text(if (he) "הסתרה: כפתור הדיבאג בפקדי הנגן. בדיקת הענן שולחת בקשת AI קטנה ועשויה לצרוך מכסה או חיוב API. המידע נשמר מקומית בלבד." else "Hide with the player debug button. Cloud test sends a small AI request and may use API quota or incur a charge. Diagnostics stay local.", color = Color.LightGray, fontSize = 11.sp, lineHeight = 14.sp)
    }
}
