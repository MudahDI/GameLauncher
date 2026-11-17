package com.mdh.gamelauncher

import android.view.KeyEvent as AndroidKeyEvent
import android.graphics.BitmapFactory
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.remember
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Artwork lokal; jika belum tersedia atau gagal dibaca, gunakan ikon aplikasi.
@Composable
fun ArtworkImage(
    app: AppItem,
    store: Store,
    kind: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val uri = store.artworkUri(app.pkg, kind)
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = uri
    ) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                // Baca dimensi terlebih dahulu agar artwork resolusi tinggi tidak
                // didekode penuh ke memori hanya untuk ditampilkan sebagai cover.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }

                val sampleSize = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) {
                        sample *= 2
                    }
                    sample
                } else {
                    2
                }

                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Image(
        bitmap ?: app.icon,
        contentDescription = app.name,
        modifier = modifier,
        contentScale = contentScale
    )
}

// ---------- Komponen ----------

/** Kartu game potret ala Steam Deck. Kartu yang sedang fokus membesar dan diberi garis putih. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCard(
    app: AppItem,
    store: Store,
    width: Dp,
    isFav: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocus: () -> Unit = {}
) {
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "cardScale"
    )
    val shape = RoundedCornerShape(store.cardCornerDp.dp)
    Box(
        Modifier
            .size(width, width * 4 / 3)
            .scale(scale)
            .clip(shape)
            .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .background(Deck.Panel)
            // combinedClickable menyediakan aksi sentuh dan aktivasi tombol utama gamepad.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_BUTTON_A,
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER -> {
                        if (focused) onDoubleClick() else onClick()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_BUTTON_X -> {
                        onLongClick()
                        true
                    }
                    else -> false
                }
            }
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick, onLongClick = onLongClick)
            .onFocusChanged { if (it.isFocused) onFocus() }
    ) {
        if (store.artworkUri(app.pkg, "cover") != null) {
            ArtworkImage(app, store, "cover", Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
        } else {
            Image(
                app.icon,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(24.dp)
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            Image(
                app.icon,
                app.name,
                Modifier
                    .align(Alignment.Center)
                    .size(width * 0.55f)
                    .clip(RoundedCornerShape(14.dp))
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(
                app.name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isFav) {
            Text(
                "★",
                color = Color(0xFFFFD54F),
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, compact: Boolean) {
    Text(
        text,
        color = Deck.TextDim,
        fontSize = if (compact) 12.sp else 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp)
    )
}

@Composable
fun HintText(text: String) {
    Text(
        text,
        color = Deck.TextDim,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
fun CardRow(
    title: String,
    list: List<AppItem>,
    focusedPkg: String?,
    store: Store,
    cardWidth: Dp,
    compact: Boolean,
    onTap: (AppItem) -> Unit,
    onPlay: (AppItem) -> Unit,
    onOptions: (AppItem) -> Unit,
    onFocus: (AppItem) -> Unit
) {
    SectionTitle(title, compact)
    LazyRow(
        modifier = Modifier.focusGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(store.gridSpacingDp.dp)
    ) {
        items(list, key = { it.pkg }) { app ->
            GameCard(
                app,
                store,
                cardWidth,
                app.pkg in store.favorites,
                app.pkg == focusedPkg,
                { onTap(app) },
                { onPlay(app) },
                { onOptions(app) },
                { onFocus(app) }
            )
        }
    }
}

@Composable
fun GameGrid(
    list: List<AppItem>,
    store: Store,
    onPlay: (AppItem) -> Unit,
    onOptions: (AppItem) -> Unit,
    tile: Dp = 110.dp,
    focusedPkg: String? = null,
    onFocus: (AppItem) -> Unit = {},
    emptyLabel: String = "Tidak ada item.",
    spacing: Dp = 16.dp
) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyLabel, color = Deck.TextDim)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(tile + 12.dp),
            contentPadding = PaddingValues(horizontal = spacing + 8.dp, vertical = spacing + 8.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.fillMaxSize().focusGroup()
        ) {
            items(list, key = { it.pkg }) { app ->
                GameCard(
                    app,
                    store,
                    tile,
                    app.pkg in store.favorites,
                    app.pkg == focusedPkg,
                    { onFocus(app) },
                    { onPlay(app) },
                    { onOptions(app) },
                    { onFocus(app) }
                )
            }
        }
    }
}

// ---------- Layar ----------

@Composable
fun HeroPanel(
    focused: AppItem?,
    store: Store,
    compact: Boolean,
    onPlay: (AppItem) -> Unit,
    onOptions: (AppItem) -> Unit,
    landscape: Boolean = false
) {
    if (focused == null) {
        HintText("Belum ada game terdeteksi. Tambahkan app lewat tab ATUR.")
        return
    }
    val isFav = focused.pkg in store.favorites
    val heroShape = RoundedCornerShape(18.dp)

    if (landscape) {
        // Layout landscape memakai artwork 16:9 yang terpisah dari panel informasi.
        // Ruang horizontal dipakai untuk judul, status, dan aksi agar banner tidak
        // berubah menjadi strip panoramik.
        Row(
            Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(heroShape)
                .background(Deck.Panel)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(228.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                ArtworkImage(
                    focused,
                    store,
                    "banner",
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.16f)))
            }
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "SEDANG DIPILIH",
                    color = Deck.Accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    focused.name,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (focused.pkg in store.recent) "Terakhir dimainkan" else "Siap dimainkan",
                    color = Deck.TextDim,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayButton("▶  MAINKAN", compact = true) { onPlay(focused) }
                    DeckButton(if (isFav) "★" else "☆", compact = true) { store.toggleFav(focused.pkg) }
                    DeckButton("⋯", compact = true) { onOptions(focused) }
                }
            }
            Column(
                Modifier.width(190.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text("DESKRIPSI", color = Deck.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(7.dp))
                Text(
                    store.description(focused.pkg).ifBlank { "Belum ada deskripsi. Tambahkan melalui Opsi game." },
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        val iconSize = if (compact) 84.dp else 112.dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (compact) 148.dp else 196.dp)
                .clip(heroShape)
                .background(Deck.Panel)
        ) {
            ArtworkImage(
                focused, store, "banner",
                Modifier.fillMaxSize().clip(heroShape),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f)))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.52f))
                    )
                )
            )
            Row(
                Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Image(
                    focused.icon,
                    focused.name,
                    Modifier.size(iconSize).clip(RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        focused.name,
                        color = Color.White,
                        fontSize = if (compact) 24.sp else 34.sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (focused.pkg in store.recent) "Terakhir dimainkan" else "Siap dimainkan",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp
                    )
                    if (store.description(focused.pkg).isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            store.description(focused.pkg),
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayButton("▶  MAINKAN", compact = true) { onPlay(focused) }
                        DeckButton(if (isFav) "★" else "☆", compact = true) { store.toggleFav(focused.pkg) }
                        DeckButton("⋯", compact = true) { onOptions(focused) }
                    }
                }
            }
        }
    }
}


private data class DashboardInfo(val timestamp: Long, val batteryPercent: Int)

@Composable
private fun HomeDashboard(
    gameCount: Int,
    favoriteCount: Int,
    recentCount: Int,
    landscape: Boolean
) {
    val context = LocalContext.current
    val info by produceState(
        initialValue = DashboardInfo(System.currentTimeMillis(), -1),
        key1 = context
    ) {
        while (true) {
            val battery = runCatching {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }.getOrNull()
            val level = battery?.getIntExtra("level", -1) ?: -1
            val scale = battery?.getIntExtra("scale", -1) ?: -1
            val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else -1
            value = DashboardInfo(System.currentTimeMillis(), percent)
            delay(30_000)
        }
    }
    val time = remember(info.timestamp) {
        SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date(info.timestamp))
    }
    val date = remember(info.timestamp) {
        SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(Date(info.timestamp))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("id", "ID")) else it.toString() }
    }

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(Deck.Panel.copy(alpha = 0.98f), Deck.PanelLight.copy(alpha = 0.88f))))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = if (landscape) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(time, color = Color.White, fontSize = if (landscape) 34.sp else 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            Text(date, color = Deck.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DashboardStat("GAME", gameCount)
                DashboardStat("FAVORIT", favoriteCount)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                DashboardStat("TERAKHIR", recentCount)
                if (info.batteryPercent >= 0) {
                    Text(
                        "BAT " + info.batteryPercent + "%",
                        color = Deck.Accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Deck.Accent.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardStat(label: String, count: Int) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(count.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Deck.TextDim, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
    }
}

@Composable
fun HomeScreen(
    visible: List<AppItem>,
    store: Store,
    focused: AppItem?,
    onFocus: (AppItem) -> Unit,
    onPlay: (AppItem) -> Unit,
    onOptions: (AppItem) -> Unit
) {
    // Lookup map avoids repeated linear scans for recently played apps.
    val visibleByPackage = remember(visible) { visible.associateBy { it.pkg } }
    val recent = remember(visibleByPackage, store.recent) {
        store.recent.mapNotNull(visibleByPackage::get)
    }
    val favs = remember(visible, store.favorites) {
        visible.filter { it.pkg in store.favorites }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 420.dp
        val landscape = maxWidth > maxHeight
        val cardWidth = if (compact) store.cardSizeDp.coerceAtMost(104).dp else store.cardSizeDp.dp
        // Ketuk sekali untuk memilih, ketuk dua kali untuk bermain.
        val tap: (AppItem) -> Unit = { a -> onFocus(a) }

        if (landscape) {
            // Kembalikan susunan landscape sebelumnya: banner di atas, deretan game di bawah.
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                HomeDashboard(visible.size, favs.size, recent.size, landscape = true)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // Batasi lebar banner khusus landscape agar artwork tidak terlalu panoramik.
                    Box(Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                        HeroPanel(focused, store, compact, onPlay, onOptions, landscape = true)
                    }
                }
                if (recent.isNotEmpty()) CardRow(
                    "TERAKHIR DIMAINKAN", recent, focused?.pkg, store,
                    cardWidth, compact, tap, onPlay, onOptions, onFocus
                )
                if (favs.isNotEmpty()) CardRow(
                    "FAVORIT", favs, focused?.pkg, store,
                    cardWidth, compact, tap, onPlay, onOptions, onFocus
                )
                if (visible.isNotEmpty()) CardRow(
                    "SEMUA GAME", visible, focused?.pkg, store,
                    cardWidth, compact, tap, onPlay, onOptions, onFocus
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                HomeDashboard(visible.size, favs.size, recent.size, landscape = false)
                Spacer(Modifier.height(14.dp))
                HeroPanel(focused, store, compact, onPlay, onOptions)
                if (recent.isNotEmpty()) CardRow(
                    "TERAKHIR DIMAINKAN", recent, focused?.pkg, store,
                    cardWidth, compact, tap, onPlay, onOptions, onFocus
                )
                if (favs.isNotEmpty()) CardRow(
                    "FAVORIT", favs, focused?.pkg, store,
                    cardWidth, compact, tap, onPlay, onOptions, onFocus
                )
                if (visible.isNotEmpty()) CardRow(
                    "SEMUA GAME", visible, focused?.pkg, store,
                    cardWidth, compact, tap, onPlay, onOptions, onFocus
                )
            }
        }
    }
}

@Composable
fun SearchScreen(
    visible: List<AppItem>,
    store: Store,
    onPlay: (AppItem) -> Unit,
    onOptions: (AppItem) -> Unit
) {
    var q by rememberSaveable { mutableStateOf("") }
    var focusedPkg by rememberSaveable { mutableStateOf<String?>(null) }
    val results = remember(visible, q) {
        if (q.isBlank()) visible
        else visible.filter { it.name.contains(q, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            singleLine = true,
            label = { Text("Cari game atau aplikasi") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
        )
        GameGrid(results, store, onPlay, onOptions, tile = store.cardSizeDp.dp, focusedPkg = focusedPkg, onFocus = { focusedPkg = it.pkg }, emptyLabel = "Tidak ada hasil.", spacing = store.gridSpacingDp.dp)
    }
}

@Composable
fun LibraryScreen(
    games: List<AppItem>,
    apps: List<AppItem>,
    store: Store,
    onPlay: (AppItem) -> Unit,
    onOptions: (AppItem) -> Unit,
    onAdd: () -> Unit
) {
    var showGames by rememberSaveable { mutableStateOf(true) }
    var onlyFav by rememberSaveable { mutableStateOf(false) }
    var focusedPkg by rememberSaveable { mutableStateOf<String?>(null) }
    val source = if (showGames) games else apps
    val list = remember(source, onlyFav, store.favorites) {
        if (onlyFav) source.filter { it.pkg in store.favorites } else source
    }
    val on = Color.White
    val off = Deck.TextDim

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("KOLEKSI", color = Deck.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showGames = true; onlyFav = false }) {
                Text("GAME  ${games.size}", color = if (showGames) on else off)
            }
            TextButton(onClick = { showGames = false; onlyFav = false }) {
                Text("APLIKASI  ${apps.size}", color = if (!showGames) on else off)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onlyFav = !onlyFav }) {
                Text(if (onlyFav) "★ Favorit aktif" else "☆ Favorit", color = if (onlyFav) Deck.Accent else off)
            }
            TextButton(onClick = onAdd) { Text("+ Pulihkan", color = Deck.Accent) }
        }
        GameGrid(
            list, store, onPlay, onOptions,
            tile = store.cardSizeDp.dp,
            focusedPkg = focusedPkg,
            onFocus = { focusedPkg = it.pkg },
            emptyLabel = if (showGames) "Belum ada game." else "Belum ada aplikasi.",
            spacing = store.gridSpacingDp.dp
        )
    }
}

@Composable
fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Deck.Panel)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(title, color = Color.White, fontSize = 17.sp)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = Deck.TextDim, fontSize = 13.sp)
    }
}

@Composable
private fun SettingSlider(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(valueLabel, color = Deck.Accent, fontSize = 12.sp)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    }
}

@Composable
fun SettingsScreen(
    onAdd: () -> Unit,
    onReset: () -> Unit,
    onWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onAccent: (String) -> Unit,
    currentAccent: String,
    hasWallpaper: Boolean,
    store: Store
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Text("TAMPILAN", color = Deck.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        Text("Warna aksen", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("Ubah warna tombol, fokus, dan elemen aktif launcher.", color = Deck.TextDim, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                "#1A9FFF" to "Biru",
                "#8B5CF6" to "Ungu",
                "#20C997" to "Mint",
                "#F59E0B" to "Amber",
                "#EF5DA8" to "Pink",
                "#E5E7EB" to "Putih"
            ).forEach { (hex, label) ->
                val selected = currentAccent.equals(hex, ignoreCase = true)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(50))
                            .background(Color(android.graphics.Color.parseColor(hex)))
                            .then(if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(50)) else Modifier)
                            .clickable { onAccent(hex) }
                    )
                    Text(label, color = Deck.TextDim, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        SettingRow(
            "Ganti wallpaper",
            "Pilih gambar dari perangkat sebagai latar launcher",
            onWallpaper
        )
        if (hasWallpaper) {
            Spacer(Modifier.height(8.dp))
            SettingRow("Hapus wallpaper", "Kembali ke latar charcoal bawaan", onClearWallpaper)
        }
        Spacer(Modifier.height(24.dp))
        Text("TATA LETAK", color = Deck.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        SettingSlider("Ukuran kartu", "${store.cardSizeDp} dp", store.cardSizeDp.toFloat(), 88f..148f, 14) { store.setCardSize(it.toInt()) }
        SettingSlider("Jarak antar-kartu", "${store.gridSpacingDp} dp", store.gridSpacingDp.toFloat(), 6f..24f, 17) { store.setGridSpacing(it.toInt()) }
        SettingSlider("Sudut kartu", "${store.cardCornerDp} dp", store.cardCornerDp.toFloat(), 4f..20f, 15) { store.setCardCorner(it.toInt()) }
        Spacer(Modifier.height(24.dp))
        Text("WALLPAPER", color = Deck.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        SettingSlider("Intensitas wallpaper", "${(store.wallpaperOpacity * 100).toInt()}%", store.wallpaperOpacity, 0.05f..1.00f, 94) { store.updateWallpaperOpacity(it) }
        SettingSlider("Kegelapan overlay", "${(store.wallpaperOverlay * 100).toInt()}%", store.wallpaperOverlay, 0.15f..0.95f, 79) { store.updateWallpaperOverlay(it) }
        Spacer(Modifier.height(10.dp))
        SettingRow("Pulihkan tampilan default", "Kembalikan ukuran kartu, jarak, sudut, warna aksen, dan efek wallpaper", { store.resetAppearance() })
        Spacer(Modifier.height(24.dp))
        Text("APLIKASI", color = Deck.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        SettingRow(
            "Pulihkan aplikasi tersembunyi",
            "Tampilkan kembali aplikasi yang sebelumnya disembunyikan",
            onAdd
        )
        Spacer(Modifier.height(10.dp))
        SettingRow(
            "Reset kategori dan daftar",
            "Kembalikan kategori otomatis dan tampilkan aplikasi tersembunyi (favorit tetap)",
            onReset
        )
    }
}

@Composable
fun AddAppDialog(
    candidates: List<AppItem>,
    onAdd: (AppItem) -> Unit,
    onClose: () -> Unit
) {
    val listMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Pulihkan aplikasi") },
        text = {
            if (candidates.isEmpty()) {
                Text("Tidak ada aplikasi tersembunyi.")
            } else {
                LazyColumn(Modifier.heightIn(max = listMaxHeight)) {
                    items(candidates, key = { it.pkg }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onAdd(app) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(app.icon, app.name, Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Selesai") } }
    )
}
