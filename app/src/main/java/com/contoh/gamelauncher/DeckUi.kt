package com.mdh.gamelauncher

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Palet warna bergaya antarmuka Steam Deck. */
object Deck {
    val Bg = Color(0xFF0E141B)
    val Panel = Color(0xFF1B2838)
    val PanelLight = Color(0xFF2A475E)
    val Accent = Color(0xFF1A9FFF)
    val PlayLight = Color(0xFF75B022)
    val PlayDark = Color(0xFF588A1B)
    val Text = Color(0xFFDCDEDF)
    val TextDim = Color(0xFF8B929A)
}

@Composable
fun rememberClock(): State<String> = produceState(initialValue = "") {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    while (true) {
        value = fmt.format(Date())
        delay(15_000)
    }
}

@Composable
fun rememberBatteryPercent(): State<Int> {
    val ctx = LocalContext.current
    return produceState(initialValue = -1) {
        while (true) {
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            value = if (level >= 0 && scale > 0) level * 100 / scale else -1
            delay(30_000)
        }
    }
}

@Composable
fun TopBar(selected: Int, onSelect: (Int) -> Unit) {
    val clock by rememberClock()
    val battery by rememberBatteryPercent()
    val tabs = listOf("BERANDA", "KOLEKSI", "CARI", "ATUR")

    BoxWithConstraints(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.35f))) {
        val wide = maxWidth >= 700.dp
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (wide) {
                Text(
                    "MDI LAUNCHER",
                    color = Deck.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = 8.dp, end = 24.dp)
                )
            }
            tabs.forEachIndexed { i, label ->
                val on = i == selected
                Column(
                    Modifier.clickable { onSelect(i) }.padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label,
                        color = if (on) Color.White else Deck.TextDim,
                        fontSize = 12.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .width(24.dp)
                            .height(2.dp)
                            .background(if (on) Deck.Accent else Color.Transparent)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (wide && battery >= 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BatteryGlyph(battery)
                    Text("$battery%", color = Deck.Text, fontSize = 13.sp)
                }
                Spacer(Modifier.width(16.dp))
            }
            Text(clock, color = Deck.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** Ikon baterai vektor agar tampil konsisten tanpa emoji. */
@Composable
private fun BatteryGlyph(percent: Int) {
    Canvas(Modifier.size(width = 20.dp, height = 12.dp)) {
        val stroke = 1.35.dp.toPx()
        val radius = 2.dp.toPx()
        val bodyWidth = size.width * 0.84f
        val inset = stroke * 1.7f
        drawRoundRect(
            color = Deck.Text,
            topLeft = Offset(0f, stroke),
            size = Size(bodyWidth, size.height - stroke * 2),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = Deck.Text,
            topLeft = Offset(bodyWidth + stroke * 0.7f, size.height * 0.31f),
            size = Size(size.width - bodyWidth - stroke * 0.7f, size.height * 0.38f),
            cornerRadius = CornerRadius(radius * 0.5f)
        )
        val fillWidth = (bodyWidth - inset * 2) * percent.coerceIn(0, 100) / 100f
        if (fillWidth > 0f) {
            drawRoundRect(
                color = if (percent <= 15) Color(0xFFFF6B6B) else Deck.Accent,
                topLeft = Offset(inset, inset),
                size = Size(fillWidth, size.height - inset * 2),
                cornerRadius = CornerRadius(radius * 0.45f)
            )
        }
    }
}

@Composable
fun PlayButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.horizontalGradient(listOf(Deck.PlayLight, Deck.PlayDark)))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun DeckButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Deck.PanelLight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 18.sp)
    }
}
