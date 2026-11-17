package com.contoh.gamelauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
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

private val Background = Color(0xFF0D1014)
private val Panel = Color(0xFF171C22)
private val Raised = Color(0xFF222B34)
private val Foreground = Color(0xFFF3F6F8)
private val Muted = Color(0xFF8996A3)
private val Accent = Color(0xFFB8F36B)
private val AccentDark = Color(0xFF26391B)
private val Danger = Color(0xFFFF8C8C)

data class AppItem(val name: String, val pkg: String, val icon: ImageBitmap, val isGame: Boolean)

private fun loadAllApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { it.activityInfo.applicationInfo }.filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }.map {
            AppItem(it.loadLabel(pm).toString(), it.packageName,
                it.loadIcon(pm).toBitmap(192, 192).asImageBitmap(), it.category == ApplicationInfo.CATEGORY_GAME)
        }.sortedBy { it.name.lowercase() }
}

private class LauncherStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private fun set(key: String) = prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    private fun list(key: String) = (prefs.getString(key, "") ?: "").split(",").filter { it.isNotBlank() }
    var added by mutableStateOf(set("added")); private set
    var removed by mutableStateOf(set("removed")); private set
    var favorites by mutableStateOf(set("favorites")); private set
    var recent by mutableStateOf(list("recent")); private set
    private fun save() {
        prefs.edit().putStringSet("added", HashSet(added)).putStringSet("removed", HashSet(removed))
            .putStringSet("favorites", HashSet(favorites)).putString("recent", recent.joinToString(",")).apply()
    }
    fun add(pkg: String) { added += pkg; removed -= pkg; save() }
    fun remove(pkg: String) { removed += pkg; added -= pkg; favorites -= pkg; recent = recent - pkg; save() }
    fun favorite(pkg: String) { favorites = if (pkg in favorites) favorites - pkg else favorites + pkg; save() }
    fun played(pkg: String) { recent = (listOf(pkg) + recent.filterNot { it == pkg }).take(12); save() }
    fun reset() { added = emptySet(); removed = emptySet(); save() }
}

private enum class Page(val label: String, val glyph: String) {
    HOME("Beranda", "⌂"), LIBRARY("Koleksi", "▦"), SEARCH("Cari", "⌕"), SETTINGS("Setelan", "⚙")
}

