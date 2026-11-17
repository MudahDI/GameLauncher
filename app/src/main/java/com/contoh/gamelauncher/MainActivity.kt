package com.contoh.gamelauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- Data ----------

data class AppItem(
    val name: String,
    val pkg: String,
    val icon: ImageBitmap,
    val isGame: Boolean
)

fun loadAllApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.applicationInfo }
        .filter { it.packageName != ctx.packageName }
        .distinctBy { it.packageName }
        .map {
            AppItem(
                it.loadLabel(pm).toString(),
                it.packageName,
                it.loadIcon(pm).toBitmap(192, 192).asImageBitmap(),
                it.category == ApplicationInfo.CATEGORY_GAME
            )
        }
        .sortedBy { it.name.lowercase() }
}

class Store(ctx: Context) {
    private val sp = ctx.applicationContext
        .getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private fun readSet(key: String): Set<String> =
        sp.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    private fun readList(key: String): List<String> =
        (sp.getString(key, "") ?: "").split(",").filter { it.isNotEmpty() }

    var added by mutableStateOf(readSet("added"))
    var removed by mutableStateOf(readSet("removed"))
    var favorites by mutableStateOf(readSet("favorites"))
    var recent by mutableStateOf(readList("recent"))

    private fun persist() {
        sp.edit()
            .putStringSet("added", HashSet(added))
            .putStringSet("removed", HashSet(removed))
            .putStringSet("favorites", HashSet(favorites))
            .putString("recent", recent.joinToString(","))
            .apply()
    }

    fun addApp(pkg: String) {
        added = added + pkg
        removed = removed - pkg
        persist()
    }

    fun removeApp(pkg: String) {
        removed = removed + pkg
        added = added - pkg
        favorites = favorites - pkg
        recent = recent - pkg
        persist()
    }

    fun toggleFav(pkg: String) {
        favorites = if (pkg in favorites) favorites - pkg else favorites + pkg
        persist()
    }

    fun markPlayed(pkg: String) {
        recent = (listOf(pkg) + recent.filter { it != pkg }).take(12)
        persist()
    }

    fun resetList() {
        added = emptySet()
        removed = emptySet()
        persist()
    }
}

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
    val items = listOf("⌂" to "Beranda", "⌕" to "Cari", "▦" to "Koleksi", "⚙" to "Atur")
    Row(Modifier.fillMaxWidth().background(Color(0xFF0D1420)).height(68.dp).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { i, (icon, label) ->
            val active = i == selected
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(if (active) Color(0xFF1C2A40) else Color.Transparent).clickable { onSelect(i) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(icon, color = if (active) Color(0xFF9FC2FF) else Color(0xFF78869B), fontSize = 22.sp)
                Text(label, color = if (active) Color.White else Color(0xFF8995A7), fontSize = 10.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
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
    val featured = recent.firstOrNull() ?: visible.firstOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("GAME LIBRARY", color = Color(0xFF91A4C2), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Beranda", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(visible.size.toString() + " GAME", color = Color(0xFFB8C5D8), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 9.dp))
        }
        if (featured != null) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp).height(218.dp).clip(RoundedCornerShape(22.dp))) {
                Image(featured.icon, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(30.dp))
                Box(Modifier.fillMaxSize().background(Color(0xD9091424)))
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xF20A1424), Color(0xA60A1424), Color(0x330A1424)))))
                Image(featured.icon, featured.name, contentScale = ContentScale.Crop,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 34.dp).size(146.dp).clip(RoundedCornerShape(22.dp)))
                Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.64f).padding(start = 26.dp, end = 8.dp)) {
                    Text(if (recent.isNotEmpty()) "LANJUTKAN PERMAINAN" else "PILIHAN GAME", color = Color(0xFF9CB8E8), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(featured.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(15.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFF9FC2FF)).clickable { onPlay(featured) }.padding(horizontal = 17.dp, vertical = 10.dp)) {
                            Text("▶  Mainkan", color = Color(0xFF091321), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.13f)).clickable { onOptions(featured) }.padding(horizontal = 13.dp, vertical = 10.dp)) {
                            Text("•••", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp).height(150.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF17243A)), contentAlignment = Alignment.Center) {
                Text("Belum ada game yang terdeteksi", color = Color(0xFFB8C5D8), fontSize = 14.sp)
            }
        }
        if (recent.isNotEmpty()) HomeGameSection("Terakhir dimainkan", recent, store, onPlay, onOptions)
        if (favs.isNotEmpty()) HomeGameSection("Favorit", favs, store, onPlay, onOptions)
        Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Semua Game", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Pilih game untuk mulai bermain", color = Color(0xFF8492A8), fontSize = 11.sp)
            }
            Text(visible.size.toString() + " TERPASANG", color = Color(0xFF8492A8), fontSize = 9.sp, letterSpacing = 1.sp)
        }
        if (visible.isEmpty()) HintText("Tambahkan game melalui menu Koleksi atau Pengaturan.")
        else LazyRow(contentPadding = PaddingValues(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            items(visible, key = { it.pkg }) { app -> ConsoleGameCard(app, app.pkg in store.favorites, { onPlay(app) }, { onOptions(app) }) }
        }
    }
}

