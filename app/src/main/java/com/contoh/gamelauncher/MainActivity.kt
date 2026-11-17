package com.contoh.gamelauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Charcoal = Color(0xFF151515)
private val SurfaceLow = Color(0xFF1D1D1D)
private val SurfaceHigh = Color(0xFF262626)
private val SurfaceSelected = Color(0xFF303030)
private val TextMain = Color(0xFFF1F1F1)
private val TextMuted = Color(0xFF9A9A9A)
private val Accent = Color(0xFFD0D0D0)
private val AccentSoft = Color(0xFFB8B8B8)
private val Danger = Color(0xFFE49A9A)

data class AppItem(
    val name: String,
    val pkg: String,
    val icon: ImageBitmap,
    val isGame: Boolean
)

private fun loadAllApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcherIntent, 0)
        .map { it.activityInfo.applicationInfo }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .map {
            AppItem(
                name = it.loadLabel(pm).toString(),
                pkg = it.packageName,
                icon = it.loadIcon(pm).toBitmap(192, 192).asImageBitmap(),
                isGame = it.category == ApplicationInfo.CATEGORY_GAME
            )
        }
        .sortedBy { it.name.lowercase() }
}

private class LauncherStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private fun readSet(key: String) = prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    private fun readList(key: String) = (prefs.getString(key, "") ?: "").split(",").filter { it.isNotBlank() }

    var added by mutableStateOf(readSet("added"))
        private set
    var removed by mutableStateOf(readSet("removed"))
        private set
    var favorites by mutableStateOf(readSet("favorites"))
        private set
    var recent by mutableStateOf(readList("recent"))
        private set

    private fun persist() {
        prefs.edit()
            .putStringSet("added", HashSet(added))
            .putStringSet("removed", HashSet(removed))
            .putStringSet("favorites", HashSet(favorites))
            .putString("recent", recent.joinToString(","))
            .apply()
    }

    fun add(pkg: String) { added = added + pkg; removed = removed - pkg; persist() }
    fun remove(pkg: String) {
        removed = removed + pkg
        added = added - pkg
        favorites = favorites - pkg
        recent = recent.filterNot { it == pkg }
        persist()
    }
    fun toggleFavorite(pkg: String) {
        favorites = if (pkg in favorites) favorites - pkg else favorites + pkg
        persist()
    }
    fun markPlayed(pkg: String) {
        recent = (listOf(pkg) + recent.filterNot { it == pkg }).take(12)
        persist()
    }
    fun resetManualLists() { added = emptySet(); removed = emptySet(); persist() }
}

private enum class LauncherPage(val label: String, val glyph: String) {
    HOME("Beranda", "⌂"),
    LIBRARY("Koleksi", "▦"),
    SEARCH("Cari", "⌕"),
    SETTINGS("Pengaturan", "⚙")
}