@Composable private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Accent), contentAlignment = Alignment.Center) {
            Text("G", color = Background, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        Column {
            Text("GAME", color = Foreground, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("LAUNCHER", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.1.sp)
        }
    }
}

@Composable private fun NavItem(page: Page, selected: Boolean, horizontal: Boolean, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(13.dp)).background(if (selected) AccentDark else Color.Transparent)
        .clickable(onClick = onClick).padding(horizontal = if (horizontal) 14.dp else 9.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(page.glyph, color = if (selected) Accent else Muted, fontSize = 18.sp)
        if (horizontal) {
            Spacer(Modifier.width(8.dp))
            Text(page.label, color = if (selected) Accent else Muted, fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable private fun TopBar(landscape: Boolean, page: Page, select: (Page) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF111820)).padding(horizontal = 24.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Brand()
        Spacer(Modifier.width(34.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Page.values().forEach { item ->
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (item == page) Color(0xFF29343F) else Color.Transparent)
                        .clickable { select(item) }.padding(horizontal = 15.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(item.label.uppercase(), color = if (item == page) Foreground else Muted,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    if (item == page) Spacer(Modifier.padding(top = 5.dp).width(22.dp).height(2.dp).background(Accent))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("●  GAMING MODE", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp, modifier = Modifier.clip(CircleShape).background(Color(0xFF1D2922))
                .padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable private fun BottomBar(page: Page, select: (Page) -> Unit) {
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 15.dp, vertical = 9.dp)
        .clip(RoundedCornerShape(21.dp)).background(Panel).padding(5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Page.values().forEach { item -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            NavItem(item, item == page, false) { select(item) }
        } }
    }
}

@Composable private fun AppIcon(app: AppItem, modifier: Modifier = Modifier) {
    Image(app.icon, app.name, modifier.aspectRatio(1f).clip(RoundedCornerShape(19.dp))
        .background(Color(0xFF29333D)).padding(7.dp), contentScale = ContentScale.Fit)
}

@Composable private fun Eyebrow(text: String) {
    Text(text.uppercase(), color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
}

@Composable private fun Heading(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Foreground, fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        action?.invoke()
    }
}

@Composable private fun SectionTitle(title: String, action: String? = null, click: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Foreground, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null && click != null) Text(action, color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = click).padding(5.dp))
    }
}

@Composable private fun PlayButton(label: String = "MAIN SEKARANG", click: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(13.dp)).background(Accent).clickable(onClick = click)
        .padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("▶", color = Background, fontSize = 10.sp)
        Text(label, color = Background, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

@Composable private fun AppCard(app: AppItem, favorite: Boolean, compact: Boolean,
    play: () -> Unit, toggle: () -> Unit, details: () -> Unit) {
    Column(Modifier.width(if (compact) 112.dp else 136.dp).focusable()) {
        Box {
            AppIcon(app, Modifier.fillMaxWidth().clickable(onClick = play))
            if (favorite) Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(23.dp)
                .clip(CircleShape).background(Color(0xDD10161B)), contentAlignment = Alignment.Center) {
                Text("★", color = Accent, fontSize = 12.sp)
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(CircleShape)
                .background(Color(0xDD10161B)).clickable(onClick = details).padding(6.dp)) {
                Text("•••", color = Foreground, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(app.name, color = Foreground, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (app.isGame) "GAME" else "APP", color = Muted, fontSize = 8.sp, letterSpacing = 1.2.sp)
    }
}

@Composable private fun FeaturedCard(app: AppItem?, compact: Boolean, play: (AppItem) -> Unit, details: (AppItem) -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF293D32), Color(0xFF1B2930), Color(0xFF202630))))
        .padding(if (compact) 18.dp else 23.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("YOUR PLAY SPACE")
                Spacer(Modifier.weight(1f))
                Text("READY TO PLAY", color = Accent, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(Color(0x3324F0A0)).padding(horizontal = 9.dp, vertical = 6.dp))
            }
            Spacer(Modifier.height(18.dp))
            if (app == null) {
                Text("Saatnya bermain.", color = Foreground, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Semua game favoritmu, dalam satu tempat.", color = Color(0xFFB6C6B8), fontSize = 11.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, Modifier.size(if (compact) 86.dp else 104.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("PILIHAN TERAKHIR", color = Color(0xFFB6C6B8), fontSize = 8.sp, letterSpacing = 1.4.sp)
                        Text(app.name, color = Foreground, fontSize = if (compact) 19.sp else 23.sp,
                            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (app.isGame) "Game • Android" else "Aplikasi • Android", color = Muted, fontSize = 10.sp)
                        Spacer(Modifier.height(3.dp))
                        PlayButton { play(app) }
                    }
                }
                Text("Lihat detail  ↗", color = Color(0xFFB6C6B8), fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.End).clickable { details(app) }.padding(4.dp))
            }
        }
    }
}

@Composable private fun EmptyState(title: String, description: String, action: String? = null, click: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Raised), contentAlignment = Alignment.Center) {
            Text("◇", color = Accent, fontSize = 25.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = Foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(description, color = Muted, fontSize = 10.sp)
        if (action != null && click != null) Text(action, color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = click).padding(12.dp))
    }
}

