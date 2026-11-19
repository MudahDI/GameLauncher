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
import androidx.compose.foundation.layout.fillMaxHeight
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                .background(if (danger) Color(0xFF4A252B) else Deck.Accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = tint, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(subtitle, color = Deck.TextDim, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Text("›", color = Deck.TextDim, fontSize = 20.sp)
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
        Deck.Language = store.language
    }
    // 0 = Beranda, 1 = Koleksi, 2 = Cari, 3 = Atur, 4 = Riwayat
    var tab by rememberSaveable { mutableStateOf(0) }
    // Simpan package name (bukan AppItem) supaya state bisa bertahan saat proses dibunuh sistem.
    var focusedPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var activeHomeRow by rememberSaveable { mutableIntStateOf(0) }
    // Home focus area: hero actions first, then the game rows.
    var homeFocusArea by rememberSaveable { mutableStateOf("hero") }
    var heroActionIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFromHome by rememberSaveable { mutableStateOf(false) }
    var detailPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var editingDescriptionPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var metadataPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var showMetadataSources by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var descriptionDraft by rememberSaveable { mutableStateOf("") }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var confirmRestart by rememberSaveable { mutableStateOf(false) }
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
    BackHandler(enabled = detailPkg != null || tab != 0) {
        if (detailPkg != null) detailPkg = null else tab = 0
    }

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
    // Fokus vertikal mengikuti baris yang benar-benar tampil di HomeScreen.
    // Jika ada favorit: 0 = favorit, 1 = game lainnya. Jika tidak: 0 = semua game.
    val homeSections = remember(games, store.favorites) {
        val favorites = games.filter { it.pkg in store.favorites }
        val others = games.filterNot { it.pkg in store.favorites }
        if (favorites.isNotEmpty()) listOf(favorites, others) else listOf(games)
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
    val options: (AppItem) -> Unit = { a -> selectedFromHome = false; selectedPkg = a.pkg }
    val homeOptions: (AppItem) -> Unit = { a -> selectedFromHome = true; selectedPkg = a.pkg }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(gamepadFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false
                else when (event.key) {
                    // L1/R1 berpindah tab utama pada portrait maupun landscape.
                    Key.ButtonL1, Key.ButtonR1 -> {
                        if (tab in 0..2) {
                            val direction = if (event.key == Key.ButtonR1) 1 else -1
                            tab = (tab + direction + 3) % 3
                            if (tab == 0) homeFocusArea = "hero"
                            true
                        } else false
                    }
                    Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> false
                    Key.DirectionRight, Key.DirectionLeft -> {
                        if (tab == 0) {
                            if (homeFocusArea == "hero") {
                                val step = if (event.key == Key.DirectionRight) 1 else -1
                                heroActionIndex = (heroActionIndex + step).coerceIn(0, 3)
                            } else {
                                val row = homeSections.getOrNull(effectiveHomeRow).orEmpty()
                                if (row.isNotEmpty()) {
                                    val itemIndex = row.indexOfFirst { it.pkg == focusedPkg }.coerceAtLeast(0)
                                    val nextIndex = if (event.key == Key.DirectionRight) (itemIndex + 1) % row.size else (itemIndex - 1 + row.size) % row.size
                                    focusedPkg = row[nextIndex].pkg
                                }
                            }
                            true
                        } else false
                    }
                    Key.DirectionDown, Key.DirectionUp -> {
                        if (tab == 0) {
                            if (homeFocusArea == "hero") {
                                if (event.key == Key.DirectionDown && homeSections.any { it.isNotEmpty() }) {
                                    homeFocusArea = "cards"
                                    activeHomeRow = effectiveHomeRow
                                }
                            } else if (event.key == Key.DirectionUp && effectiveHomeRow == 0) {
                                homeFocusArea = "hero"
                                heroActionIndex = 0
                            } else if (homeSections.any { it.isNotEmpty() }) {
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
                        } else false
                    }
                    else -> false
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false
                else {
                    when (event.key) {
                        Key.ButtonY -> {
                            (ctx as? ComponentActivity)?.requestedOrientation =
                                if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            true
                        }
                        Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            if (tab == 0 && focused != null) {
                                if (homeFocusArea == "hero") {
                                    when (heroActionIndex) {
                                        0 -> play(focused)
                                        1 -> store.toggleFav(focused.pkg)
                                        2 -> detailPkg = focused.pkg
                                        3 -> homeOptions(focused)
                                    }
                                } else play(focused)
                                true
                            } else false
                        }
                        Key.ButtonB, Key.Escape, Key.Back -> {
                            when {
                                detailPkg != null -> { detailPkg = null; true }
                                selectedPkg != null -> { selectedPkg = null; true }
                                metadataPkg != null -> { metadataPkg = null; true }
                                editingDescriptionPkg != null -> { editingDescriptionPkg = null; true }
                                showMetadataSources -> { showMetadataSources = false; true }
                                showPicker -> { showPicker = false; true }
                                confirmReset -> { confirmReset = false; true }
                                tab != 0 -> { tab = 0; true }
                                else -> false
                            }
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
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (isLandscape) SideNavigation(selected = tab) { tab = it }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            (fadeIn(tween(150)) + slideInHorizontally(tween(180)) { it / 24 }) togetherWith
                                fadeOut(tween(90))
                        },
                        label = "mdiTabTransition"
                    ) { targetTab ->
                        if (detailPkg != null) {
                            val detailApp = (games + apps).firstOrNull { it.pkg == detailPkg }
                            if (detailApp != null) {
                                GameDetailScreen(
                                    app = detailApp,
                                    store = store,
                                    onBack = { detailPkg = null },
                                    onPlay = play,
                                    onOptions = options
                                )
                            }
                        } else when (targetTab) {
                            0 -> HomeScreen(
                                games, store, focused, if (homeFocusArea == "cards") effectiveHomeRow else -1,
                                { app, rowIndex ->
                                    homeFocusArea = "cards"
                                    activeHomeRow = rowIndex
                                    focusedPkg = app.pkg
                                },
                                play, homeOptions,
                                onDetails = { detailPkg = it.pkg },
                                installedApps = apps,
                                focusedActionIndex = if (homeFocusArea == "hero") heroActionIndex else -1
                            )
                            1 -> LibraryScreen(games, apps, store, play, options) { showPicker = true }
                            2 -> SearchScreen(visible, store, play, options)
                            3 -> SettingsScreen(
                                onAdd = { showPicker = true },
                                onReset = { confirmReset = true },
                                onRestart = { confirmRestart = true },
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
                                onThemeChange = { store.updateLightTheme(it) },
                                hasPortraitWallpaper = store.portraitWallpaperUri != null,
                                hasLandscapeWallpaper = store.landscapeWallpaperUri != null,
                                store = store,
                                onManageMetadataSources = { showMetadataSources = true },
                                currentLanguage = store.language,
                                onLanguageChange = { store.updateLanguage(it) }
                            )
                            else -> HistoryScreen(visible, store, play, options)
                        }
                    }
                }
            }
            if (!isLandscape) BottomNavigation(tab) { tab = it }
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
                    Text(a.name, color = Deck.Text)
                    Text(tx("OPSI GAME", "GAME OPTIONS"), color = Deck.TextDim, fontSize = 10.sp, letterSpacing = 1.5.sp)
                }
            },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.48f).dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    OptionAction(if (fav) tx("Hapus dari favorit", "Remove from favorites") else tx("Tambahkan ke favorit", "Add to favorites"), tx("Atur game ini sebagai favorit", "Set this game as a favorite"), if (fav) "♥" else "♡") {
                        store.toggleFav(a.pkg); selectedPkg = null
                    }
                    OptionAction(if (store.isGame(a)) tx("Pindahkan ke Aplikasi", "Move to Apps") else tx("Pindahkan ke Game", "Move to Games"), tx("Ubah kategori di koleksi launcher", "Change this item's launcher category"), "⇄") {
                        if (store.isGame(a)) store.moveToApps(a.pkg) else store.moveToGame(a.pkg)
                        selectedPkg = null
                    }
                    if (selectedFromHome) {
                    if (store.isGame(a)) OptionAction(tx("Cari metadata & artwork", "Find metadata & artwork"), tx("Cari judul, deskripsi, dan gambar dari Wikipedia", "Find titles, descriptions, and artwork from metadata sources"), "⌕") {
                        metadataPkg = a.pkg
                        selectedPkg = null
                    }
                    OptionAction(tx("Edit deskripsi", "Edit description"), tx("Ubah informasi yang tampil di halaman utama", "Change the information shown on the home screen"), "✎") {
                        descriptionDraft = store.description(a.pkg)
                        editingDescriptionPkg = a.pkg
                        selectedPkg = null
                    }
                    OptionAction(if (store.artworkUri(a.pkg, "cover") == null) tx("Pilih cover portrait", "Choose portrait cover") else tx("Ganti cover portrait", "Change portrait cover"), tx("Artwork untuk kartu koleksi", "Artwork used on library cards"), "▣") {
                        pendingArtworkPkg = a.pkg
                        pendingArtworkKind = "cover"
                        selectedPkg = null
                        artworkPicker.launch(arrayOf("image/*"))
                    }
                    if (store.artworkUri(a.pkg, "cover") != null) {
                        OptionAction(tx("Hapus cover portrait", "Remove portrait cover"), tx("Kembali menggunakan ikon aplikasi", "Use the app icon again"), "⌫", danger = true) {
                            store.clearArtwork(a.pkg, "cover")
                        }
                    }
                    OptionAction(if (store.artworkUri(a.pkg, "banner") == null) tx("Pilih banner landscape", "Choose landscape banner") else tx("Ganti banner landscape", "Change landscape banner"), tx("Artwork untuk panel utama mode landscape", "Artwork for the landscape home panel"), "▤") {
                        pendingArtworkPkg = a.pkg
                        pendingArtworkKind = "banner"
                        selectedPkg = null
                        artworkPicker.launch(arrayOf("image/*"))
                    }
                    if (store.artworkUri(a.pkg, "banner") != null) {
                        OptionAction(tx("Hapus banner landscape", "Remove landscape banner"), tx("Kembali menggunakan artwork bawaan", "Use the default artwork again"), "⌫", danger = true) {
                            store.clearArtwork(a.pkg, "banner")
                        }
                    }
                    }
                    OptionAction(tx("Info aplikasi", "App info"), tx("Buka pengaturan Android untuk melihat info, mengelola izin, atau uninstall aplikasi", "Open Android settings to view app details, manage permissions, or uninstall"), "ⓘ") {
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
                    OptionAction(tx("Sembunyikan dari launcher", "Hide from launcher"), tx("Hapus game ini dari daftar tanpa menghapus aplikasinya", "Remove this game from the launcher without uninstalling it"), "−", danger = true) {
                        store.removeApp(a.pkg)
                        selectedPkg = null
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedPkg = null }) { Text(tx("Tutup", "Close")) } }
        )
    }

    metadataTarget?.let { app ->
        GameMetadataDialog(
            app = app,
            onDismiss = { metadataPkg = null },
            onApply = { result ->
                coroutineScope.launch {
                    store.setMetadata(app.pkg, result.title, result.extract.ifBlank { store.description(app.pkg) })
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
            title = { Text(tx("Deskripsi game", "Game description")) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = descriptionDraft,
                    onValueChange = { descriptionDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(tx("Tulis deskripsi untuk $appName...", "Write a description for $appName...")) },
                    minLines = 4,
                    maxLines = 7
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.setDescription(pkg, descriptionDraft)
                    editingDescriptionPkg = null
                }) { Text(tx("Simpan", "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { editingDescriptionPkg = null }) { Text(tx("Batal", "Cancel")) }
            }
        )
    }


    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text(tx("Restart MDI Launcher?", "Restart MDI Launcher?")) },
            text = { Text(tx("Launcher akan dimulai ulang. Pengaturan, favorit, dan koleksi game tetap tersimpan.", "The launcher will restart. Your settings, favorites, and game library will be preserved.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = false
                    val restartIntent = Intent(ctx, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    ctx.startActivity(restartIntent)
                }) { Text(tx("Restart", "Restart")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestart = false }) { Text(tx("Batal", "Cancel")) }
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(tx("Kembalikan daftar otomatis?", "Restore automatic list?")) },
            text = { Text(tx("Kategori manual dan aplikasi tersembunyi akan dikembalikan ke deteksi otomatis. Favorit tidak berubah.", "Manual categories and hidden apps will return to automatic detection. Favorites will remain unchanged.")) },
            confirmButton = {
                TextButton(onClick = { store.resetList(); confirmReset = false }) { Text(tx("Kembalikan", "Restore")) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(tx("Batal", "Cancel")) } }
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

    override fun onStop() {
        super.onStop()
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
                window.statusBarColor = if (store.isLightTheme) android.graphics.Color.rgb(214, 220, 228) else android.graphics.Color.rgb(14, 20, 27)
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
                LauncherApp(list.orEmpty(), sessionTick, store)
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
