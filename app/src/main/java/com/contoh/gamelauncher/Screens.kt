package com.contoh.gamelauncher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameTile(
    app: AppItem,
    size: Dp,
    isFav: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        Modifier
            .width(size)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box {
            Image(
                app.icon,
                app.name,
                Modifier.size(size).clip(RoundedCornerShape(16.dp))
            )
            if (isFav) {
                Text(
                    "★",
                    color = Color(0xFFFFD54F),
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            app.name,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BannerTile(
    app: AppItem,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Image(
            app.icon,
            null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(24.dp)
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        Image(
            app.icon,
            app.name,
            Modifier
                .align(Alignment.Center)
                .size(height * 0.6f)
                .clip(RoundedCornerShape(14.dp))
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
                .padding(12.dp)
        ) {
            Text(
                app.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, compact: Boolean) {
    Text(
        text,
        color = Color.White,
        fontSize = if (compact) 20.sp else 26.sp,
        fontWeight = FontWeight.Light,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun HintText(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
fun GameGrid(
    list: List<AppItem>,
    store: Store,
    onPlay: (AppItem) -> Unit,
    onOptions: (AppItem) -> Unit,
    tile: Dp = 100.dp
) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Tidak ada game.", color = Color.White.copy(alpha = 0.6f))
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(tile + 12.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(list, key = { it.pkg }) { app ->
                GameTile(
                    app,
                    tile,
                    app.pkg in store.favorites,
                    { onPlay(app) },
                    { onOptions(app) }
                )
            }
        }
    }
}

@Composable
fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf("🏠" to "Beranda", "🔍" to "Cari", "📚" to "Koleksi", "⚙️" to "Atur")
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF14181F)).height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, (icon, label) ->
            val on = i == selected
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    icon,
                    fontSize = 20.sp,
                    modifier = Modifier.alpha(if (on) 1f else 0.6f)
                )
                Text(
                    label,
                    fontSize = 11.sp,
                    color = if (on) Color.White else Color.White.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(if (on) Color.White else Color.Transparent)
                )
            }
        }
    }
}

// ---------- Layar ----------

@Composable
fun HomeScreen(
    visible: List<AppItem>,
    store: Store,
    onPlay: (AppItem) -> Unit,
    onOptions: (AppItem) -> Unit
) {
    val recent = store.recent.mapNotNull { p -> visible.find { it.pkg == p } }
    val favs = visible.filter { it.pkg in store.favorites }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        val compact = h < 420.dp
        val k = if (compact) 0.8f else 1f

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)
        ) {
            SectionTitle("Terakhir Dimainkan", compact)
            if (recent.isEmpty()) {
                HintText("Belum ada. Mainkan game dari baris \"Semua Game\" di bawah.")
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(recent, key = { _, a -> a.pkg }) { i, app ->
                        if (i == 0) {
                            BannerTile(
                                app, 260.dp * k, 150.dp * k,
                                { onPlay(app) }, { onOptions(app) }
                            )
                        } else {
                            GameTile(
                                app, 110.dp * k, app.pkg in store.favorites,
                                { onPlay(app) }, { onOptions(app) }
                            )
                        }
                    }
                }
            }

            SectionTitle("Favorit", compact)
            if (favs.isEmpty()) {
                HintText("Tahan lama sebuah game, lalu pilih \"Tambah ke favorit\".")
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(favs, key = { it.pkg }) { app ->
                        GameTile(
                            app, 100.dp * k, true,
                            { onPlay(app) }, { onOptions(app) }
                        )
                    }
                }
            }

            SectionTitle("Semua Game", compact)
            if (visible.isEmpty()) {
                HintText("Belum ada game terdeteksi. Tambahkan app lewat tab Atur.")
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(visible, key = { it.pkg }) { app ->
                        GameTile(
                            app, 100.dp * k, app.pkg in store.favorites,
                            { onPlay(app) }, { onOptions(app) }
                        )
                    }
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
        GameGrid(results, store, onPlay, onOptions)
    }
}

@Composable
fun LibraryScreen(
    visible: List<AppItem>,
    store: Store,
    onPlay: (AppItem) -> Unit,
    onOptions: (AppItem) -> Unit,
    onAdd: () -> Unit
) {
    var onlyFav by rememberSaveable { mutableStateOf(false) }
    val list = if (onlyFav) visible.filter { it.pkg in store.favorites } else visible
    val on = Color.White
    val off = Color.White.copy(alpha = 0.5f)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Koleksi", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onlyFav = false }) {
                Text("Semua", color = if (!onlyFav) on else off)
            }
            TextButton(onClick = { onlyFav = true }) {
                Text("★ Fav", color = if (onlyFav) on else off)
            }
            TextButton(onClick = onAdd) { Text("+ Tambah") }
        }
        GameGrid(list, store, onPlay, onOptions, tile = 110.dp)
    }
}

@Composable
fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)
    ) {
        Text(title, color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
    }
}

@Composable
fun SettingsScreen(onAdd: () -> Unit, onReset: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Text("Pengaturan", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        SettingRow(
            "+ Tambah app ke launcher",
            "Pilih dari semua aplikasi terpasang",
            onAdd
        )
        SettingRow(
            "Kembalikan daftar otomatis",
            "Batalkan semua tambahan dan penghapusan manual (favorit tetap)",
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
