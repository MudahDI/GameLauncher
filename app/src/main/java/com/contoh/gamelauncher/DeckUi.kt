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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
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
    var Accent by mutableStateOf(Color(0xFF1A9FFF))
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
private fun HeaderGlyph(kind: String, color: Color, isLandscape: Boolean = false) {
    Canvas(Modifier.size(22.dp)) {
        val sw = 2.1.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        when (kind) {
            "history" -> {
                drawCircle(color, size.minDimension * 0.39f, style = Stroke(sw))
                drawLine(color, Offset(cx, cy), Offset(cx, cy - size.height * 0.22f), sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(color, Offset(cx, cy), Offset(cx + size.width * 0.19f, cy + size.height * 0.15f), sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            "rotate" -> {
                val w = if (isLandscape) size.width * 0.68f else size.width * 0.43f
                val h = if (isLandscape) size.height * 0.43f else size.height * 0.68f
                val left = (size.width - w) / 2f
                val top = (size.height - h) / 2f
                drawRoundRect(color, Offset(left, top), Size(w, h), CornerRadius(2.5.dp.toPx()), style = Stroke(sw))
                drawLine(color, Offset(cx - w * 0.16f, top + h * 0.82f), Offset(cx + w * 0.16f, top + h * 0.82f), sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            else -> {
                val outer = androidx.compose.ui.graphics.Path()
                val teeth = 24
                for (i in 0 until teeth) {
                    val angle = -Math.PI / 2.0 + i * Math.PI * 2.0 / teeth
                    val radius = if (i % 3 == 0 || i % 3 == 1) size.minDimension * 0.46f else size.minDimension * 0.37f
                    val x = cx + kotlin.math.cos(angle).toFloat() * radius
                    val y = cy + kotlin.math.sin(angle).toFloat() * radius
                    if (i == 0) outer.moveTo(x, y) else outer.lineTo(x, y)
                }
                outer.close()
                drawPath(outer, color, style = Stroke(sw, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawCircle(color, size.minDimension * 0.19f, style = Stroke(sw))
            }
        }
    }
}
@Composable
fun HeaderBar(onHistory: () -> Unit, onRotate: () -> Unit, rotationMode: Int, isLandscape: Boolean, onSettings: () -> Unit) {
    val clock by rememberClock()
    val battery by rememberBatteryPercent()
    Row(
        Modifier.fillMaxWidth()
            .height(48.dp)
            .background(Color.Black.copy(alpha = 0.30f))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("MDI", color = Deck.Accent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.width(9.dp))
        Text("LAUNCHER", color = Deck.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        if (battery >= 0) {
            Text("$battery%", color = Deck.TextDim, fontSize = 12.sp)
            Spacer(Modifier.width(12.dp))
        }
        Text(clock, color = Deck.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(9.dp))
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(Deck.Panel).clickable(onClick = onHistory), contentAlignment = Alignment.Center) {
            HeaderGlyph("history", Deck.Text)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(Deck.Panel).clickable(onClick = onRotate), contentAlignment = Alignment.Center) {
            HeaderGlyph("rotate", Deck.Accent, isLandscape)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(Deck.Panel).clickable(onClick = onSettings), contentAlignment = Alignment.Center) {
            HeaderGlyph("settings", Deck.Text)
        }
    }
}

@Composable
fun BottomNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf("BERANDA", "KOLEKSI", "CARI")
    Row(
        Modifier.fillMaxWidth()
            .height(48.dp)
            .background(Color(0xF20B1017))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, label ->
            val active = index == selected
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Deck.Panel else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    label,
                    color = if (active) Color.White else Deck.TextDim,
                    fontSize = 10.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier.width(if (active) 20.dp else 0.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Deck.Accent)
                )
            }
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
fun PlayButton(text: String, compact: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .height(if (compact) 38.dp else 44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.horizontalGradient(listOf(Deck.PlayLight, Deck.PlayDark)))
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 14.dp else 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = if (compact) 12.sp else 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = if (compact) 0.5.sp else 1.sp
        )
    }
}

@Composable
fun DeckButton(label: String, compact: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(if (compact) 38.dp else 44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Deck.PanelLight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = if (compact) 16.sp else 18.sp)
    }
}