@Composable private fun HomePage(apps: List<AppItem>, store: LauncherStore, compact: Boolean,
    play: (AppItem) -> Unit, details: (AppItem) -> Unit, library: () -> Unit) {
    val recent = store.recent.mapNotNull { pkg -> apps.firstOrNull { it.pkg == pkg } }
    val favorites = apps.filter { it.pkg in store.favorites }
    val games = apps.filter { it.isGame }
    val featured = recent.firstOrNull() ?: favorites.firstOrNull() ?: games.firstOrNull() ?: apps.firstOrNull()
    Column(Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("GOOD AFTERNOON", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Beranda", color = Foreground, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            }
            Text("${games.size} GAMES  •  ${apps.size} APPS", color = Muted, fontSize = 9.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        if (featured != null) {
            Box(Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 28.dp)
                .height(if (compact) 270.dp else 310.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF354A43), Color(0xFF202D38), Color(0xFF121A23))))) {
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                    listOf(Color(0xAA0D1117), Color(0x2210141B), Color(0xAA0D1117)))))
                Row(Modifier.fillMaxSize().padding(if (compact) 20.dp else 32.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (recent.isNotEmpty()) "PICK UP WHERE YOU LEFT OFF" else "FEATURED IN YOUR LIBRARY",
                            color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                        Text(featured.name, color = Foreground, fontSize = if (compact) 27.sp else 38.sp,
                            fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (featured.isGame) "Game • Android" else "Application • Android",
                            color = Color(0xFFB6C0C9), fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PlayButton("PLAY NOW") { play(featured) }
                            Text("DETAILS", color = Foreground, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x553A4652))
                                    .clickable { details(featured) }.padding(horizontal = 17.dp, vertical = 13.dp))
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    AppIcon(featured, Modifier.width(if (compact) 112.dp else 180.dp))
                }
            }
        } else EmptyState("Your library is empty", "Add games and apps to get started.")
        Spacer(Modifier.height(30.dp))
        Column(Modifier.padding(horizontal = if (compact) 18.dp else 30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("RECENTLY PLAYED", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                    Text("Continue playing", color = Foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Text("VIEW LIBRARY  ›", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = library).padding(8.dp))
            }
            Spacer(Modifier.height(15.dp))
            val continueItems = if (recent.isNotEmpty()) recent else games
            if (continueItems.isEmpty()) EmptyState("No games yet", "Your recently played games will appear here.")
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(continueItems.take(12), key = { it.pkg }) { app ->
                    Column(Modifier.width(if (compact) 116.dp else 150.dp).clickable { play(app) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1.18f).clip(RoundedCornerShape(13.dp))
                            .background(Raised).padding(13.dp), contentAlignment = Alignment.Center) {
                            AppIcon(app, Modifier.fillMaxHeight())
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(app.name, color = Foreground, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("PLAY", color = Muted, fontSize = 8.sp, letterSpacing = 1.2.sp)
                    }
                }
            }
            if (favorites.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text("YOUR FAVORITES", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                Text("Favorite games", color = Foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(favorites, key = { it.pkg }) { app ->
                        Column(Modifier.width(112.dp).clickable { play(app) }) {
                            AppIcon(app, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(7.dp))
                            Text(app.name, color = Foreground, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable private fun FilterChip(label: String, selected: Boolean, click: () -> Unit) {
    Text(label, color = if (selected) Background else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(CircleShape).background(if (selected) Accent else Panel)
            .clickable(onClick = click).padding(horizontal = 14.dp, vertical = 9.dp))
}

@Composable private fun AppGrid(apps: List<AppItem>, store: LauncherStore, compact: Boolean,
    play: (AppItem) -> Unit, details: (AppItem) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Adaptive(if (compact) 100.dp else 126.dp),
        contentPadding = PaddingValues(bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxSize()) {
        items(apps, key = { it.pkg }) { a -> AppCard(a, a.pkg in store.favorites, compact,
            { play(a) }, { store.favorite(a.pkg) }, { details(a) }) }
    }
}

@Composable private fun LibraryPage(apps: List<AppItem>, store: LauncherStore, compact: Boolean,
    play: (AppItem) -> Unit, details: (AppItem) -> Unit, add: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("Semua") }
    var descending by rememberSaveable { mutableStateOf(false) }
    val filtered = apps.filter { when (filter) {
        "Game" -> it.isGame
        "Aplikasi" -> !it.isGame
        "Favorit" -> it.pkg in store.favorites
        else -> true
    } }.let { if (descending) it.sortedByDescending { app -> app.name.lowercase() } else it.sortedBy { app -> app.name.lowercase() } }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (compact) 112.dp else 154.dp),
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(start = if (compact) 18.dp else 30.dp, end = if (compact) 18.dp else 30.dp, bottom = 30.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp)) {
                Text("YOUR COLLECTION", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Library", color = Foreground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("${filtered.size} titles in your library", color = Muted, fontSize = 11.sp)
                    }
                    Text("+ ADD GAME", color = Background, fontSize = 9.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Accent)
                            .clickable(onClick = add).padding(horizontal = 15.dp, vertical = 11.dp))
                }
                Spacer(Modifier.height(17.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("Semua", "Game", "Aplikasi", "Favorit").forEach { option ->
                        FilterChip(option, filter == option) { filter = option }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(if (descending) "Z–A" else "A–Z", color = Muted, fontSize = 9.sp,
                        modifier = Modifier.clickable { descending = !descending }.padding(8.dp))
                }
            }
        }
        if (filtered.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState("Library is empty", "Try another filter or add a game.", "Add game", add)
            }
        } else {
            items(filtered, key = { it.pkg }) { app ->
                Column(Modifier.clickable { details(app) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.22f).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Raised, Panel))).padding(13.dp),
                        contentAlignment = Alignment.Center) {
                        AppIcon(app, Modifier.fillMaxHeight())
                        if (app.pkg in store.favorites) Text("★", color = Accent, fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.TopEnd))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(app.name, color = Foreground, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (app.isGame) "GAME" else "APPLICATION", color = Muted, fontSize = 8.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable private fun SearchPage(apps: List<AppItem>, store: LauncherStore, compact: Boolean,
    play: (AppItem) -> Unit, details: (AppItem) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = apps.filter { it.name.contains(query, true) || it.pkg.contains(query, true) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (compact) 112.dp else 154.dp),
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(start = if (compact) 18.dp else 30.dp, end = if (compact) 18.dp else 30.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp)) {
                Text("DISCOVER YOUR LIBRARY", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                Text("Search", color = Foreground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp),
                    singleLine = true, placeholder = { Text("Search games and apps…", color = Muted) },
                    shape = RoundedCornerShape(14.dp))
                Text("${results.size} RESULTS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        if (results.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState("No results", "Try another title or package name.")
            }
        } else {
            items(results, key = { it.pkg }) { app ->
                Column(Modifier.clickable { details(app) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.22f).clip(RoundedCornerShape(14.dp))
                        .background(Raised).padding(12.dp), contentAlignment = Alignment.Center) {
                        AppIcon(app, Modifier.fillMaxHeight())
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(app.name, color = Foreground, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable private fun SettingCard(title: String, description: String, glyph: String, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Panel).clickable(onClick = click).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Raised), contentAlignment = Alignment.Center) {
            Text(glyph, color = Accent, fontSize = 19.sp)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Muted, fontSize = 10.sp)
        }
        Text("›", color = Muted, fontSize = 22.sp)
    }
}

@Composable private fun SettingsPage(add: () -> Unit, reset: () -> Unit, gamepad: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        Eyebrow("PREFERENCES")
        Heading("Pengaturan", "Sesuaikan pengalaman launcher.")
        SettingCard("Tambah aplikasi", "Pilih aplikasi yang ingin ditampilkan.", "+", add)
        Spacer(Modifier.height(10.dp))
        SettingCard("Pulihkan daftar", "Reset daftar manual; favorit dan riwayat tetap aman.", "↺", reset)
        Spacer(Modifier.height(10.dp))
        SettingCard("Gamepad tester", "Periksa koneksi dan tombol controller.", "⌘", gamepad)
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp))
            .background(Brush.linearGradient(listOf(Raised, Panel))).padding(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("GAME LAUNCHER 2.0", color = Foreground, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                Text("Built for your library. Designed for play.", color = Muted, fontSize = 10.sp)
                Text("CHARCOAL EDITION  •  ANDROID", color = Accent, fontSize = 8.sp, letterSpacing = 1.3.sp)
            }
        }
    }
}

