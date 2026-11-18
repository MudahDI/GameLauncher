package com.mdh.gamelauncher

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import androidx.compose.material3.dynamicDarkColorScheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
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
    val tint = if (danger) Color(0xFFFF7777) else Deck.Accent
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
                .background(if (danger) Color(0xFF4A252B) else Deck.Accent.copy(alpha = 0.18f)),
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
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val uri = if (isLandscape) store.landscapeWallpaperUri else store.portraitWallpaperUri
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
fun LauncherApp(allApps: List<AppItem>, sessionRefresh: Int, store: Store) {
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val elementColor = MaterialTheme.colorScheme.primary
    SideEffect {
        Deck.Accent = elementColor
        Deck.IsLightTheme = store.isLightTheme
    }
    // 0 = Beranda, 1 = Koleksi, 2 = Cari, 3 = Atur, 4 = Riwayat
    var tab by rememberSaveable { mutableStateOf(0) }
    // Simpan package name (bukan AppItem) supaya state bisa bertahan saat proses dibunuh sistem.
    var focusedPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var activeHomeRow by rememberSaveable { mutableIntStateOf(0) }
    var selectedPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var editingDescriptionPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var metadataPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var showMetadataSources by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var descriptionDraft by rememberSaveable { mutableStateOf("") }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var pendingArtworkPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingArtworkKind by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingWallpaperOrientation by rememberSaveable { mutableStateOf<String?>(null) }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            when (pendingWallpaperOrientation) {
                "portrait" -> store.setPortraitWallpaper(uri)
                "landscape" -> store.setLandscapeWallpaper(uri)
            }
        }
        pendingWallpaperOrientation = null
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

    // Dari tab mana pun, Back kembali ke Beranda dulu.
    BackHandler(enabled = tab != 0) { tab = 0 }

    LaunchedEffect(sessionRefresh) { store.reloadPlayStats() }

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
    // Beranda memiliki tiga baris navigasi vertikal: terakhir dimainkan, favorit, semua game.
    // Stable row IDs: 0 = recent, 1 = favorites, 2 = all games.
    val homeSections = remember(games, store.recent, store.favorites) {
        val byPackage = games.associateBy { it.pkg }
        listOf(
            store.recent.mapNotNull(byPackage::get),
            games.filter { it.pkg in store.favorites },
            games
        )
    }
    val effectiveHomeRow = if (homeSections.getOrNull(activeHomeRow)?.isNotEmpty() == true) {
        activeHomeRow
    } else {
        homeSections.indexOfFirst { it.isNotEmpty() }.coerceAtLeast(0)
    }
    LaunchedEffect(effectiveHomeRow, homeSections, focusedPkg) {
        if (homeSections.getOrNull(effectiveHomeRow)?.isNotEmpty() == true) {
            activeHomeRow = effectiveHomeRow
            val row = homeSections[effectiveHomeRow]
            if (row.none { it.pkg == focusedPkg }) focusedPkg = row.first().pkg
        }
    }

    val gamepadFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { gamepadFocusRequester.requestFocus() }
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
            .focusRequester(gamepadFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false
                else when (event.key) {
                    // Gamepad navigation is intentionally supported on Beranda only.
                    Key.ButtonL1, Key.PageUp, Key.ButtonR1, Key.PageDown -> true
                    Key.DirectionRight, Key.DirectionLeft -> {
                        if (tab == 0) {
                            val row = homeSections.getOrNull(effectiveHomeRow).orEmpty()
                            if (row.isNotEmpty()) {
                                val itemIndex = row.indexOfFirst { it.pkg == focusedPkg }.coerceAtLeast(0)
                                val nextIndex = if (event.key == Key.DirectionRight) (itemIndex + 1) % row.size else (itemIndex - 1 + row.size) % row.size
                                focusedPkg = row[nextIndex].pkg
                            }
                            true
                        } else true
                    }
                    Key.DirectionDown, Key.DirectionUp -> {
                        if (tab == 0) {
                            if (homeSections.any { it.isNotEmpty() }) {
                                val direction = if (event.key == Key.DirectionDown) 1 else -1
                                var targetRowIndex = effectiveHomeRow + direction
                                while (targetRowIndex in homeSections.indices && homeSections[targetRowIndex].isEmpty()) targetRowIndex += direction
                                if (targetRowIndex in homeSections.indices) {
                                    val currentRow = homeSections[effectiveHomeRow]
                                    val targetRow = homeSections[targetRowIndex]
                                    val itemIndex = currentRow.indexOfFirst { it.pkg == focusedPkg }.coerceAtLeast(0)
                                    activeHomeRow = targetRowIndex
                                    focusedPkg = targetRow[itemIndex.coerceAtMost(targetRow.lastIndex)].pkg
                                }
                            }
                            true
                        } else true
                    }
                    else -> false
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false
                else {
                    val currentIndex = games.indexOfFirst { it.pkg == focused?.pkg }.coerceAtLeast(0)
                    when (event.key) {
                        Key.ButtonY -> {
                            (ctx as? ComponentActivity)?.requestedOrientation =
                                if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            true
                        }
                        Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            if (tab == 0 && focused != null) {
                                play(focused)
                                true
                            } else false
                        }
                        Key.ButtonB, Key.Escape, Key.Back -> {
                            when {
                                selectedPkg != null -> selectedPkg = null
                                metadataPkg != null -> metadataPkg = null
                                editingDescriptionPkg != null -> editingDescriptionPkg = null
                                showMetadataSources -> showMetadataSources = false
                                showPicker -> showPicker = false
                                confirmReset -> confirmReset = false
                                tab != 0 -> tab = 0
                                else -> false
                            }.let { true }
                        }
                        else -> false
                    }
                }
            }
            .background(Brush.verticalGradient(listOf(Deck.Bg, Deck.Panel)))
            // Portrait tetap menghormati status/navigation bar; landscape memakai seluruh area layar.
            .then(if (isLandscape) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
    ) {
        WallpaperBackground(store)
        Column(Modifier.fillMaxSize()) {
            HeaderBar(
                onHistory = { tab = 4 },
                onRotate = {
                    (ctx as? ComponentActivity)?.requestedOrientation =
                        if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                },
                isLandscape = isLandscape,
                onSettings = { tab = 3 }
            )
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
                        games, store, focused, effectiveHomeRow,
                        { app, rowIndex ->
                            activeHomeRow = rowIndex
                            focusedPkg = app.pkg
                        },
                        play, options
                    )
                    1 -> LibraryScreen(games, apps, store, play, options) { showPicker = true }
                    2 -> SearchScreen(visible, store, play, options)
                    3 -> SettingsScreen(
                        onAdd = { showPicker = true },
                        onReset = { confirmReset = true },
                        onPortraitWallpaper = {
                            pendingWallpaperOrientation = "portrait"
                            wallpaperPicker.launch(arrayOf("image/*"))
                        },
                        onLandscapeWallpaper = {
                            pendingWallpaperOrientation = "landscape"
                            wallpaperPicker.launch(arrayOf("image/*"))
                        },
                        onClearPortraitWallpaper = { store.setPortraitWallpaper(null) },
                        onClearLandscapeWallpaper = { store.setLandscapeWallpaper(null) },
                        onAccent = { store.setAccent(it) },
                        currentAccent = store.accentHex,
                        onMaterialYou = { store.setMaterialYou(it) },
                        isLightTheme = store.isLightTheme,
                        onThemeChange = { store.setLightTheme(it) },
                        hasPortraitWallpaper = store.portraitWallpaperUri != null,
                        hasLandscapeWallpaper = store.landscapeWallpaperUri != null,
                        store = store,
                        onManageMetadataSources = { showMetadataSources = true }
                    )
                    else -> HistoryScreen(visible, store, play, options)
                }
                }
            }
            BottomNavigation(tab) { tab = it }
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
                    OptionAction("Info aplikasi", "Buka pengaturan Android untuk melihat info, mengelola izin, atau uninstall aplikasi", "ⓘ") {
                        selectedPkg = null
                        runCatching {
                            ctx.startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${a.pkg}")
                                )
                            )
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
    private var refreshTick by mutableIntStateOf(0)
    private var sessionRefresh by mutableIntStateOf(0)
    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshTick++
        }
    }
    private var receiverRegistered = false

    override fun onResume() {
        super.onResume()
        Store.finishPendingSession(this)
        sessionRefresh++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Orientation is manual: start in portrait and change only via the header button.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageChanges, packageFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(packageChanges, packageFilter)
        }
        receiverRegistered = true
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.attributes = window.attributes.apply { preferredRefreshRate = 120f }
        }
        window.statusBarColor = android.graphics.Color.rgb(14, 20, 27)
        window.navigationBarColor = android.graphics.Color.rgb(14, 20, 27)
        applySystemBarsForOrientation()
        setContent {
            val ctx = LocalContext.current
            val store = remember { Store(ctx) }
            val accent = Color(android.graphics.Color.parseColor(store.accentHex))
            val baseScheme = when {
                store.useMaterialYou && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ->
                    if (store.isLightTheme) androidx.compose.material3.dynamicLightColorScheme(ctx) else dynamicDarkColorScheme(ctx)
                store.isLightTheme -> androidx.compose.material3.lightColorScheme(primary = accent)
                else -> darkColorScheme(primary = accent)
            }
            SideEffect {
                window.statusBarColor = if (store.isLightTheme) android.graphics.Color.rgb(243, 245, 248) else android.graphics.Color.rgb(14, 20, 27)
                window.navigationBarColor = window.statusBarColor
                WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = store.isLightTheme
                WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = store.isLightTheme
            }
            MaterialTheme(
                colorScheme = baseScheme.copy(
                    background = Deck.Bg,
                    surface = Deck.Panel,
                    onSurface = Deck.Text,
                    surfaceVariant = Deck.PanelLight,
                    onSurfaceVariant = Deck.TextDim
                )
            ) {
                val tick = refreshTick
                val sessionTick = sessionRefresh
                val apps by produceState<List<AppItem>?>(initialValue = null, tick) {
                    value = withContext(Dispatchers.IO) { loadAllApps(ctx) }
                }
                val list = apps
                if (list == null) Box(Modifier.fillMaxSize().background(Deck.Bg))
                else LauncherApp(list, sessionTick, store)
            }
        }
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(packageChanges) }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun applySystemBarsForOrientation() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
}
