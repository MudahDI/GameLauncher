package com.mdh.gamelauncher

import android.os.Bundle
import android.content.Intent
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.MotionEvent
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

// ---------- Root ----------

@Composable
fun LauncherApp(allApps: List<AppItem>, controllerNotice: String?, onDismissControllerNotice: () -> Unit) {
    val ctx = LocalContext.current
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val rootFocusRequester = remember { FocusRequester() }
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val store = remember { Store(ctx) }
    // 0 = Beranda, 1 = Koleksi, 2 = Cari, 3 = Atur
    var tab by rememberSaveable { mutableStateOf(0) }
    // Simpan package name (bukan AppItem) supaya state bisa bertahan saat proses dibunuh sistem.
    var focusedPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var showQuickMenu by rememberSaveable { mutableStateOf(false) }
    var pendingArtworkPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingArtworkKind by rememberSaveable { mutableStateOf<String?>(null) }
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pkg = pendingArtworkPkg
        val kind = pendingArtworkKind
        if (uri != null && pkg != null && kind != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            store.setArtwork(pkg, kind, uri)
        }
        pendingArtworkPkg = null
        pendingArtworkKind = null
    }

    // Fokus dikembalikan ke root saat tab berubah agar D-pad selalu punya titik awal.
    LaunchedEffect(tab) { runCatching { rootFocusRequester.requestFocus() } }

    // Dari tab mana pun, Back kembali ke Beranda dulu.
    BackHandler(enabled = tab != 0) { tab = 0 }

    // Semua aplikasi launcher tampil otomatis; klasifikasi mengikuti deteksi Android
    // kecuali pengguna pernah mengubah kategori secara manual.
    val visible = allApps.filter { it.pkg !in store.removed }
    val games = visible.filter { store.isGame(it) }
    val apps = visible.filterNot { store.isGame(it) }
    val candidates = allApps.filter { it.pkg in store.removed }
    // Game yang sedang dipilih; kalau belum ada, pakai yang terakhir dimainkan lalu game pertama.
    val focused = games.find { it.pkg == focusedPkg }
        ?: store.recent.firstNotNullOfOrNull { p -> games.find { it.pkg == p } }
        ?: games.firstOrNull()
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
            // Portrait tetap menghormati status/navigation bar; landscape memakai seluruh area layar.
            .then(if (isLandscape) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> focusManager.moveFocus(FocusDirection.Up)
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> focusManager.moveFocus(FocusDirection.Down)
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> focusManager.moveFocus(FocusDirection.Left)
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> focusManager.moveFocus(FocusDirection.Right)
                    AndroidKeyEvent.KEYCODE_BUTTON_L1 -> {
                        tab = (tab - 1 + 4) % 4
                        true
                    }
                    AndroidKeyEvent.KEYCODE_BUTTON_R1 -> {
                        tab = (tab + 1) % 4
                        true
                    }
                    AndroidKeyEvent.KEYCODE_BUTTON_START -> {
                        if (!showPicker && !confirmReset && selectedPkg == null) {
                            showQuickMenu = !showQuickMenu
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_BUTTON_B,
                    AndroidKeyEvent.KEYCODE_BACK -> {
                        when {
                            showQuickMenu -> showQuickMenu = false
                            showPicker -> showPicker = false
                            selectedPkg != null -> selectedPkg = null
                            confirmReset -> confirmReset = false
                            tab != 0 -> tab = 0
                        }
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
            HeaderBar()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> HomeScreen(
                        games, store, focused,
                        { focusedPkg = it.pkg }, play, options
                    )
                    1 -> LibraryScreen(games, apps, store, play, options) { showPicker = true }
                    2 -> SearchScreen(visible, store, play, options)
                    else -> SettingsScreen(
                        onAdd = { showPicker = true },
                        onReset = { confirmReset = true }
                    )
                }
            }
            BottomNavigation(tab) { tab = it }
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
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { store.toggleFav(a.pkg); selectedPkg = null }) {
                        Text(if (fav) "★ Hapus dari favorit" else "☆ Tambah ke favorit")
                    }
                    TextButton(
                        onClick = {
                            if (store.isGame(a)) store.moveToApps(a.pkg) else store.moveToGame(a.pkg)
                            selectedPkg = null
                        }
                    ) {
                        Text(if (store.isGame(a)) "Pindahkan ke Aplikasi" else "Pindahkan ke Game")
                    }
                    TextButton(onClick = {
                        pendingArtworkPkg = a.pkg
                        pendingArtworkKind = "cover"
                        selectedPkg = null
                        artworkPicker.launch(arrayOf("image/*"))
                    }) { Text(if (store.artworkUri(a.pkg, "cover") == null) "Pilih cover portrait" else "Ganti cover portrait") }
                    if (store.artworkUri(a.pkg, "cover") != null) {
                        TextButton(onClick = { store.clearArtwork(a.pkg, "cover") }) { Text("Hapus cover") }
                    }
                    TextButton(onClick = {
                        pendingArtworkPkg = a.pkg
                        pendingArtworkKind = "banner"
                        selectedPkg = null
                        artworkPicker.launch(arrayOf("image/*"))
                    }) { Text(if (store.artworkUri(a.pkg, "banner") == null) "Pilih banner landscape" else "Ganti banner landscape") }
                    if (store.artworkUri(a.pkg, "banner") != null) {
                        TextButton(onClick = { store.clearArtwork(a.pkg, "banner") }) { Text("Hapus banner") }
                    }
                    TextButton(onClick = { store.removeApp(a.pkg); selectedPkg = null }) {
                        Text("Sembunyikan dari launcher")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedPkg = null }) { Text("Tutup") } }
        )
    }

    if (showQuickMenu) {
        AlertDialog(
            onDismissRequest = { showQuickMenu = false },
            title = { Text("Menu cepat") },
            text = {
                Column {
                    listOf("Beranda", "Koleksi", "Cari", "Atur").forEachIndexed { index, label ->
                        TextButton(
                            onClick = {
                                tab = index
                                showQuickMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                label,
                                color = if (tab == index) Deck.Accent else Deck.Text
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickMenu = false }) { Text("Tutup") }
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Kembalikan daftar otomatis?") },
            text = { Text("Kategori manual dan aplikasi tersembunyi akan dikembalikan ke deteksi otomatis. Favorit tidak berubah.") },
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
    private var lastStickMoveAt = 0L
    private var lastStickDirection = 0

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

    /**
     * Terjemahkan gerakan analog kiri menjadi navigasi D-pad.
     * Ambang dan interval pengulangan mencegah fokus melompat terlalu cepat.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            val device = event.device
            val xRange = device?.getMotionRange(MotionEvent.AXIS_X, InputDevice.SOURCE_JOYSTICK)
            val yRange = device?.getMotionRange(MotionEvent.AXIS_Y, InputDevice.SOURCE_JOYSTICK)
            if (xRange == null || yRange == null) {
                return super.dispatchGenericMotionEvent(event)
            }

            val x = event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y)
            // Hormati dead-zone bawaan perangkat agar stick tidak bergerak sendiri.
            val xThreshold = maxOf(0.55f, xRange.flat * 2f)
            val yThreshold = maxOf(0.55f, yRange.flat * 2f)
            val direction = when {
                kotlin.math.abs(x) > kotlin.math.abs(y) && x < -xThreshold -> AndroidKeyEvent.KEYCODE_DPAD_LEFT
                kotlin.math.abs(x) > kotlin.math.abs(y) && x > xThreshold -> AndroidKeyEvent.KEYCODE_DPAD_RIGHT
                y < -yThreshold -> AndroidKeyEvent.KEYCODE_DPAD_UP
                y > yThreshold -> AndroidKeyEvent.KEYCODE_DPAD_DOWN
                else -> 0
            }

            if (direction == 0) {
                lastStickDirection = 0
                // Jangan telan gerakan netral atau input joystick lain yang bukan navigasi.
                return super.dispatchGenericMotionEvent(event)
            }

            val now = android.os.SystemClock.uptimeMillis()
            if (direction != lastStickDirection || now - lastStickMoveAt >= 260L) {
                lastStickDirection = direction
                lastStickMoveAt = now
                dispatchKeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_UP, direction))
            }
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: portrait memberi padding aman di Compose, landscape memakai seluruh area.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        // Warna bar mengikuti tema charcoal saat portrait.
        window.statusBarColor = android.graphics.Color.rgb(14, 20, 27)
        window.navigationBarColor = android.graphics.Color.rgb(14, 20, 27)
        inputManager = getSystemService(INPUT_SERVICE) as InputManager
        scanConnectedControllers()
        inputManager.registerInputDeviceListener(inputListener, android.os.Handler(mainLooper))
        applySystemBarsForOrientation()
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
                    value = withContext(Dispatchers.IO) { loadAllApps(ctx) }
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

    /**
     * Portrait: system bars tetap terlihat agar launcher nyaman sebagai HOME utama.
     * Landscape: sembunyikan keduanya untuk ruang layar penuh; swipe tepi layar
     * tetap dapat menampilkan bar sementara.
     */
    private fun applySystemBarsForOrientation() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBarsForOrientation()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBarsForOrientation()
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