@Composable private fun DetailPage(app: AppItem, favorite: Boolean, compact: Boolean,
    back: () -> Unit, play: () -> Unit, toggle: () -> Unit, remove: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = if (compact) 20.dp else 34.dp, vertical = 18.dp)) {
        Text("←  KEMBALI KE KOLEKSI", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp, modifier = Modifier.clickable(onClick = back).padding(vertical = 9.dp))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF293C32), Raised))).padding(vertical = 24.dp),
            contentAlignment = Alignment.Center) {
            AppIcon(app, Modifier.size(if (compact) 142.dp else 174.dp))
        }
        Spacer(Modifier.height(22.dp))
        Eyebrow(if (app.isGame) "ANDROID GAME" else "ANDROID APP")
        Text(app.name, color = Foreground, fontSize = 27.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PlayButton { play() }
            Text(if (favorite) "★  FAVORIT" else "☆  FAVORIT", color = if (favorite) Accent else Foreground,
                fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(13.dp))
                    .background(Raised).clickable(onClick = toggle).padding(horizontal = 15.dp, vertical = 13.dp))
        }
        Spacer(Modifier.height(27.dp))
        Text("INFORMASI APLIKASI", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        Text(app.pkg, color = Foreground, fontSize = 11.sp)
        Spacer(Modifier.height(26.dp))
        Text("Hapus dari launcher", color = Danger, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = remove).padding(vertical = 8.dp))
        Text("Aplikasi tetap terpasang di perangkat. Hanya dihapus dari daftar GameLauncher.", color = Muted, fontSize = 10.sp)
    }
}

