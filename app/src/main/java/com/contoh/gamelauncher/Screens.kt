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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.text.style.TextAlign
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
        // Explicit focus target is required for hardware DPAD traversal.
        .focusable()
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
    showTitle: Boolean = true,
    landscapeArtwork: Boolean = false,
    plainIcon: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "cardScale"
    )
    val shape = remember(store.cardCornerDp) { RoundedCornerShape(store.cardCornerDp.dp) }
    // Kartu koleksi selalu memakai cover portrait. Banner hanya untuk hero/detail.
    val cardArtworkKind = "cover"
    val cardArtworkUri = remember(app.pkg, store.artworks) { store.artworkUri(app.pkg, "cover") }
    val displayIcon = app.icon
    Box(
        Modifier
            .then(
                if (plainIcon) Modifier.width(width).height(82.dp)
                else Modifier.size(width, width * 4f / 3f).clip(shape)
                    .then(if (focused) Modifier.border(3.dp, Deck.Accent, shape) else Modifier)
                    .background(Deck.Panel)
            )
            .scale(scale)
            // combinedClickable handles activation; focusable exposes the card
            // as a DPAD focus target on Android devices.
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.ButtonA || event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)) { onClick(); true } else false
            }
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick, onLongClick = onLongClick)
    ) {
        if (plainIcon) {
            Image(
                displayIcon,
                app.name,
                Modifier.align(Alignment.TopCenter)
                    .size(54.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .then(if (focused) Modifier.border(2.dp, Deck.Accent, RoundedCornerShape(13.dp)) else Modifier)
            )
        } else if (cardArtworkUri != null) {
            ArtworkImage(app, store, cardArtworkKind, Modifier.fillMaxSize(), ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
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
                    .align(if (plainIcon) Alignment.BottomCenter else Alignment.BottomStart)
                    .fillMaxWidth()
                    .then(if (plainIcon) Modifier else Modifier.background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
                    ))
                    .padding(horizontal = if (plainIcon) 1.dp else 8.dp, vertical = if (plainIcon) 2.dp else 8.dp)
            ) {
                Text(
                    app.name,
                    color = if (plainIcon) Deck.Text else Color.White,
                    fontSize = if (plainIcon) 10.sp else 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, compact: Boolean) {
    Row(
        Modifier.padding(start = 16.dp, top = 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(4.dp).height(if (compact) 17.dp else 21.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Deck.Accent)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = Deck.Text,
            fontSize = if (compact) 14.sp else 17.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.1.sp
        )
    }
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
    onFocus: (AppItem, Int) -> Unit,
    landscapeCards: Boolean = false
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
                    showTitle = false,
                    landscapeArtwork = landscapeCards
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
    landscape: Boolean = false,
    onDetails: (AppItem) -> Unit = {}
) {
    if (focused == null) {
        HintText("Belum ada game terdeteksi. Tambahkan app lewat tab ATUR.")
        return
    }
    val isFav = focused.pkg in store.favorites
    val heroShape = RoundedCornerShape(18.dp)

    if (landscape) {
        Box(
            Modifier.fillMaxWidth()
                .height(if (compact) 150.dp else 184.dp)
                .clip(heroShape)
                .background(Deck.Panel)
        ) {
            // Hero memakai artwork/banner landscape; cover portrait dipakai pada kartu game.
            ArtworkImage(
                focused, store, "banner",
                Modifier.fillMaxSize(),
                ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.88f),
                    0.48f to Color.Black.copy(alpha = 0.54f),
                    0.78f to Color.Black.copy(alpha = 0.12f),
                    1f to Color.Transparent
                )
            ))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.24f))
            )))
            Column(
                Modifier.align(Alignment.CenterStart).fillMaxWidth(0.58f)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("GAME UNGGULAN", color = Deck.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(5.dp))
                Text(focused.name, color = Color.White, fontSize = if (compact) 19.sp else 25.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (focused.pkg in store.recent) "Terakhir dimainkan" else "Siap dimainkan", color = Color.White.copy(alpha = 0.76f), fontSize = 11.sp)
                if (store.description(focused.pkg).isNotBlank() && !compact) {
                    Spacer(Modifier.height(4.dp))
                    Text(store.description(focused.pkg), color = Color.White.copy(alpha = 0.84f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlayButton("▶  MAINKAN", compact = true) { onPlay(focused) }
                    DeckButton(if (isFav) "♥" else "♡", compact = true) { store.toggleFav(focused.pkg) }
                    DeckButton("ⓘ", compact = true) { onDetails(focused) }
                    DeckButton("⋯", compact = true) { onOptions(focused) }
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
                    if (isFav) Text("♥ FAVORIT", color = Color(0xFFFF6B8A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                    DeckButton(if (isFav) "♥" else "♡", compact = true) { store.toggleFav(focused.pkg) }
                    DeckButton("ⓘ", compact = true) { onDetails(focused) }
                    DeckButton("⋯", compact = true) { onOptions(focused) }
                }
            }
        }
    }
}



@Composable
private fun AppStrip(apps: List<AppItem>, onPlay: (AppItem) -> Unit) {
    Column {
        SectionTitle("APLIKASI", false)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(apps, key = { it.pkg }) { app ->
                Box(
                    Modifier.width(124.dp).height(88.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Deck.PanelLight)
                        .clickable { onPlay(app) },
                    contentAlignment = Alignment.Center
                ) {
                    Image(app.icon, contentDescription = app.name, modifier = Modifier.size(54.dp), contentScale = ContentScale.Fit)
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
    onOptions: (AppItem) -> Unit,
    onDetails: (AppItem) -> Unit = {},
    installedApps: List<AppItem> = emptyList()
) {
    val favs = remember(visible, store.favorites) {
        visible.filter { it.pkg in store.favorites }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 420.dp
        val landscape = maxWidth > maxHeight
        val cardWidth = if (landscape) 132.dp else store.cardSizeDp.dp.coerceAtMost(124.dp)
        val tap: (AppItem, Int) -> Unit = { app, rowIndex -> onFocus(app, rowIndex) }

        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (landscape) 22.dp else 16.dp, vertical = if (landscape) 18.dp else 12.dp)
        ) {
            HeroPanel(focused, store, compact, onPlay, onOptions, landscape = landscape, onDetails = onDetails)
            if (favs.isNotEmpty()) {
                CardRow(
                    "GAME FAVORIT", favs, focused?.pkg, activeRow == 1, 1, store,
                    cardWidth, compact, tap, onPlay, onOptions, onFocus,
                    landscapeCards = landscape
                )
            } else if (visible.isNotEmpty()) {
                CardRow(
                    "GAME", visible, focused?.pkg, activeRow == 2, 2, store,
                    cardWidth, compact, tap, onPlay, onOptions, onFocus,
                    landscapeCards = landscape
                )
            }
            if (installedApps.isNotEmpty()) {
                Spacer(Modifier.height(if (landscape) 4.dp else 8.dp))
                AppStrip(installedApps, onPlay)
            }
        }
    }
}

@Composable
fun GameDetailScreen(
    app: AppItem,
    store: Store,
    onBack: () -> Unit,
    onPlay: (AppItem) -> Unit,
    onOptions: (AppItem) -> Unit
) {
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val description = store.description(app.pkg).ifBlank {
        "Belum ada deskripsi untuk game ini. Kamu bisa menambahkannya melalui pengelolaan metadata."
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = if (landscape) maxHeight * 0.78f else (maxHeight * 0.42f).coerceIn(220.dp, 340.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(heroHeight).clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))) {
                ArtworkImage(app, store, "banner", Modifier.fillMaxSize(), ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.28f), Color.Black.copy(alpha = 0.94f)))))
                Row(Modifier.align(Alignment.TopStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onBack).padding(horizontal = 14.dp, vertical = 9.dp)) {
                        Text("‹  KEMBALI", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
                    Text("DETAIL GAME", color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(app.name, color = Color.White, fontSize = if (landscape) 28.sp else 25.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Aplikasi Android  •  " + if (app.pkg in store.recent) "Pernah dimainkan" else "Siap dimainkan", color = Color.White.copy(alpha = 0.76f), fontSize = 12.sp)
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlayButton("▶  MAINKAN") { onPlay(app) }
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Deck.Panel).clickable { store.toggleFav(app.pkg) }, contentAlignment = Alignment.Center) {
                        Text(if (app.pkg in store.favorites) "♥" else "♡", color = if (app.pkg in store.favorites) Color(0xFFFF6B8A) else Deck.Text, fontSize = 21.sp)
                    }
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Deck.Panel).clickable { onOptions(app) }, contentAlignment = Alignment.Center) {
                        Text("⋯", color = Deck.Text, fontSize = 22.sp)
                    }
                }
                Text("TENTANG GAME", color = Deck.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text(description, color = Deck.Text, fontSize = 14.sp, lineHeight = 21.sp)
                Text("INFORMASI APLIKASI", color = Deck.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Deck.Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Nama paket", color = Deck.TextDim, fontSize = 11.sp)
                    Text(app.pkg, color = Deck.Text, fontSize = 13.sp)
                    Text("Status favorit", color = Deck.TextDim, fontSize = 11.sp)
                    Text(if (app.pkg in store.favorites) "Favorit" else "Belum ditandai", color = Deck.Text, fontSize = 13.sp)
                    Text("Total waktu bermain", color = Deck.TextDim, fontSize = 11.sp)
                    Text(formatPlaytime(store.playtimeMs[app.pkg] ?: 0L), color = Deck.Text, fontSize = 13.sp)
                }
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
    val tile = if (landscape) 68.dp else 76.dp
    val spacing = 10.dp

    // Search field is a full-width item in the same lazy grid as the results,
    // so it scrolls away naturally instead of occupying a fixed header row.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(tile + 12.dp),
        contentPadding = PaddingValues(
            horizontal = 12.dp,
            vertical = 6.dp
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
                    plainIcon = true
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
    val tile = if (landscape) 68.dp else 76.dp
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
                        color = Deck.Text,
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
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Triple(true, "🎮", "Game (" + games.size + ")"),
                    Triple(false, "▦", "Aplikasi (" + apps.size + ")")
                ).forEach { (isGames, icon, label) ->
                    val active = showGames == isGames
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .gamepadClickable { showGames = isGames; onlyFav = false }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(icon, fontSize = 15.sp)
                        Text(
                            label,
                            color = if (active) Deck.Accent else Deck.TextDim,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .gamepadClickable { onlyFav = !onlyFav }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (onlyFav) "♥" else "♡",
                        color = if (onlyFav) Color(0xFFFF5B78) else Deck.TextDim,
                        fontSize = 19.sp
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
                    plainIcon = true,
                    showMdiBranding = false
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
        Text(title, color = Deck.Text, fontSize = 17.sp)
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
            Text(title, color = Deck.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
    isLightTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    hasPortraitWallpaper: Boolean,
    hasLandscapeWallpaper: Boolean,
    store: Store,
    onManageMetadataSources: () -> Unit,
    onRestart: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().focusGroup().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Text("TAMPILAN", color = Deck.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        Text("Tema aplikasi", color = Deck.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("Pilih tampilan gelap atau terang untuk seluruh launcher.", color = Deck.TextDim, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(false to "Gelap", true to "Terang").forEach { (light, label) ->
                val selected = isLightTheme == light
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Deck.Accent.copy(alpha = 0.18f) else Deck.Panel)
                        .then(if (selected) Modifier.border(1.dp, Deck.Accent, RoundedCornerShape(10.dp)) else Modifier)
                        .clickable { onThemeChange(light) }
                        .padding(vertical = 13.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (light) "☀" else "☾", color = if (selected) Deck.Accent else Deck.Text, fontSize = 20.sp)
                    Text(label, color = if (selected) Deck.Accent else Deck.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Warna elemen", color = Deck.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
                Text("Material You", color = Deck.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
        Spacer(Modifier.height(10.dp))
        SettingRow(
            "Restart MDI Launcher",
            "Mulai ulang launcher untuk menerapkan perubahan atau menyegarkan tampilan",
            onRestart
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
                Text("RIWAYAT BERMAIN", color = Deck.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("${history.size} game pernah dibuka", color = Deck.TextDim, fontSize = 11.sp)
            }
            TextButton(onClick = { store.clearHistory() }) { Text("BERSIHKAN", color = Deck.Accent, fontSize = 10.sp) }
        }
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◷", color = Deck.TextDim, fontSize = 36.sp)
                    Text("Belum ada riwayat bermain", color = Deck.Text, fontSize = 14.sp)
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
                            Text(app.name, color = Deck.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
