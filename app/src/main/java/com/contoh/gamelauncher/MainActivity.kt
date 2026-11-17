package com.contoh.gamelauncher

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- Root ----------

@Composable
fun LauncherApp(allApps: List<AppItem>, pad: GamepadInput) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    // 0 = Beranda, 1 = Koleksi, 2 = Cari, 3 = Atur
    var tab by rememberSaveable { mutableStateOf(0) }
    // Simpan package name (bukan AppItem) supaya state bisa bertahan saat proses dibunuh sistem.
    var focusedPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    // Dari tab mana pun, Back kembali ke Beranda dulu.
    BackHandler(enabled = tab != 0) { tab = 0 }

    // Gamepad: L1/R1 pindah tab, X membuka Cari, B kembali ke Beranda.
    // Navigasi dan aksi di dalam tiap layar ditangani layar itu sendiri.
    LaunchedEffect(pad) {
        pad.events.collect { key ->
            when (key) {
                Pad.PrevTab -> { tab = (tab - 1).coerceAtLeast(0) }
                Pad.NextTab -> { tab = (tab + 1).coerceAtMost(3) }
                Pad.Search -> { tab = 2 }
                Pad.Back -> { if (tab != 0) tab = 0 }
                else -> {}
            }
        }
    }

    val visible = allApps.filter { (it.isGame || it.pkg in store.added) && it.pkg !in store.removed }
    val visiblePkgs = visible.map { it.pkg }.toSet()
    val candidates = allApps.filter { it.pkg !in visiblePkgs }
    // Game yang sedang dipilih; kalau belum ada, pakai yang terakhir dimainkan lalu game pertama.
    val focused = visible.find { it.pkg == focusedPkg }
        ?: store.recent.firstNotNullOfOrNull { p -> visible.find { it.pkg == p } }
        ?: visible.firstOrNull()
    val selected = selectedPkg?.let { p -> allApps.find { it.pkg == p } }

    val play: (AppItem) -> Unit = { a ->
        ctx.packageManager.getLaunchIntentForPackage(a.pkg)?.let { launch ->
            // Catat "terakhir dimainkan" hanya kalau app benar-benar berhasil dibuka.
            runCatching { ctx.startActivity(launch) }
                .onSuccess { store.markPlayed(a.pkg) }
        }
    }
    val options: (AppItem) -> Unit = { a -> selectedPkg = a.pkg }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Deck.Bg, Deck.Panel)))
    ) {
        if (focused != null) {
            Image(
                bitmap = focused.icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(70.dp).alpha(0.35f)
            )
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(tab) { tab = it }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> HomeScreen(
                        visible, store, focused,
                        { focusedPkg = it.pkg }, play, options, pad
                    )
                    1 -> LibraryScreen(
                        visible, store, play, options,
                        onAdd = { showPicker = true }, pad = pad
                    )
                    2 -> SearchScreen(visible, store, play, options, pad)
                    else -> SettingsScreen(
                        onAdd = { showPicker = true },
                        onReset = { confirmReset = true },
                        pad = pad
                    )
                }
            }
        }
    }

    if (showPicker) {
        AddAppDialog(candidates, { store.addApp(it.pkg) }, { showPicker = false })
    }

    selected?.let { a ->
        val fav = a.pkg in store.favorites
        AlertDialog(
            onDismissRequest = { selectedPkg = null },
            title = { Text(a.name) },
            text = {
                Column {
                    TextButton(onClick = { store.toggleFav(a.pkg); selectedPkg = null }) {
                        Text(if (fav) "★ Hapus dari favorit" else "☆ Tambah ke favorit")
                    }
                    TextButton(onClick = { store.removeApp(a.pkg); selectedPkg = null }) {
                        Text("Hapus dari launcher")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedPkg = null }) { Text("Tutup") } }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Kembalikan daftar otomatis?") },
            text = { Text("Semua app yang kamu tambah atau hapus manual akan dikembalikan ke deteksi otomatis. Favorit tidak berubah.") },
            confirmButton = {
                TextButton(onClick = { store.resetList(); confirmReset = false }) { Text("Kembalikan") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Batal") } }
        )
    }
}

class MainActivity : ComponentActivity() {
    /** Naik setiap kali launcher kembali tampil, memicu pemuatan ulang daftar app. */
    private var refreshTick by mutableIntStateOf(0)

    private val pad = GamepadInput()
    private var stickX = 0
    private var stickY = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Deck.Accent,
                    background = Deck.Bg,
                    surface = Deck.Panel,
                    onSurface = Deck.Text
                )
            ) {
                val ctx = LocalContext.current
                val tick = refreshTick
                val apps by produceState<List<AppItem>?>(initialValue = null, tick) {
                    value = withContext(Dispatchers.Default) { loadAllApps(ctx) }
                }
                val list = apps
                if (list == null) {
                    Box(Modifier.fillMaxSize().background(Deck.Bg))
                } else {
                    LauncherApp(list, pad)
                }
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        // Setelah bermain atau memasang game baru, muat ulang daftar.
        refreshTick++
    }

    // ---------- Gamepad ----------

    private fun KeyEvent.isFromPad(): Boolean =
        (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD

    private fun padKeyFor(code: Int): Pad? = when (code) {
        KeyEvent.KEYCODE_DPAD_LEFT -> Pad.Left
        KeyEvent.KEYCODE_DPAD_RIGHT -> Pad.Right
        KeyEvent.KEYCODE_DPAD_UP -> Pad.Up
        KeyEvent.KEYCODE_DPAD_DOWN -> Pad.Down
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A -> Pad.Confirm
        KeyEvent.KEYCODE_BUTTON_B -> Pad.Back
        KeyEvent.KEYCODE_BUTTON_X -> Pad.Search
        KeyEvent.KEYCODE_BUTTON_Y -> Pad.Favorite
        KeyEvent.KEYCODE_BUTTON_L1 -> Pad.PrevTab
        KeyEvent.KEYCODE_BUTTON_R1 -> Pad.NextTab
        else -> null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.isFromPad()) {
            val key = padKeyFor(event.keyCode)
            if (key != null) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val directional =
                        key == Pad.Left || key == Pad.Right || key == Pad.Up || key == Pad.Down
                    // Arah boleh berulang saat ditahan; tombol aksi hanya sekali per tekan.
                    if (directional || event.repeatCount == 0) pad.send(key)
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Sebagian gamepad melaporkan D-pad (hat) dan stik kiri sebagai sumbu, bukan tombol. */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val isStick = (ev.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (isStick && ev.action == MotionEvent.ACTION_MOVE) {
            val hx = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val x = axisDir(if (abs(hx) > 0.5f) hx else ev.getAxisValue(MotionEvent.AXIS_X), stickX)
            val y = axisDir(if (abs(hy) > 0.5f) hy else ev.getAxisValue(MotionEvent.AXIS_Y), stickY)
            if (x != stickX) {
                stickX = x
                if (x < 0) pad.send(Pad.Left) else if (x > 0) pad.send(Pad.Right)
            }
            if (y != stickY) {
                stickY = y
                if (y < 0) pad.send(Pad.Up) else if (y > 0) pad.send(Pad.Down)
            }
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    /** Dorong lewat 0,6 untuk memicu, lepas di bawah 0,3 untuk mengatur ulang (hindari getar di batas). */
    private fun axisDir(v: Float, last: Int): Int = when {
        v < -0.6f -> -1
        v > 0.6f -> 1
        abs(v) < 0.3f -> 0
        else -> last
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Begitu layar disentuh, sorotan gamepad di Koleksi/Cari/Atur disembunyikan lagi.
        if (ev.action == MotionEvent.ACTION_DOWN) pad.touched()
        return super.dispatchTouchEvent(ev)
    }
}
