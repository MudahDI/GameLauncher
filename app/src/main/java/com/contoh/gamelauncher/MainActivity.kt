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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

private fun prefs(ctx: Context) =
    ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

fun loadSet(ctx: Context, key: String): Set<String> =
    prefs(ctx).getStringSet(key, emptySet())?.toSet() ?: emptySet()

fun saveSet(ctx: Context, key: String, set: Set<String>) {
    prefs(ctx).edit().putStringSet(key, HashSet(set)).apply()
}

@Composable
fun Launcher(allApps: List<AppItem>) {
    val ctx = LocalContext.current
    var added by remember { mutableStateOf(loadSet(ctx, "added")) }
    var removed by remember { mutableStateOf(loadSet(ctx, "removed")) }
    var showPicker by remember { mutableStateOf(false) }

    val visible = allApps.filter { (it.isGame || it.pkg in added) && it.pkg !in removed }
    val visiblePkgs = visible.map { it.pkg }.toSet()
    val candidates = allApps.filter { it.pkg !in visiblePkgs }

    val onAdd: (AppItem) -> Unit = { a ->
        added = added + a.pkg
        removed = removed - a.pkg
        saveSet(ctx, "added", added)
        saveSet(ctx, "removed", removed)
    }
    val onRemove: (AppItem) -> Unit = { a ->
        removed = removed + a.pkg
        added = added - a.pkg
        saveSet(ctx, "added", added)
        saveSet(ctx, "removed", removed)
    }

    if (visible.isEmpty()) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF0B0F1A)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Belum ada app di launcher", color = Color.White)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showPicker = true }) { Text("+ Tambah app") }
            }
        }
    } else {
        GameCarousel(visible, onRemove) { showPicker = true }
    }

    if (showPicker) {
        val listMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
        AlertDialog(
            onDismissRequest = { showPicker = false },
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
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("Selesai") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardPager(
    games: List<AppItem>,
    pagerState: PagerState,
    cardSize: Dp,
    paneWidth: Dp,
    onCardClick: (Int) -> Unit,
    onCardLongClick: (Int) -> Unit
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = (paneWidth - cardSize) / 2),
        pageSize = PageSize.Fixed(cardSize),
        pageSpacing = 16.dp,
        key = { games[it].pkg }
    ) { page ->
        val offset = ((pagerState.currentPage - page) +
                pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
        val s = lerp(0.8f, 1f, 1f - offset)
        Image(
            bitmap = games[page].icon,
            contentDescription = games[page].name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer { scaleX = s; scaleY = s; alpha = lerp(0.5f, 1f, 1f - offset) }
                .clip(RoundedCornerShape(32.dp))
                .combinedClickable(
                    onClick = { onCardClick(page) },
                    onLongClick = { onCardLongClick(page) }
                )
        )
    }
}

@Composable
private fun InfoBlock(current: AppItem, onPlay: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            current.name,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onPlay) {
            Text("MAIN", fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Tahan lama ikon untuk menghapus dari launcher",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCarousel(
    games: List<AppItem>,
    onRemove: (AppItem) -> Unit,
    onAddClick: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { games.size }
    val current = games[pagerState.currentPage.coerceIn(0, games.lastIndex)]
    var pendingRemove by remember { mutableStateOf<AppItem?>(null) }

    fun launchApp(g: AppItem) {
        ctx.packageManager.getLaunchIntentForPackage(g.pkg)
            ?.let { ctx.startActivity(it) }
    }

    val onCardClick: (Int) -> Unit = { page ->
        if (page == pagerState.currentPage) launchApp(games[page])
        else scope.launch { pagerState.animateScrollToPage(page) }
    }
    val onCardLongClick: (Int) -> Unit = { page -> pendingRemove = games[page] }
    val counter = "GAME LIBRARY  ·  ${pagerState.currentPage + 1}/${games.size}"

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B0F1A))) {
        val screenW = maxWidth
        val screenH = maxHeight
        val landscape = screenW > screenH

        Image(
            bitmap = current.icon,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(60.dp).alpha(0.4f)
        )

        if (landscape) {
            val paneWidth = screenW * 0.6f
            val cardSize = minOf(320.dp, maxOf(120.dp, screenH - 110.dp))
            Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(
                    Modifier.width(paneWidth).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        counter,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    CardPager(games, pagerState, cardSize, paneWidth, onCardClick, onCardLongClick)
                    Spacer(Modifier.weight(1f))
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(end = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    InfoBlock(current) { launchApp(current) }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onAddClick) { Text("+ Tambah app") }
                }
            }
        } else {
            val cardSize = minOf(screenW - 180.dp, screenH * 0.45f)
            Column(
                Modifier.fillMaxSize().safeDrawingPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    counter,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 24.dp)
                )
                Spacer(Modifier.weight(1f))
                CardPager(games, pagerState, cardSize, screenW, onCardClick, onCardLongClick)
                Spacer(Modifier.height(28.dp))
                InfoBlock(current) { launchApp(current) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onAddClick) { Text("+ Tambah app") }
            }
        }
    }

    pendingRemove?.let { g ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Hapus ${g.name} dari launcher?") },
            text = { Text("Aplikasi tidak di-uninstall. Kamu bisa menambahkannya lagi lewat tombol + Tambah app.") },
            confirmButton = {
                TextButton(onClick = { onRemove(g); pendingRemove = null }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Batal") }
            }
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
                    Launcher(list)
                }
            }
        }
    }
}
