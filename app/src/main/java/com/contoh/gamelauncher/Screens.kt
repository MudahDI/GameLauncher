package com.mdh.gamelauncher

import android.os.Build

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import java.io.FileInputStream
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.remember
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
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

// Cache bitmap bersama agar artwork tidak didekode ulang setiap kali tab dibuat kembali.
internal object ArtworkBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024L / 8L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    @Synchronized
    fun get(key: String): Bitmap? = cache.get(key)

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun load(context: android.content.Context, uri: android.net.Uri, maxDimension: Int = 960): androidx.compose.ui.graphics.ImageBitmap? {
        val key = "${uri}|$maxDimension"
        get(key)?.let { return it.asImageBitmap() }
        return runCatching {
            fun openArtworkStream() = if (uri.scheme == "file") uri.path?.let { FileInputStream(it) } else context.contentResolver.openInputStream(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openArtworkStream()?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
            } else {
                sample = 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = openArtworkStream()?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null
            put(key, bitmap)
            bitmap.asImageBitmap()
        }.getOrNull()
    }
}

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
    val decodeSize = if (kind == "cover") 640 else 960
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = uri?.let { ArtworkBitmapCache.get("${it}|$decodeSize")?.asImageBitmap() },
        key1 = uri
    ) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            ArtworkBitmapCache.load(context, uri, decodeSize)
        }
    }
    Image(
        bitmap ?: app.icon,
        contentDescription = app.name,
        modifier = modifier,
        contentScale = contentScale
    )
}

// Gamepad A/Enter attiva i controlli; il focus DPAD è gestito dal sistema Compose.
private fun Modifier.gamepadClickable(onClick: () -> Unit): Modifier = composed {
    var hasGamepadFocus by remember { mutableStateOf(false) }
    this
        .onFocusChanged { hasGamepadFocus = it.isFocused }
        .then(
            if (hasGamepadFocus) Modifier.border(
                2.dp, Deck.Accent, RoundedCornerShape(10.dp)
            ) else Modifier
        )
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown &&
                (event.key == Key.ButtonA || event.key == Key.Enter ||
                    event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)
            ) {
                onClick()
                true
            } else false
        }
        .clickable(onClick = onClick)
}

// ---------- Komponen ----------