@Composable private fun AddAppsDialog(apps: List<AppItem>, add: (AppItem) -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text("Tambah aplikasi") }, text = {
        if (apps.isEmpty()) Text("Tidak ada aplikasi lain untuk ditambahkan.")
        else Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            apps.forEach { a -> Row(Modifier.fillMaxWidth().clickable { add(a) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Image(a.icon, a.name, Modifier.size(40.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(12.dp))
                Text(a.name, color = Foreground, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("Selesai") } })
}

@Composable private fun LauncherRoot(allApps: List<AppItem>) {
    val context = LocalContext.current
    val store = remember { LauncherStore(context) }
    var pageName by rememberSaveable { mutableStateOf(Page.HOME.name) }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<AppItem?>(null) }
    var showGamepadTest by remember { mutableStateOf(false) }
    var lastGamepadInput by remember { mutableStateOf("Belum ada input") }
    var gamepadConnected by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { rootFocus.requestFocus() }
    val page = Page.values().firstOrNull { it.name == pageName } ?: Page.HOME
    val visible = allApps.filter { (it.isGame || it.pkg in store.added) && it.pkg !in store.removed }
    val selected = allApps.firstOrNull { it.pkg == selectedPackage }
    val launch: (AppItem) -> Unit = { a ->
        context.packageManager.getLaunchIntentForPackage(a.pkg)?.let { store.played(a.pkg); context.startActivity(it) }
    }
    val details: (AppItem) -> Unit = { selectedPackage = it.pkg }

    gamepadConnected = remember {
        InputDevice.getDeviceIds().any { id -> InputDevice.getDevice(id)?.let { device ->
            device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK)
        } == true }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Background)
        .focusRequester(rootFocus).onPreviewKeyEvent { event ->
            val code = event.key
            fun key(code: Int) = Key(code.toLong())
            lastGamepadInput = when (code) {
                key(AndroidKeyEvent.KEYCODE_DPAD_UP) -> "D-pad ↑"
                key(AndroidKeyEvent.KEYCODE_DPAD_DOWN) -> "D-pad ↓"
                key(AndroidKeyEvent.KEYCODE_DPAD_LEFT) -> "D-pad ←"
                key(AndroidKeyEvent.KEYCODE_DPAD_RIGHT) -> "D-pad →"
                key(AndroidKeyEvent.KEYCODE_BUTTON_A), key(AndroidKeyEvent.KEYCODE_ENTER) -> "A / Confirm"
                key(AndroidKeyEvent.KEYCODE_BUTTON_B), key(AndroidKeyEvent.KEYCODE_BACK), key(AndroidKeyEvent.KEYCODE_ESCAPE) -> "B / Back"
                key(AndroidKeyEvent.KEYCODE_BUTTON_X) -> "X"
                key(AndroidKeyEvent.KEYCODE_BUTTON_Y) -> "Y"
                key(AndroidKeyEvent.KEYCODE_BUTTON_L1) -> "L1"
                key(AndroidKeyEvent.KEYCODE_BUTTON_R1) -> "R1"
                key(AndroidKeyEvent.KEYCODE_BUTTON_START) -> "Start"
                else -> "Input"
            }
            when (code) {
                key(AndroidKeyEvent.KEYCODE_BUTTON_B), key(AndroidKeyEvent.KEYCODE_BACK), key(AndroidKeyEvent.KEYCODE_ESCAPE) -> {
                    if (selectedPackage != null) selectedPackage = null
                    else if (page != Page.HOME) pageName = Page.HOME.name
                    else false
                    true
                }
                key(AndroidKeyEvent.KEYCODE_BUTTON_L1) -> { pageName = Page.values()[((page.ordinal - 1 + Page.values().size) % Page.values().size)].name; true }
                key(AndroidKeyEvent.KEYCODE_BUTTON_R1) -> { pageName = Page.values()[(page.ordinal + 1) % Page.values().size].name; true }
                key(AndroidKeyEvent.KEYCODE_BUTTON_A), key(AndroidKeyEvent.KEYCODE_ENTER), key(AndroidKeyEvent.KEYCODE_BUTTON_X) -> {
                    if (selectedPackage != null) selected?.let { launch(it) }
                    else if (page == Page.HOME) visible.firstOrNull()?.let { launch(it) }
                    true
                }
                else -> false
            }
        }.focusable()) {
        val landscape = maxWidth > maxHeight && maxWidth >= 600.dp
        val compact = !landscape && maxWidth < 600.dp
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(landscape, page) { pageName = it.name }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (selected != null) DetailPage(selected, selected.pkg in store.favorites, compact,
                    { selectedPackage = null }, { launch(selected) }, { store.favorite(selected.pkg) }, { removeTarget = selected })
                else when (page) {
                    Page.HOME -> HomePage(visible, store, compact, launch, details) { pageName = Page.LIBRARY.name }
                    Page.LIBRARY -> LibraryPage(visible, store, compact, launch, details) { showAdd = true }
                    Page.SEARCH -> SearchPage(visible, store, compact, launch, details)
                    Page.SETTINGS -> SettingsPage({ showAdd = true }, { showReset = true }) { showGamepadTest = true }
                }
            }
            if (!landscape) BottomBar(page) { pageName = it.name }
        }
    }
    if (showGamepadTest) AlertDialog(onDismissRequest = { showGamepadTest = false },
        title = { Text("Gamepad tester") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (gamepadConnected) "Controller terdeteksi" else "Controller belum terdeteksi",
                color = if (gamepadConnected) Accent else Muted, fontWeight = FontWeight.Bold)
            Text("Tekan tombol pada gamepad untuk melihat input di bawah.", color = Muted, fontSize = 11.sp)
            Text(lastGamepadInput, color = Foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("D-pad: navigasi • A: pilih • B: kembali • L1/R1: pindah tab",
                color = Muted, fontSize = 10.sp)
        } }, confirmButton = { TextButton(onClick = { showGamepadTest = false }) { Text("Selesai") } })
    if (showAdd) AddAppsDialog(allApps.filter { a -> visible.none { it.pkg == a.pkg } },
        { store.add(it.pkg) }, { showAdd = false })
    if (showReset) AlertDialog(onDismissRequest = { showReset = false },
        title = { Text("Pulihkan daftar otomatis?") },
        text = { Text("Daftar tambah dan hapus manual akan direset. Favorit dan riwayat tetap disimpan.") },
        confirmButton = { TextButton(onClick = { store.reset(); showReset = false }) { Text("Pulihkan") } },
        dismissButton = { TextButton(onClick = { showReset = false }) { Text("Batal") } })
    removeTarget?.let { target -> AlertDialog(onDismissRequest = { removeTarget = null },
        title = { Text("Hapus dari GameLauncher?") },
        text = { Text("${target.name} akan dihapus dari daftar launcher. Aplikasi dan datanya tetap ada di perangkat.") },
        confirmButton = { TextButton(onClick = {
            store.remove(target.pkg); removeTarget = null; selectedPackage = null
        }) { Text("Hapus dari daftar", color = Danger) } },
        dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Batal") } })
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Background, surface = Panel,
                primary = Accent, onBackground = Foreground, onSurface = Foreground)) {
                val context = LocalContext.current
                val apps by produceState<List<AppItem>?>(null) {
                    value = withContext(Dispatchers.Default) { loadAllApps(context) }
                }
                Surface(Modifier.fillMaxSize(), color = Background) {
                    if (apps == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Brand() }
                    else LauncherRoot(apps!!)
                }
            }
        }
    }
}
