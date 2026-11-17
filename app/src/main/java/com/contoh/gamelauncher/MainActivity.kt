package com.mdh.gamelauncher

import android.os.Bundle
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.MotionEvent
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.launch

@Composable
private fun OptionAction(
    title: String,
    subtitle: String,
    symbol: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (danger) Color(0xFFFF7777) else Deck.Text
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Deck.Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(if (danger) Color(0xFF4A252B) else Deck.PanelLight),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = tint, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Deck.TextDim, fontSize = 11.sp, lineHeight = 14.sp)
        }
        Text("›", color = Deck.TextDim, fontSize = 22.sp)
    }
}


@Composable
private fun WallpaperBackground(store: Store) {
    val context = LocalContext.current
    val uri = store.wallpaperUri
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = uri?.let { ArtworkBitmapCache.get("${it}|1440")?.asImageBitmap() },
        key1 = uri
    ) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            ArtworkBitmapCache.load(context, uri, 1440)
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(store.wallpaperOpacity)
        )
        Box(Modifier.fillMaxSize().background(Deck.Bg.copy(alpha = store.wallpaperOverlay)))
    }
}

// ---------- Root ----------

@Composable
fun LauncherApp(allApps: List<AppItem>, controllerNotice: String?, onDismissControllerNotice: () -> Unit) {
    val ctx = LocalContext.current
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val rootFocusRequester = remember { FocusRequester() }
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val store = remember { Store(ctx) }
    SideEffect {
        runCatching {
            Deck.Accent = Color(android.graphics.Color.parseColor(store.accentHex))
        }
    }
    // 0 = Beranda, 1 = Koleksi, 2 = Cari, 3 = Atur, 4 = Riwayat
    var tab by rememberSaveable { mutableStateOf(0) }
    var rotationMode by rememberSaveable { mutableIntStateOf(0) }
    val applyRotation: (Int) -> Unit = { mode ->
        rotationMode = mode
        val requested = when (mode) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        (ctx as? ComponentActivity)?.requestedOrientation = requested
    }
    // Simpan package name (bukan AppItem) supaya state bisa bertahan saat proses dibunuh sistem.
    var focusedPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var editingDescriptionPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var metadataPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var showMetadataSources by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var descriptionDraft by rememberSaveable { mutableStateOf("") }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var showQuickMenu by rememberSaveable { mutableStateOf(false) }
    var pendingArtworkPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingArtworkKind by rememberSaveable { mutableStateOf<String?>(null) }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            store.setWallpaper(uri)
        }
    }
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

    LaunchedEffect(allApps) { store.reloadPlayStats() }

    // Semua aplikasi launcher tampil otomatis; klasifikasi mengikuti deteksi Android
    // kecuali pengguna pernah mengubah kategori secara manual.
    val visible = remember(allApps, store.removed) {
        allApps.filter { it.pkg !in store.removed }
    }
    val games = remember(visible, store.forcedGames, store.forcedApps, store.metadataTitles) {
        visible.filter { store.isGame(it) }.map { app -> app.copy(name = store.displayName(app.pkg, app.name)) }
    }
    val apps = remember(visible, store.forcedGames, store.forcedApps) {
        visible.filterNot { store.isGame(it) }
    }
    val candidates = remember(allApps, store.removed) {
        allApps.filter { it.pkg in store.removed }
    }
    // Package lookup avoids scanning the game list repeatedly for recent items.
    val gamesByPackage = remember(games) { games.associateBy { it.pkg } }
    val focused = remember(gamesByPackage, focusedPkg, store.recent, games) {
        gamesByPackage[focusedPkg]
            ?: store.recent.firstNotNullOfOrNull(gamesByPackage::get)
            ?: games.firstOrNull()
    }
    val selected = selectedPkg?.let { p -> (games + apps).find { it.pkg == p } }
    val metadataTarget = metadataPkg?.let { p -> (games + apps).find { it.pkg == p } }

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
                        tab = (tab - 1 + 5) % 5
                        true
                    }
                    AndroidKeyEvent.KEYCODE_BUTTON_R1 -> {
                        tab = (tab + 1) % 5
                        true
                    }
                    AndroidKeyEvent.KEYCODE_BUTTON_START -> {
                        if (!showPicker && !confirmReset && selectedPkg == null) {
                            showQuickMenu = !showQuickMenu
                        }
                        true
                    }
                    (if (store.swapAB) AndroidKeyEvent.KEYCODE_BUTTON_A else AndroidKeyEvent.KEYCODE_BUTTON_B),
                    AndroidKeyEvent.KEYCODE_BACK -> {
                        when {
                            showQuickMenu -> showQuickMenu = false
                            showMetadataSources -> showMetadataSources = false
                            metadataPkg != null -> metadataPkg = null
                            editingDescriptionPkg != null -> editingDescriptionPkg = null
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
        WallpaperBackground(store)
        Column(Modifier.fillMaxSize()) {
            HeaderBar(onHistory = { tab = 4 }, onRotate = { applyRotation((rotationMode + 1) % 3) }, rotationMode = rotationMode, onSettings = { tab = 3 })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(tween(150)) + slideInHorizontally(tween(180)) { it / 24 }) togetherWith
                            fadeOut(tween(90))
                    },
                    label = "mdiTabTransition"
                ) { targetTab ->
                when (targetTab) {
                    0 -> HomeScreen(
                        games, store, focused,
                        { focusedPkg = it.pkg }, play, options
                    )
                    1 -> LibraryScreen(games, apps, store, play, options) { showPicker = true }
                    2 -> SearchScreen(visible, store, play, options)
                    3 -> SettingsScreen(
                        onAdd = { showPicker = true },
                        onReset = { confirmReset = true },
                        onWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
                        onClearWallpaper = { store.setWallpaper(null) },
                        onAccent = { store.setAccent(it) },
                        currentAccent = store.accentHex,
                        hasWallpaper = store.wallpaperUri != null,
                        store = store,
                        onManageMetadataSources = { showMetadataSources = true }
                    )
                    else -> HistoryScreen(visible, store, play, options)
                }
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

    if (showMetadataSources) {
        MetadataSourcesDialog(onDismiss = { showMetadataSources = false })
    }

    if (showPicker) {
        AddAppDialog(candidates, { store.addApp(it.pkg) }, { showPicker = false })
    }

    selected?.let { a ->
        val fav = a.pkg in store.favorites
        AlertDialog(
            onDismissRequest = { selectedPkg = null },
            title = {
                Column {
                    Text(a.name, color = Color.White)
                    Text("OPSI GAME", color = Deck.TextDim, fontSize = 10.sp, letterSpacing = 1.5.sp)
                }
            },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    OptionAction(if (fav) "Hapus dari favorit" else "Tambahkan ke favorit", "Atur game ini sebagai favorit", if (fav) "★" else "☆") {
                        store.toggleFav(a.pkg); selectedPkg = null
                    }
                    OptionAction(if (store.isGame(a)) "Pindahkan ke Aplikasi" else "Pindahkan ke Game", "Ubah kategori di koleksi launcher", "⇄") {
                        if (store.isGame(a)) store.moveToApps(a.pkg) else store.moveToGame(a.pkg)
                        selectedPkg = null
                    }
                    if (store.isGame(a)) OptionAction("Cari metadata & artwork", "Cari judul, deskripsi, dan gambar dari Wikipedia", "⌕") {
                        metadataPkg = a.pkg
                        selectedPkg = null
                    }
                    OptionAction("Edit deskripsi", "Ubah informasi yang tampil di halaman utama", "✎") {
                        descriptionDraft = store.description(a.pkg)
                        editingDescriptionPkg = a.pkg
                        selectedPkg = null
                    }
                    OptionAction(if (store.artworkUri(a.pkg, "cover") == null) "Pilih cover portrait" else "Ganti cover portrait", "Artwork untuk kartu koleksi", "▣") {
                        pendingArtworkPkg = a.pkg
                        pendingArtworkKind = "cover"
                        selectedPkg = null
                        artworkPicker.launch(arrayOf("image/*"))
                    }
                    if (store.artworkUri(a.pkg, "cover") != null) {
                        OptionAction("Hapus cover portrait", "Kembali menggunakan ikon aplikasi", "⌫", danger = true) {
                            store.clearArtwork(a.pkg, "cover")
                        }
                    }
                    OptionAction(if (store.artworkUri(a.pkg, "banner") == null) "Pilih banner landscape" else "Ganti banner landscape", "Artwork untuk panel utama mode landscape", "▤") {
                        pendingArtworkPkg = a.pkg
                        pendingArtworkKind = "banner"
                        selectedPkg = null
                        artworkPicker.launch(arrayOf("image/*"))
                    }
                    if (store.artworkUri(a.pkg, "banner") != null) {
                        OptionAction("Hapus banner landscape", "Kembali menggunakan artwork bawaan", "⌫", danger = true) {
                            store.clearArtwork(a.pkg, "banner")
                        }
                    }
                    OptionAction("Sembunyikan dari launcher", "Hapus game ini dari daftar tanpa menghapus aplikasinya", "−", danger = true) {
                        store.removeApp(a.pkg)
                        selectedPkg = null
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedPkg = null }) { Text("Tutup") } }
        )
    }

    metadataTarget?.let { app ->
        GameMetadataDialog(
            app = app,
            onDismiss = { metadataPkg = null },
            onApply = { result ->
                coroutineScope.launch {
                    store.setMetadata(app.pkg, result.title, result.extract)
                    val coverSource = result.coverUrl ?: result.thumbnailUrl?.takeIf {
                        result.bannerUrl == null || it != result.bannerUrl
                    }
                    val coverUri = coverSource?.let { url ->
                        withContext(Dispatchers.IO) {
                            MetadataRepository.downloadArtwork(ctx, app.pkg, "cover", url)
                        }
                    }
                    val bannerUri = result.bannerUrl?.let { url ->
                        withContext(Dispatchers.IO) {
                            MetadataRepository.downloadArtwork(ctx, app.pkg, "banner", url)
                        }
                    }
                    coverUri?.let { store.setArtwork(app.pkg, "cover", it) }
                    bannerUri?.let { store.setArtwork(app.pkg, "banner", it) }
                    metadataPkg = null
                }
            }
        )
    }

    editingDescriptionPkg?.let { pkg ->
        val appName = allApps.firstOrNull { it.pkg == pkg }?.name ?: pkg
        AlertDialog(
            onDismissRequest = { editingDescriptionPkg = null },
            title = { Text("Deskripsi game") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = descriptionDraft,
                    onValueChange = { descriptionDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Tulis deskripsi untuk $appName...") },
                    minLines = 4,
                    maxLines = 7
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.setDescription(pkg, descriptionDraft)
                    editingDescriptionPkg = null
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { editingDescriptionPkg = null }) { Text("Batal") }
            }
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
            if (direction != lastStickDirection || now - lastStickMoveAt >= 105L) {
                lastStickDirection = direction
                lastStickMoveAt = now
                // Pasangan down/up mengikuti alur input navigasi Android normal.
                dispatchKeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, direction))
                dispatchKeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_UP, direction))
            }
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }
    override fun onResume() {
        super.onResume()
        Store.finishPendingSession(this)
        refreshTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        // Edge-to-edge: portrait memberi padding aman di Compose, landscape memakai seluruh area.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        // Minta refresh rate tinggi yang tersedia; Android tetap boleh memilih mode adaptif.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.attributes = window.attributes.apply { preferredRefreshRate = 120f }
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