// Branding overlay dihilangkan agar artwork dan ikon game tetap bersih.

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
    onDoubleClick: (() -> Unit)? = null,
    onLongClick: () -> Unit,
    onFocus: () -> Unit = {},
    blurIconBackground: Boolean = false,
    showMdiBranding: Boolean = false,
    showTitle: Boolean = true
) {
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "cardScale"
    )
    val shape = remember(store.cardCornerDp) { RoundedCornerShape(store.cardCornerDp.dp) }
    val coverUri = remember(app.pkg, store.artworks) { store.artworkUri(app.pkg, "cover") }
    val displayIcon = app.icon
    Box(
        Modifier
            .size(width, width * 4 / 3)
            .scale(scale)
            .clip(shape)
            .then(if (focused) Modifier.border(3.dp, Deck.Accent, shape) else Modifier)
            .background(Deck.Panel)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.ButtonA || event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)) { onClick(); true } else false
            }
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick, onLongClick = onLongClick)
    ) {
        if (coverUri != null) {
            ArtworkImage(app, store, "cover", Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
        } else {
            if (blurIconBackground) {
                Image(
                    displayIcon,
                    null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().scale(1.04f).alpha(0.58f)
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f)))
            } else {
                Image(
                    app.icon,
                    null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.48f)
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            }
            Image(
                displayIcon,
                app.name,
                Modifier
                    .align(Alignment.Center)
                    .size(width * 0.55f)
                    .clip(RoundedCornerShape(14.dp)),
            )
        }
        if (showTitle) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardRow(
    title: String,
    list: List<AppItem>,
    focusedPkg: String?,
    rowActive: Boolean,
    rowIndex: Int,
    store: Store,
    cardWidth: Dp,
    compact: Boolean,
    onTap: (AppItem, Int) -> Unit,
    onPlay: (AppItem) -> Unit,
    onOptions: (AppItem) -> Unit,
    onFocus: (AppItem, Int) -> Unit
) {
    val listState = rememberLazyListState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val focusedIndex = remember(list, focusedPkg) {
        if (rowActive) list.indexOfFirst { it.pkg == focusedPkg } else -1
    }

    // Keep the selected card visible horizontally, including when focus changes
    // through the gamepad or by tapping a card.
    LaunchedEffect(rowActive, focusedIndex) {
        if (rowActive && focusedIndex >= 0) {
            listState.animateScrollToItem(focusedIndex)
            bringIntoViewRequester.bringIntoView()
        }
    }

    Column(Modifier.bringIntoViewRequester(bringIntoViewRequester)) {
        SectionTitle(title, compact)
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(store.gridSpacingDp.dp)
        ) {
            items(list, key = { it.pkg }, contentType = { "game-card" }) { app ->
                GameCard(
                    app,
                    store,
                    cardWidth,
                    app.pkg in store.favorites,
                    rowActive && app.pkg == focusedPkg,
                    { onTap(app, rowIndex) },
                    null,
                    { onOptions(app) },
                    { onFocus(app, rowIndex) },
                    showMdiBranding = true,
                    showTitle = false
                )
            }
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
    spacing: Dp = 16.dp,
    showMdiBranding: Boolean = false
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
            modifier = Modifier.fillMaxSize()
        ) {
            items(list, key = { it.pkg }, contentType = { "game-card" }) { app ->
                GameCard(
                    app,
                    store,
                    tile,
                    app.pkg in store.favorites,
                    app.pkg == focusedPkg,
                    { onPlay(app) },
                    { onPlay(app) },
                    { onOptions(app) },
                    { onFocus(app) },
                    blurIconBackground = true,
                    showMdiBranding = showMdiBranding
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
        // Ukuran panel mengikuti lebar aktual agar tetap seimbang di ponsel dan tablet.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val artworkWidth = (maxWidth * 0.31f).coerceIn(176.dp, 258.dp)
            val descriptionWidth = (maxWidth * 0.23f).coerceIn(148.dp, 204.dp)
            val panelHeight = if (compact) 132.dp else 154.dp
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(panelHeight)
                    .clip(heroShape)
                    .background(Deck.Panel)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(artworkWidth)
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
                    Modifier.width(descriptionWidth).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("DESKRIPSI", color = Deck.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        store.description(focused.pkg).ifBlank { "Belum ada deskripsi. Tambahkan melalui Opsi game." },
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = if (compact) 3 else 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (compact) 158.dp else 218.dp)
                .clip(heroShape)
                .background(Deck.Panel)
        ) {
            ArtworkImage(
                focused, store, "banner",
                Modifier.fillMaxSize().clip(heroShape),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f)))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.08f),
                        0.38f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.88f)
                    )
                )
            )
            Column(
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 15.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(Deck.Accent))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "GAME TERPILIH",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.weight(1f))
                    if (isFav) Text("★ FAVORIT", color = Color(0xFFFFD54F), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    focused.name,
                    color = Color.White,
                    fontSize = if (compact) 19.sp else 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (focused.pkg in store.recent) "Terakhir dimainkan" else "Siap dimainkan",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp
                )
                if (store.description(focused.pkg).isNotBlank() && !compact) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        store.description(focused.pkg),
                        color = Color.White.copy(alpha = 0.84f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(9.dp))
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