@Composable
private fun BrandMark() {
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(SurfaceHigh),
        contentAlignment = Alignment.Center
    ) {
        Text("G", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = TextMuted, fontSize = 11.sp)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun NavigationItem(page: LauncherPage, selected: Boolean, horizontal: Boolean, onClick: () -> Unit) {
    val tint = if (selected) TextMain else TextMuted
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SurfaceSelected else Color.Transparent)
            .clickable(onClick = onClick)
            .then(if (horizontal) Modifier.padding(horizontal = 14.dp, vertical = 11.dp) else Modifier.padding(horizontal = 7.dp, vertical = 8.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (horizontal) Arrangement.Start else Arrangement.Center
    ) {
        Text(page.glyph, color = tint, fontSize = 19.sp)
        if (horizontal) {
            Spacer(Modifier.width(12.dp))
            Text(page.label, color = tint, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        } else {
            Spacer(Modifier.width(5.dp))
            Text(page.label, color = tint, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun NavigationBar(page: LauncherPage, landscape: Boolean, onSelect: (LauncherPage) -> Unit) {
    if (landscape) {
        Column(
            Modifier.width(190.dp).fillMaxHeight().background(SurfaceLow).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark()
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("GAME", color = TextMuted, fontSize = 8.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Text("LAUNCHER", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("MENU", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 9.dp, bottom = 4.dp))
            LauncherPage.values().forEach { item ->
                NavigationItem(item, item == page, true) { onSelect(item) }
            }
            Spacer(Modifier.weight(1f))
            Text("GAME LAUNCHER 2.0", color = TextMuted, fontSize = 9.sp, modifier = Modifier.padding(start = 5.dp))
        }
    } else {
        Row(
            Modifier.fillMaxWidth().background(SurfaceLow).navigationBarsPadding().padding(horizontal = 7.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LauncherPage.values().forEach { item ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    NavigationItem(item, item == page, false) { onSelect(item) }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: AppItem, modifier: Modifier = Modifier) {
    Image(
        bitmap = app.icon,
        contentDescription = app.name,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(15.dp))
            .background(SurfaceHigh)
            .padding(7.dp)
    )
}

@Composable
private fun AppTile(
    app: AppItem,
    favorite: Boolean,
    compact: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onDetails: () -> Unit
) {
    Column(Modifier.width(if (compact) 104.dp else 132.dp)) {
        Box {
            AppIcon(app, Modifier.fillMaxWidth().clickable(onClick = onOpen))
            if (favorite) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(21.dp)
                        .clip(CircleShape).background(Color(0xCC171717)),
                    contentAlignment = Alignment.Center
                ) { Text("★", color = TextMain, fontSize = 11.sp) }
            }
            Text(
                "⋯",
                color = TextMain,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
                    .clip(CircleShape).background(Color(0xCC171717))
                    .clickable(onClick = onDetails).padding(horizontal = 7.dp, vertical = 1.dp)
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(app.name, color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (app.isGame) "GAME" else "APP", color = TextMuted, fontSize = 8.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun EmptyState(title: String, description: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("◇", color = TextMuted, fontSize = 28.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(description, color = TextMuted, fontSize = 11.sp)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Text(action, color = TextMain, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onAction).padding(8.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, color = TextMuted, fontSize = 10.sp, modifier = Modifier.clickable(onClick = onAction))
        }
    }
}

@Composable
private fun HomePage(
    apps: List<AppItem>,
    store: LauncherStore,
    compact: Boolean,
    onPlay: (AppItem) -> Unit,
    onDetails: (AppItem) -> Unit,
    onLibrary: () -> Unit
) {
    val recentApps = store.recent.mapNotNull { pkg -> apps.firstOrNull { it.pkg == pkg } }
    val favorites = apps.filter { it.pkg in store.favorites }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 18.dp else 28.dp, vertical = 22.dp)
    ) {
        PageTitle("Beranda", "${apps.size} aplikasi dalam koleksi")
        if (recentApps.isNotEmpty()) {
            SectionHeader("Lanjutkan bermain")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                items(recentApps, key = { it.pkg }) { app ->
                    AppTile(app, app.pkg in store.favorites, compact, { onPlay(app) }, { store.toggleFavorite(app.pkg) }, { onDetails(app) })
                }
            }
            Spacer(Modifier.height(26.dp))
        }
        if (favorites.isNotEmpty()) {
            SectionHeader("Favorit")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                items(favorites, key = { it.pkg }) { app ->
                    AppTile(app, true, compact, { onPlay(app) }, { store.toggleFavorite(app.pkg) }, { onDetails(app) })
                }
            }
            Spacer(Modifier.height(26.dp))
        }
        SectionHeader("Koleksi game", "LIHAT SEMUA") { onLibrary() }
        if (apps.isEmpty()) {
            EmptyState("Koleksi masih kosong", "Tambahkan game atau aplikasi dari pengaturan.")
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                items(apps.take(14), key = { it.pkg }) { app ->
                    AppTile(app, app.pkg in store.favorites, compact, { onPlay(app) }, { store.toggleFavorite(app.pkg) }, { onDetails(app) })
                }
            }
        }
    }
}

@Composable
private fun LibraryPage(
    apps: List<AppItem>,
    store: LauncherStore,
    compact: Boolean,
    onPlay: (AppItem) -> Unit,
    onDetails: (AppItem) -> Unit,
    onAdd: () -> Unit
) {
    var filter by rememberSaveable { mutableStateOf("Semua") }
    var sortDescending by rememberSaveable { mutableStateOf(false) }
    val filtered = apps.filter {
        when (filter) {
            "Game" -> it.isGame
            "Aplikasi" -> !it.isGame
            "Favorit" -> it.pkg in store.favorites
            else -> true
        }
    }.let { list -> if (sortDescending) list.sortedByDescending { it.name.lowercase() } else list.sortedBy { it.name.lowercase() } }

    Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 18.dp else 28.dp, vertical = 22.dp)) {
        PageTitle("Koleksi", "${filtered.size} item", trailing = {
            Text("+ Tambah", color = TextMain, fontSize = 11.sp, modifier = Modifier
                .clip(RoundedCornerShape(10.dp)).background(SurfaceHigh).clickable(onClick = onAdd).padding(horizontal = 12.dp, vertical = 9.dp))
        })
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Semua", "Game", "Aplikasi", "Favorit").forEach { item ->
                val selected = filter == item
                Text(
                    item, color = if (selected) Charcoal else TextMuted, fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (selected) Accent else SurfaceLow)
                        .clickable { filter = item }.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text("A–Z", color = TextMuted, fontSize = 10.sp, modifier = Modifier.clickable { sortDescending = !sortDescending })
        }
        Spacer(Modifier.height(18.dp))
        if (filtered.isEmpty()) {
            EmptyState("Tidak ada item", "Coba filter lain atau tambahkan aplikasi.", "Tambah aplikasi", onAdd)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (compact) 96.dp else 122.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.pkg }) { app ->
                    AppTile(app, app.pkg in store.favorites, compact, { onPlay(app) }, { store.toggleFavorite(app.pkg) }, { onDetails(app) })
                }
            }
        }
    }
}

@Composable
private fun SearchPage(
    apps: List<AppItem>,
    store: LauncherStore,
    compact: Boolean,
    onPlay: (AppItem) -> Unit,
    onDetails: (AppItem) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val result = apps.filter { it.name.contains(query, true) || it.pkg.contains(query, true) }
    Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 18.dp else 28.dp, vertical = 22.dp)) {
        PageTitle("Pencarian", "Temukan game dan aplikasi")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Cari nama aplikasi…", color = TextMuted) },
            shape = RoundedCornerShape(13.dp)
        )
        Spacer(Modifier.height(18.dp))
        if (result.isEmpty()) {
            EmptyState(if (query.isBlank()) "Mulai mencari" else "Tidak ditemukan", "Hasil pencarian akan muncul di sini.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (compact) 96.dp else 122.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(result, key = { it.pkg }) { app ->
                    AppTile(app, app.pkg in store.favorites, compact, { onPlay(app) }, { store.toggleFavorite(app.pkg) }, { onDetails(app) })
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(onAdd: () -> Unit, onReset: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        PageTitle("Pengaturan", "Atur koleksi GameLauncher")
        SettingRow("Tambah aplikasi", "Pilih aplikasi yang ingin ditampilkan", onAdd)
        Spacer(Modifier.height(9.dp))
        SettingRow("Pulihkan daftar otomatis", "Reset daftar manual. Favorit dan riwayat tetap disimpan.", onReset)
        Spacer(Modifier.height(28.dp))
        Text("GameLauncher 2.0", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("Minimal by design. Made for play.", color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SettingRow(title: String, description: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceLow)
            .clickable(onClick = onClick).padding(15.dp)
    ) {
        Text(title, color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(description, color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun DetailPage(
    app: AppItem,
    favorite: Boolean,
    compact: Boolean,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 20.dp else 34.dp, vertical = 22.dp)
    ) {
        Text("‹  KEMBALI", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onBack).padding(bottom = 22.dp))
        Box(
            Modifier.fillMaxWidth().height(if (compact) 190.dp else 240.dp)
                .clip(RoundedCornerShape(20.dp)).background(SurfaceLow),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(app, Modifier.size(if (compact) 132.dp else 164.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(app.name, color = TextMain, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(if (app.isGame) "GAME ANDROID" else "APLIKASI ANDROID", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.3.sp)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("▶  MAIN", color = Charcoal, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Accent)
                    .clickable(onClick = onPlay).padding(horizontal = 22.dp, vertical = 13.dp))
            Text(if (favorite) "★  FAVORIT" else "☆  FAVORIT", color = TextMain, fontSize = 11.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(SurfaceHigh)
                    .clickable(onClick = onFavorite).padding(horizontal = 16.dp, vertical = 13.dp))
        }
        Spacer(Modifier.height(26.dp))
        Text("INFORMASI APLIKASI", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(8.dp))
        Text(app.pkg, color = TextMain, fontSize = 11.sp)
        Spacer(Modifier.height(24.dp))
        Text("Hapus dari launcher", color = Danger, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onRemove).padding(vertical = 8.dp))
        Text("Aplikasi tetap terpasang di perangkat. Hanya dihapus dari daftar GameLauncher.", color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun AddAppsDialog(apps: List<AppItem>, onAdd: (AppItem) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Tambah aplikasi") },
        text = {
            if (apps.isEmpty()) {
                Text("Tidak ada aplikasi lain untuk ditambahkan.")
            } else {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    apps.forEach { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onAdd(app) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(app.icon, app.name, Modifier.size(38.dp), contentScale = ContentScale.Fit)
                            Spacer(Modifier.width(12.dp))
                            Text(app.name, color = TextMain, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Selesai") } }
    )
}

@Composable
private fun LauncherRoot(allApps: List<AppItem>) {
    val context = LocalContext.current
    val store = remember { LauncherStore(context) }
    var pageName by rememberSaveable { mutableStateOf(LauncherPage.HOME.name) }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<AppItem?>(null) }

    val page = LauncherPage.values().firstOrNull { it.name == pageName } ?: LauncherPage.HOME
    val visibleApps = allApps.filter { (it.isGame || it.pkg in store.added) && it.pkg !in store.removed }
    val selectedApp = allApps.firstOrNull { it.pkg == selectedPackage }
    val launchApp: (AppItem) -> Unit = { app ->
        context.packageManager.getLaunchIntentForPackage(app.pkg)?.let { intent ->
            store.markPlayed(app.pkg)
            context.startActivity(intent)
        }
    }
    val openDetails: (AppItem) -> Unit = { selectedPackage = it.pkg }

    BoxWithConstraints(Modifier.fillMaxSize().background(Charcoal)) {
        val landscape = maxWidth > maxHeight && maxWidth >= 600.dp
        val compact = !landscape && maxWidth < 600.dp
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (landscape) NavigationBar(page, true) { pageName = it.name }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().height(58.dp).padding(horizontal = if (compact) 18.dp else 28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandMark()
                    Spacer(Modifier.width(10.dp))
                    Text("GAME LAUNCHER", color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Spacer(Modifier.weight(1f))
                    Text("2.0", color = TextMuted, fontSize = 10.sp)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (selectedApp != null) {
                        DetailPage(
                            selectedApp,
                            selectedApp.pkg in store.favorites,
                            compact,
                            { selectedPackage = null },
                            { launchApp(selectedApp) },
                            { store.toggleFavorite(selectedApp.pkg) },
                            { removeTarget = selectedApp }
                        )
                    } else {
                        when (page) {
                            LauncherPage.HOME -> HomePage(visibleApps, store, compact, launchApp, openDetails) { pageName = LauncherPage.LIBRARY.name }
                            LauncherPage.LIBRARY -> LibraryPage(visibleApps, store, compact, launchApp, openDetails) { showAdd = true }
                            LauncherPage.SEARCH -> SearchPage(visibleApps, store, compact, launchApp, openDetails)
                            LauncherPage.SETTINGS -> SettingsPage({ showAdd = true }, { showReset = true })
                        }
                    }
                }
                if (!landscape) NavigationBar(page, false) { pageName = it.name }
            }
        }
    }

    if (showAdd) {
        AddAppsDialog(
            allApps.filter { app -> visibleApps.none { it.pkg == app.pkg } },
            { store.add(it.pkg) },
            { showAdd = false }
        )
    }
    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Pulihkan daftar otomatis?") },
            text = { Text("Daftar tambah dan hapus manual akan direset. Favorit dan riwayat tetap disimpan.") },
            confirmButton = {
                TextButton(onClick = { store.resetManualLists(); showReset = false }) { Text("Pulihkan") }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("Batal") } }
        )
    }
    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Hapus dari GameLauncher?") },
            text = { Text("${target.name} akan dihapus dari daftar launcher. Aplikasi dan datanya tetap ada di perangkat.") },
            confirmButton = {
                TextButton(onClick = {
                    store.remove(target.pkg)
                    removeTarget = null
                    selectedPackage = null
                }) { Text("Hapus dari daftar", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Batal") } }
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Charcoal,
                    surface = SurfaceLow,
                    primary = Accent,
                    onBackground = TextMain,
                    onSurface = TextMain
                )
            ) {
                val context = LocalContext.current
                val apps by produceState<List<AppItem>?>(null) {
                    value = withContext(Dispatchers.Default) { loadAllApps(context) }
                }
                Surface(modifier = Modifier.fillMaxSize(), color = Charcoal) {
                    if (apps == null) {
                        Box(Modifier.fillMaxSize().background(Charcoal), contentAlignment = Alignment.Center) {
                            Text("GameLauncher", color = TextMuted, fontSize = 13.sp)
                        }
                    } else {
                        LauncherRoot(apps!!)
                    }
                }
            }
        }
    }
}
