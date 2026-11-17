package com.contoh.gamelauncher

import android.view.KeyEvent as AndroidKeyEvent
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
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
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
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
    onLongClick: () -> Unit,
    onFocus: () -> Unit = {}
) {
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "cardScale")
    val shape = RoundedCornerShape(10.dp)
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
                        onClick()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_BUTTON_X -> {
                        onLongClick()
                        true
                    }
                    else -> false
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
    onOptions: (AppItem) -> Unit,
    onFocus: (AppItem) -> Unit
) {
    SectionTitle(title, compact)
    LazyRow(
        modifier = Modifier.focusGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(list, key = { it.pkg }) { app ->
            GameCard(
                app,
                store,
                cardWidth,
                app.pkg in store.favorites,
                app.pkg == focusedPkg,
                { onTap(app) },
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
    emptyLabel: String = "Tidak ada item."
) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyLabel, color = Deck.TextDim)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(tile + 12.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().focusGroup()
        ) {
            items(list, key = { it.pkg }) { app ->
                GameCard(
                    app,
                    store,
                    tile,
                    app.pkg in store.favorites,
                    app.pkg == focusedPkg,
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
    onOptions: (AppItem) -> Unit
) {
    if (focused == null) {
        HintText("Belum ada game terdeteksi. Tambahkan app lewat tab ATUR.")
        return
    }
    val iconSize = if (compact) 84.dp else 112.dp
    val isFav = focused.pkg in store.favorites
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 148.dp else 196.dp)
            .clip(RoundedCornerShape(14.dp))
    ) {
        ArtworkImage(
            focused, store, "banner",
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color.Black.copy(alpha = 0.86f), Color.Black.copy(alpha = 0.48f), Color.Transparent)
                )
            )
        )
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
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
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlayButton("▶  MAINKAN") { onPlay(focused) }
                    DeckButton(if (isFav) "★" else "☆") { store.toggleFav(focused.pkg) }
                    DeckButton("⋯") { onOptions(focused) }
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
    onFocus: (AppItem) -> Unit,
    onPlay: (AppItem) -> Unit,
    onOptions: (AppItem) -> Unit
) {
    val recent = store.recent.mapNotNull { p -> visible.find { it.pkg == p } }
    val favs = visible.filter { it.pkg in store.favorites }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 420.dp
        val cardWidth = if (compact) 104.dp else 132.dp
        // Ketuk sekali untuk memilih, ketuk kartu yang sama (atau tombol MAINKAN) untuk bermain.
        val tap: (AppItem) -> Unit = { a ->
            if (a.pkg == focused?.pkg) onPlay(a) else onFocus(a)
        }

        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Banner diberi batas lebar di landscape agar tidak menjadi terlalu panoramik.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().widthIn(max = 960.dp)) {
                    HeroPanel(focused, store, compact, onPlay, onOptions)
                }
            }

            if (recent.isNotEmpty()) {
                CardRow(
                    "TERAKHIR DIMAINKAN", recent, focused?.pkg, store,
                    cardWidth, compact, tap, onOptions, onFocus
                )
            }
            if (favs.isNotEmpty()) {
                CardRow(
                    "FAVORIT", favs, focused?.pkg, store,
                    cardWidth, compact, tap, onOptions, onFocus
                )
            }
            if (visible.isNotEmpty()) {
                CardRow(
                    "SEMUA GAME", visible, focused?.pkg, store,
                    cardWidth, compact, tap, onOptions, onFocus
                )
            }
            HintText("Ketuk untuk memilih · ketuk lagi untuk bermain · tahan untuk opsi")
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
    val results =
        if (q.isBlank()) visible else visible.filter { it.name.contains(q, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            singleLine = true,
            label = { Text("Cari game atau aplikasi") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
        )
        GameGrid(results, store, onPlay, onOptions, focusedPkg = focusedPkg, onFocus = { focusedPkg = it.pkg }, emptyLabel = "Tidak ada hasil.")
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
    val list = if (onlyFav) source.filter { it.pkg in store.favorites } else source
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
            focusedPkg = focusedPkg,
            onFocus = { focusedPkg = it.pkg },
            emptyLabel = if (showGames) "Belum ada game." else "Belum ada aplikasi."
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
fun SettingsScreen(onAdd: () -> Unit, onReset: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Text("PENGATURAN", color = Deck.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
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