@Composable
fun HomeScreen(
    visible: List<AppItem>,
    store: Store,
    focused: AppItem?,
    activeRow: Int,
    onFocus: (AppItem, Int) -> Unit,
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
        // Kartu beranda hanya memilih game. Aksi bermain tersedia di tombol MAINKAN pada HeroPanel.
        val tap: (AppItem, Int) -> Unit = { app, rowIndex -> onFocus(app, rowIndex) }

        if (landscape) {
            // Kembalikan susunan landscape sebelumnya: banner di atas, deretan game di bawah.
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // Batasi lebar banner khusus landscape agar artwork tidak terlalu panoramik.
                    Box(Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                        HeroPanel(focused, store, compact, onPlay, onOptions, landscape = true)
                    }
                }
                if (recent.isNotEmpty()) CardRow(
                    "TERAKHIR DIMAINKAN", recent, focused?.pkg, activeRow == 0, 0, store,
                    cardWidth, compact, tap, onPlay, onOptions, onFocus
                )
                if (favs.isNotEmpty()) CardRow(
                    "FAVORIT", favs, focused?.pkg, activeRow == 1, 1, store,
                    cardWidth, compact, tap, onPlay, onOptions, onFocus
                )
                if (visible.isNotEmpty()) CardRow(
                    "SEMUA GAME", visible, focused?.pkg, activeRow == 2, 2, store,
                    cardWidth, compact, tap, onPlay, onOptions, onFocus
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                HeroPanel(focused, store, compact, onPlay, onOptions)
                if (recent.isNotEmpty()) CardRow(
                    "TERAKHIR DIMAINKAN", recent, focused?.pkg, activeRow == 0, 0, store,
                    cardWidth, compact, tap, onPlay, onOptions, onFocus
                )
                if (favs.isNotEmpty()) CardRow(
                    "FAVORIT", favs, focused?.pkg, activeRow == 1, 1, store,
                    cardWidth, compact, tap, onPlay, onOptions, onFocus
                )
                if (visible.isNotEmpty()) CardRow(
                    "SEMUA GAME", visible, focused?.pkg, activeRow == 2, 2, store,
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val landscape = maxWidth > maxHeight
    val tile = store.cardSizeDp.dp.coerceAtMost(if (landscape) 112.dp else 136.dp)
    val spacing = store.gridSpacingDp.dp.coerceAtMost(if (landscape) 12.dp else 16.dp)

    // Search field is a full-width item in the same lazy grid as the results,
    // so it scrolls away naturally instead of occupying a fixed header row.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(tile + if (landscape) 10.dp else 12.dp),
        contentPadding = PaddingValues(
            horizontal = if (landscape) 18.dp else spacing + 8.dp,
            vertical = if (landscape) 6.dp else 10.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = Modifier.fillMaxSize().focusGroup()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                singleLine = true,
                label = { Text("Cari game atau aplikasi") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (results.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Tidak ada hasil.", color = Deck.TextDim)
                }
            }
        } else {
            items(results, key = { it.pkg }, contentType = { "game-card" }) { app ->
                GameCard(
                    app,
                    store,
                    tile,
                    app.pkg in store.favorites,
                    app.pkg == focusedPkg,
                    { onPlay(app) },
                    { onPlay(app) },
                    { onOptions(app) },
                    { focusedPkg = app.pkg },
                    blurIconBackground = true
                )
            }
        }
    }
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val landscape = maxWidth > maxHeight
    val tile = store.cardSizeDp.dp.coerceAtMost(if (landscape) 112.dp else 136.dp)
    val spacing = store.gridSpacingDp.dp.coerceAtMost(if (landscape) 12.dp else 16.dp)

    // Header, category selector, and app cards share one lazy grid so every
    // library control scrolls away together, including in landscape.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(tile + if (landscape) 10.dp else 12.dp),
        contentPadding = PaddingValues(
            horizontal = if (landscape) 18.dp else spacing + 8.dp,
            vertical = if (landscape) 6.dp else 10.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = Modifier.fillMaxSize().focusGroup()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp, top = 5.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "PERPUSTAKAAN",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp
                    )
                    Text(
                        "${list.size} item ditampilkan",
                        color = Deck.TextDim,
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = onAdd) {
                    Text("+ PULIHKAN", color = Deck.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Triple(true, "GAME", games.size),
                    Triple(false, "APLIKASI", apps.size)
                ).forEach { (isGames, label, count) ->
                    val active = showGames == isGames
                    Column(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) Deck.Accent.copy(alpha = 0.18f) else Deck.Panel.copy(alpha = 0.55f))
                            .gamepadClickable { showGames = isGames; onlyFav = false }
                            .padding(horizontal = 13.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            label,
                            color = if (active) Deck.Accent else Deck.TextDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            count.toString(),
                            color = if (active) Deck.Accent else Deck.TextDim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Box(
                    Modifier.width(46.dp).height(43.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (onlyFav) Deck.Accent.copy(alpha = 0.20f) else Deck.Panel.copy(alpha = 0.55f))
                        .gamepadClickable { onlyFav = !onlyFav },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (onlyFav) "★" else "☆",
                        color = if (onlyFav) Deck.Accent else Deck.TextDim,
                        fontSize = 20.sp
                    )
                }
            }
        }
        if (list.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (onlyFav) "Belum ada favorit di kategori ini."
                        else if (showGames) "Belum ada game."
                        else "Belum ada aplikasi.",
                        color = Deck.TextDim
                    )
                }
            }
        } else {
            items(list, key = { it.pkg }, contentType = { "game-card" }) { app ->
                GameCard(
                    app,
                    store,
                    tile,
                    app.pkg in store.favorites,
                    app.pkg == focusedPkg,
                    { onPlay(app) },
                    { onPlay(app) },
                    { onOptions(app) },
                    { focusedPkg = app.pkg },
                    blurIconBackground = true,
                    showMdiBranding = true
                )
            }
        }
    }
    }
}

