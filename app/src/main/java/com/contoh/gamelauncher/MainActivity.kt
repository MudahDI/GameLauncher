package com.contoh.gamelauncher

import android.os.Bundle
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

// ---------- Root ----------

@Composable
fun LauncherApp(allApps: List<AppItem>, controllerNotice: String?, onDismissControllerNotice: () -> Unit) {
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
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_BUTTON_L1 -> {
                        tab = (tab - 1 + 4) % 4
                        true
                    }
                    AndroidKeyEvent.KEYCODE_BUTTON_R1 -> {
                        tab = (tab + 1) % 4
                        true
                    }
                    else -> false
                }
            }
    ) {
        if (focused != null) {
            Image(
                bitmap = focused.icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(70.dp).alpha(0.35f)
            )
        }
        Column(Modifier.fillMaxSize()) {
            TopBar(tab) { tab = it }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> HomeScreen(
                        visible, store, focused,
                        { focusedPkg = it.pkg }, play, options
                    )
                    1 -> LibraryScreen(visible, store, play, options) { showPicker = true }
                    2 -> SearchScreen(visible, store, play, options)
                    else -> SettingsScreen(
                        onAdd = { showPicker = true },
                        onReset = { confirmReset = true }
                    )
                }
            }
        }
        LaunchedEffect(controllerNotice) {
            if (controllerNotice != null) {
                delay(3200)
                onDismissControllerNotice()
            }
        }
        if (controllerNotice != null) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 60.dp, start = 20.dp, end = 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Deck.PanelLight.copy(alpha = 0.96f))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(controllerNotice, color = Color.White, style = MaterialTheme.typography.bodyMedium)
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
    private var controllerNotice by mutableStateOf<String?>(null)
    private lateinit var inputManager: InputManager
    private val connectedControllers = mutableMapOf<Int, String>()

    private fun isGameController(device: InputDevice?): Boolean {
        if (device == null || device.isVirtual) return false
        return device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
            device.supportsSource(InputDevice.SOURCE_JOYSTICK)
    }

    private fun controllerName(deviceId: Int): String? {
        val device = InputDevice.getDevice(deviceId) ?: return null
        if (!isGameController(device)) return null
        return device.name.ifBlank { "Gamepad" }
    }

    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            val name = controllerName(deviceId) ?: return
            connectedControllers[deviceId] = name
            controllerNotice = "Gamepad tersambung\n$name"
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            val name = connectedControllers.remove(deviceId) ?: return
            controllerNotice = "Gamepad terputus\n$name"
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            val name = controllerName(deviceId)
            if (name == null) {
                connectedControllers.remove(deviceId)
            } else {
                connectedControllers[deviceId] = name
            }
        }
    }

    private fun scanConnectedControllers() {
        connectedControllers.clear()
        InputDevice.getDeviceIds().forEach { id ->
            controllerName(id)?.let { connectedControllers[id] = it }
        }
        if (connectedControllers.isNotEmpty()) {
            val names = connectedControllers.values.distinct().joinToString(", ")
            controllerNotice = "Gamepad terdeteksi\n$names"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        inputManager = getSystemService(INPUT_SERVICE) as InputManager
        scanConnectedControllers()
        inputManager.registerInputDeviceListener(inputListener, android.os.Handler(mainLooper))
        // Immersive sticky: status bar dan navigation bar tersembunyi,
        // tetapi bisa dimunculkan sementara dengan swipe dari tepi layar.
        hideSystemBars()
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
                    LauncherApp(list, controllerNotice) { controllerNotice = null }
                }
            }
        }
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onRestart() {
        super.onRestart()
        // Setelah bermain atau memasang game baru, muat ulang daftar.
        refreshTick++
        scanConnectedControllers()
    }

    override fun onDestroy() {
        if (::inputManager.isInitialized) {
            inputManager.unregisterInputDeviceListener(inputListener)
        }
        super.onDestroy()
    }
}
