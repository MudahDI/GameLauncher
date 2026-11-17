package com.mdh.gamelauncher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Komponen ----------

/** Kartu game potret ala Steam Deck. Kartu yang sedang fokus membesar dan diberi garis putih. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCard(
    app: AppItem,
    width: Dp,
    isFav: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
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
        modifier = Modifier.padding(start = 32.dp, top = 14.dp)
    )
}

@Composable
fun HintText(text: String) {
    Text(
        text,
        color = Deck.TextDim,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
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
    scrollToFocus: Boolean = false,
    isActiveRow: Boolean = false
) {
    val listState = rememberLazyListState()
    val bring = remember { BringIntoViewRequester() }
    val currentList by rememberUpdatedState(list)

    // Khusus mode gamepad: geser baris agar kartu yang dipilih selalu terlihat penuh.
    LaunchedEffect(focusedPkg, scrollToFocus) {
        if (!scrollToFocus) return@LaunchedEffect
        val idx = currentList.indexOfFirst { it.pkg == focusedPkg }
        if (idx >= 0) {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.find { it.index == idx }
            val fully = item != null &&
                item.offset >= info.viewportStartOffset &&
                item.offset + item.size <= info.viewportEndOffset
            if (!fully) listState.animateScrollToItem(idx)
        }
    }
    // Dan gulir halaman ke baris yang sedang aktif.
    LaunchedEffect(focusedPkg, isActiveRow) {
        if (isActiveRow) bring.bringIntoView()
    }

    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bring)) {
        SectionTitle(title, compact)
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(list, key = { it.pkg }) { app ->
                GameCard(
                    app,
                    cardWidth,
                    app.pkg in store.favorites,
                    app.pkg == focusedPkg,
                    { onTap(app) },
                    { onOptions(app) }
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
    pad: GamepadInput? = null
) {
    val gridState = rememberLazyGridState()
    var padPkg by rememberSaveable { mutableStateOf<String?>(null) }
    val currentList by rememberUpdatedState(list)
    val padActive = pad?.active == true
    // Pilihan gamepad; kalau belum ada atau sudah tidak di daftar, mulai dari game pertama.
    val sel = padPkg?.takeIf { p -> list.any { it.pkg == p } } ?: list.firstOrNull()?.pkg
    // Sorotan hanya tampil saat gamepad dipakai, jadi tampilan sentuh tidak berubah.
    val shown = if (padActive) sel else null

    val onKey by rememberUpdatedState<(Pad) -> Unit>({ key ->
        val l = list
        if (l.isNotEmpty()) {
            val cols = (gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.column } ?: 0) + 1
            val cur = l.indexOfFirst { it.pkg == sel }.coerceAtLeast(0)
            when (key) {
                Pad.Left -> { padPkg = l[(cur - 1).coerceAtLeast(0)].pkg }
                Pad.Right -> { padPkg = l[(cur + 1).coerceAtMost(l.lastIndex)].pkg }
                Pad.Up -> { padPkg = l[if (cur - cols >= 0) cur - cols else cur].pkg }
                Pad.Down -> {
                    val target = cur + cols
                    padPkg = l[
                        when {
                            target <= l.lastIndex -> target
                            cur / cols < l.lastIndex / cols -> l.lastIndex
                            else -> cur
                        }
                    ].pkg
                }
                Pad.Confirm -> { onPlay(l[cur]) }
                Pad.Favorite -> { store.toggleFav(l[cur].pkg) }
                else -> {}
            }
        }
    })
    if (pad != null) {
        LaunchedEffect(pad) { pad.events.collect { onKey(it) } }
    }

    LaunchedEffect(shown) {
        val idx = currentList.indexOfFirst { it.pkg == shown }
        if (shown != null && idx >= 0) {
            val info = gridState.layoutInfo
            val item = info.visibleItemsInfo.find { it.index == idx }
            val fully = item != null &&
                item.offset.y >= info.viewportStartOffset &&
                item.offset.y + item.size.height <= info.viewportEndOffset
            if (!fully) gridState.animateScrollToItem(idx)
        }
    }

    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Tidak ada game.", color = Deck.TextDim)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(tile + 12.dp),
            state = gridState,
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(list, key = { it.pkg }) { app ->
                GameCard(
                    app,
                    tile,
                    app.pkg in store.favorites,
                    app.pkg == shown,
                    { onPlay(app) },
                    { onOptions(app) }
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
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Image(
            focused.icon,
            focused.name,
            Modifier.size(iconSize).clip(RoundedCornerShape(20.dp))
        )
        Spacer(Modifier.width(20.dp))
        Column {
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
                color = Deck.TextDim,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayButton("▶  MAINKAN") { onPlay(focused) }
                DeckButton(if (isFav) "★" else "☆") { store.toggleFav(focused.pkg) }
                DeckButton("⋯") { onOptions(focused) }
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
    onOptions: (AppItem) -> Unit,
    pad: GamepadInput? = null
) {
    val recent = store.recent.mapNotNull { p -> visible.find { it.pkg == p } }
    val favs = visible.filter { it.pkg in store.favorites }
    // Urutan baris sama persis dengan yang ditampilkan di bawah (baris kosong dilewati).
    val rows = listOf(recent, favs, visible).filter { it.isNotEmpty() }

    var padRow by rememberSaveable { mutableStateOf(0) }
    val padActive = pad?.active == true
    val activeRow = padRow.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
    val scrollState = rememberScrollState()

    val onKey by rememberUpdatedState<(Pad) -> Unit>({ key ->
        val f = focused
        when (key) {
            Pad.Confirm -> { if (f != null) onPlay(f) }
            Pad.Favorite -> { if (f != null) store.toggleFav(f.pkg) }
            Pad.Left, Pad.Right, Pad.Up, Pad.Down -> {
                if (rows.isNotEmpty()) {
                    // Baris saat ini: baris aktif kalau memuat game terpilih, kalau tidak baris pertama yang memuatnya.
                    val r = if (f != null && rows[activeRow].any { it.pkg == f.pkg }) {
                        activeRow
                    } else {
                        rows.indexOfFirst { row -> f != null && row.any { it.pkg == f.pkg } }
                            .coerceAtLeast(0)
                    }
                    val col = if (f == null) -1 else rows[r].indexOfFirst { it.pkg == f.pkg }
                    val pick: (Int, Int) -> Unit = { row, c ->
                        val nr = row.coerceIn(0, rows.lastIndex)
                        val list = rows[nr]
                        onFocus(list[c.coerceIn(0, list.lastIndex)])
                        padRow = nr
                    }
                    when (key) {
                        Pad.Left -> { pick(r, if (col < 0) 0 else col - 1) }
                        Pad.Right -> { pick(r, if (col < 0) 0 else col + 1) }
                        Pad.Up -> { pick(r - 1, col) }
                        else -> { pick(r + 1, col) }
                    }
                }
            }
            else -> {}
        }
    })
    if (pad != null) {
        LaunchedEffect(pad) { pad.events.collect { onKey(it) } }
    }
    // Kembali ke baris pertama: gulir ke atas supaya panel game terlihat lagi.
    LaunchedEffect(padActive, activeRow) {
        if (padActive && activeRow == 0) scrollState.animateScrollTo(0)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 420.dp
        val cardWidth = if (compact) 104.dp else 132.dp
        // Ketuk sekali untuk memilih, ketuk kartu yang sama (atau tombol MAINKAN) untuk bermain.
        val tap: (AppItem) -> Unit = { a ->
            if (a.pkg == focused?.pkg) onPlay(a) else onFocus(a)
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = 16.dp)
        ) {
            HeroPanel(focused, store, compact, onPlay, onOptions)

            var r = 0
            if (recent.isNotEmpty()) {
                CardRow(
                    "TERAKHIR DIMAINKAN", recent, focused?.pkg, store,
                    cardWidth, compact, tap, onOptions,
                    scrollToFocus = padActive, isActiveRow = padActive && activeRow == r
                )
                r++
            }
            if (favs.isNotEmpty()) {
                CardRow(
                    "FAVORIT", favs, focused?.pkg, store,
                    cardWidth, compact, tap, onOptions,
                    scrollToFocus = padActive, isActiveRow = padActive && activeRow == r
                )
                r++
            }
            if (visible.isNotEmpty()) {
                CardRow(
                    "SEMUA GAME", visible, focused?.pkg, store,
                    cardWidth, compact, tap, onOptions,
                    scrollToFocus = padActive, isActiveRow = padActive && activeRow == r
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
    onOptions: (AppItem) -> Unit,
    pad: GamepadInput? = null
) {
    var q by rememberSaveable { mutableStateOf("") }
    val results =
        if (q.isBlank()) visible else visible.filter { it.name.contains(q, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            singleLine = true,
            label = { Text("Cari game") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
        )
        GameGrid(results, store, onPlay, onOptions, pad = pad)
    }
}

@Composable
fun LibraryScreen(
    visible: List<AppItem>,
    store: Store,
    onPlay: (AppItem) -> Unit,
    onOptions: (AppItem) -> Unit,
    onAdd: () -> Unit,
    pad: GamepadInput? = null
) {
    var onlyFav by rememberSaveable { mutableStateOf(false) }
    val list = if (onlyFav) visible.filter { it.pkg in store.favorites } else visible
    val on = Color.White
    val off = Deck.TextDim
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("KOLEKSI", color = Deck.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onlyFav = false }) {
                Text("Semua", color = if (!onlyFav) on else off)
            }
            TextButton(onClick = { onlyFav = true }) {
                Text("★ Fav", color = if (onlyFav) on else off)
            }
            TextButton(onClick = onAdd) { Text("+ Tambah", color = Deck.Accent) }
        }
        GameGrid(list, store, onPlay, onOptions, pad = pad)
    }
}

@Composable
fun SettingRow(title: String, subtitle: String, onClick: () -> Unit, selected: Boolean = false) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (selected) Modifier.border(2.dp, Color.White, shape) else Modifier)
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
fun SettingsScreen(onAdd: () -> Unit, onReset: () -> Unit, pad: GamepadInput? = null) {
    var sel by rememberSaveable { mutableStateOf(0) }
    val padActive = pad?.active == true
    val onKey by rememberUpdatedState<(Pad) -> Unit>({ key ->
        when (key) {
            Pad.Up -> { sel = 0 }
            Pad.Down -> { sel = 1 }
            Pad.Confirm -> { if (sel == 0) onAdd() else onReset() }
            else -> {}
        }
    })
    if (pad != null) {
        LaunchedEffect(pad) { pad.events.collect { onKey(it) } }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Text("PENGATURAN", color = Deck.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        SettingRow(
            "+ Tambah app ke launcher",
            "Pilih dari semua aplikasi terpasang",
            onAdd,
            selected = padActive && sel == 0
        )
        Spacer(Modifier.height(10.dp))
        SettingRow(
            "Kembalikan daftar otomatis",
            "Batalkan semua tambahan dan penghapusan manual (favorit tetap)",
            onReset,
            selected = padActive && sel == 1
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
        title = { Text("Tambah app") },
        text = {
            if (candidates.isEmpty()) {
                Text("Tidak ada app lain untuk ditambahkan.")
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