@Composable
fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Deck.Panel)
            .gamepadClickable(onClick)
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
    onPortraitWallpaper: () -> Unit,
    onLandscapeWallpaper: () -> Unit,
    onClearPortraitWallpaper: () -> Unit,
    onClearLandscapeWallpaper: () -> Unit,
    onAccent: (String) -> Unit,
    currentAccent: String,
    onMaterialYou: (Boolean) -> Unit,
    hasPortraitWallpaper: Boolean,
    hasLandscapeWallpaper: Boolean,
    store: Store,
    onManageMetadataSources: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().focusGroup().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Text("TAMPILAN", color = Deck.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        Text("Warna elemen", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("Atur warna tombol, indikator fokus, ikon aktif, dan elemen interaktif.", color = Deck.TextDim, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Deck.Panel)
                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Material You", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        "Gunakan warna dinamis dari wallpaper perangkat"
                    else
                        "Memerlukan Android 12 atau lebih baru",
                    color = Deck.TextDim,
                    fontSize = 11.sp
                )
            }
            Switch(
                modifier = Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.ButtonA && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { onMaterialYou(!store.useMaterialYou); true } else false
                },
                checked = store.useMaterialYou,
                onCheckedChange = onMaterialYou,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            )
        }
        Spacer(Modifier.height(12.dp))
        if (!store.useMaterialYou) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            .then(if (selected) Modifier.border(2.dp, Deck.Accent, RoundedCornerShape(50)) else Modifier)
                            .gamepadClickable { onAccent(hex) }
                    )
                    Text(label, color = Deck.TextDim, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        SettingRow(
            "Wallpaper Portrait",
            if (hasPortraitWallpaper) "Ganti gambar latar khusus orientasi Portrait" else "Pilih gambar latar untuk orientasi Portrait",
            onPortraitWallpaper
        )
        Spacer(Modifier.height(8.dp))
        SettingRow(
            "Wallpaper Landscape",
            if (hasLandscapeWallpaper) "Ganti gambar latar khusus orientasi Landscape" else "Pilih gambar latar untuk orientasi Landscape",
            onLandscapeWallpaper
        )
        if (hasPortraitWallpaper) {
            Spacer(Modifier.height(8.dp))
            SettingRow("Hapus wallpaper Portrait", "Gunakan latar charcoal bawaan saat Portrait", onClearPortraitWallpaper)
        }
        if (hasLandscapeWallpaper) {
            Spacer(Modifier.height(8.dp))
            SettingRow("Hapus wallpaper Landscape", "Gunakan latar charcoal bawaan saat Landscape", onClearLandscapeWallpaper)
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
        SettingRow("Pulihkan tampilan default", "Kembalikan ukuran kartu, jarak, sudut, warna elemen, dan efek wallpaper", { store.resetAppearance() })
        Spacer(Modifier.height(24.dp))
        Text("DATABASE GAME", color = Deck.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        SettingRow("Sumber metadata game", "Kelola provider bawaan dan endpoint database kustom", onManageMetadataSources)
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
                                .gamepadClickable { onAdd(app) }
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


@Composable
fun HistoryScreen(visible: List<AppItem>, store: Store, onPlay: (AppItem) -> Unit, onOptions: (AppItem) -> Unit) {
    val byPackage = remember(visible) { visible.associateBy { it.pkg } }
    val history = remember(store.recent, byPackage) { store.recent.mapNotNull(byPackage::get) }
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    Column(Modifier.fillMaxSize().focusGroup().padding(horizontal = if (landscape) 22.dp else 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("RIWAYAT BERMAIN", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("${history.size} game pernah dibuka", color = Deck.TextDim, fontSize = 11.sp)
            }
            TextButton(onClick = { store.clearHistory() }) { Text("BERSIHKAN", color = Deck.Accent, fontSize = 10.sp) }
        }
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◷", color = Deck.TextDim, fontSize = 36.sp)
                    Text("Belum ada riwayat bermain", color = Color.White, fontSize = 14.sp)
                    Text("Game yang kamu buka akan muncul di sini.", color = Deck.TextDim, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.pkg }) { app ->
                    val playedAt = store.lastPlayedAt[app.pkg] ?: 0L
                    val duration = store.playtimeMs[app.pkg] ?: 0L
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Deck.Panel).gamepadClickable { onPlay(app) }.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(app.icon, app.name, Modifier.size(if (landscape) 48.dp else 54.dp).clip(RoundedCornerShape(9.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Terakhir: " + if (playedAt > 0L) android.text.format.DateUtils.getRelativeTimeSpanString(playedAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS) else "—", color = Deck.TextDim, fontSize = 10.sp)
                            Text("Total bermain: " + formatPlaytime(duration), color = Deck.Accent, fontSize = 10.sp)
                        }
                        TextButton(onClick = { onOptions(app) }) { Text("•••", color = Deck.TextDim) }
                    }
                }
            }
        }
    }
}
private fun formatPlaytime(milliseconds: Long): String {
    val minutes = (milliseconds / 60000L).coerceAtLeast(0L)
    val hours = minutes / 60L
    return if (hours > 0L) "${hours}j ${minutes % 60L}m" else "${minutes}m"
}