@Composable
private fun HomeGameSection(title: String, games: List<AppItem>, store: Store, onPlay: (AppItem) -> Unit, onOptions: (AppItem) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(games.size.toString(), color = Color(0xFF8492A8), fontSize = 11.sp)
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(games, key = { it.pkg }) { app -> ConsoleGameCard(app, app.pkg in store.favorites, { onPlay(app) }, { onOptions(app) }) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConsoleGameCard(app: AppItem, favorite: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(Modifier.width(122.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(Modifier.fillMaxWidth().height(122.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xFF202B3D))) {
            Image(app.icon, app.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (favorite) Text("★", color = Color(0xFFFFD36E), fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(app.name, color = Color(0xFFE7ECF4), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

// ---------- Root ----------

@Composable
fun LauncherApp(allApps: List<AppItem>) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var selected by remember { mutableStateOf<AppItem?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val visible = allApps.filter { (it.isGame || it.pkg in store.added) && it.pkg !in store.removed }
    val visiblePkgs = visible.map { it.pkg }.toSet()
    val candidates = allApps.filter { it.pkg !in visiblePkgs }
    val backdrop = store.recent.firstNotNullOfOrNull { p -> visible.find { it.pkg == p } }
        ?: visible.firstOrNull()

    val play: (AppItem) -> Unit = { a ->
        ctx.packageManager.getLaunchIntentForPackage(a.pkg)?.let {
            ctx.startActivity(it)
            store.markPlayed(a.pkg)
        }
    }
    val options: (AppItem) -> Unit = { a -> selected = a }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF17243A), Color(0xFF0B111C), Color(0xFF080D15))))) {
        if (backdrop != null) {
            Image(
                bitmap = backdrop.icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(90.dp).alpha(0.12f)
            )
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> HomeScreen(visible, store, play, options)
                    1 -> SearchScreen(visible, store, play, options)
                    2 -> LibraryScreen(visible, store, play, options) { showPicker = true }
                    else -> SettingsScreen(
                        onAdd = { showPicker = true },
                        onReset = { confirmReset = true }
                    )
                }
            }
            BottomBar(tab) { tab = it }
        }
    }

    if (showPicker) {
        AddAppDialog(candidates, { store.addApp(it.pkg) }, { showPicker = false })
    }

    selected?.let { a ->
        val fav = a.pkg in store.favorites
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(a.name) },
            text = {
                Column {
                    TextButton(onClick = { store.toggleFav(a.pkg); selected = null }) {
                        Text(if (fav) "★ Hapus dari favorit" else "☆ Tambah ke favorit")
                    }
                    TextButton(onClick = { store.removeApp(a.pkg); selected = null }) {
                        Text("Hapus dari launcher")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Tutup") } }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Kembalikan daftar otomatis?") },
            text = { Text("Semua app yang kamu tambah atau hapus manual akan dikembalikan ke deteksi otomatis. Favorit tidak berubah.") },
            confirmButton = {
                TextButton(onClick = { store.resetList(); confirmReset = false }) { Text("Kembalikan") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Batal") } }
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val ctx = LocalContext.current
                val apps by produceState<List<AppItem>?>(initialValue = null) {
                    value = withContext(Dispatchers.Default) { loadAllApps(ctx) }
                }
                val list = apps
                if (list == null) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF0B0F1A)))
                } else {
                    LauncherApp(list)
                }
            }
        }
    }
}
