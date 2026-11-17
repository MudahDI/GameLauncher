package com.contoh.gamelauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- Root ----------

@Composable
fun LauncherApp(allApps: List<AppItem>) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    // 0 = Beranda, 1 = Koleksi, 2 = Cari, 3 = Atur
    var tab by rememberSaveable { mutableStateOf(0) }
    // Simpan package name (bukan AppItem) supaya state bisa bertahan saat proses dibunuh sistem.
    var focusedPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    // Dari tab mana pun, Back kembali ke Beranda dulu.
    BackHandler(enabled = tab != 0) { tab = 0 }

    val visible = allApps.filter { (it.isGame || it.pkg in store.added) && it.pkg !in store.removed }
    val visiblePkgs = visible.map { it.pkg }.toSet()
    val candidates = allApps.filter { it.pkg !in visiblePkgs }
    // Game yang sedang dipilih; kalau belum ada, pakai yang terakhir dimainkan lalu game pertama.
    val focused = visible.find { it.pkg == focusedPkg }
        ?: store.recent.firstNotNullOfOrNull { p -> visible.find { it.pkg == p } }
        ?: visible.firstOrNull()
    val selected = selectedPkg?.let { p -> allApps.find { it.pkg == p } }

    val play: (AppItem) -> Unit = { a ->
        ctx.packageManager.getLaunchIntentForPackage(a.pkg)?.let { launch ->
            // Catat "terakhir dimainkan" hanya kalau app benar-benar berhasil dibuka.
            runCatching { ctx.startActivity(launch) }
                .onSuccess { store.markPlayed(a.pkg) }
        }
    }
    val options: (AppItem) -> Unit = { a -> selectedPkg = a.pkg }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Deck.Bg, Deck.Panel)))
    ) {
        if (focused != null) {
            Image(
                bitmap = focused.icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(70.dp).alpha(0.35f)
            )
        }
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(tab) { tab = it }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> HomeScreen(
                        visible, store, focused,
                        { focusedPkg = it.pkg }, play, options
                    )
                    1 -> LibraryScreen(visible, store, play, options) { showPicker = true }
                    2 -> SearchScreen(visible, store, play, options)
                    else -> SettingsScreen(
                        onAdd = { showPicker = true },
                        onReset = { confirmReset = true }
                    )
                }
            }
        }
    }

    if (showPicker) {
        AddAppDialog(candidates, { store.addApp(it.pkg) }, { showPicker = false })
    }

    selected?.let { a ->
        val fav = a.pkg in store.favorites
        AlertDialog(
            onDismissRequest = { selectedPkg = null },
            title = { Text(a.name) },
            text = {
                Column {
                    TextButton(onClick = { store.toggleFav(a.pkg); selectedPkg = null }) {
                        Text(if (fav) "★ Hapus dari favorit" else "☆ Tambah ke favorit")
                    }
                    TextButton(onClick = { store.removeApp(a.pkg); selectedPkg = null }) {
                        Text("Hapus dari launcher")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedPkg = null }) { Text("Tutup") } }
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
    /** Naik setiap kali launcher kembali tampil, memicu pemuatan ulang daftar app. */
    private var refreshTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Deck.Accent,
                    background = Deck.Bg,
                    surface = Deck.Panel,
                    onSurface = Deck.Text
                )
            ) {
                val ctx = LocalContext.current
                val tick = refreshTick
                val apps by produceState<List<AppItem>?>(initialValue = null, tick) {
                    value = withContext(Dispatchers.Default) { loadAllApps(ctx) }
                }
                val list = apps
                if (list == null) {
                    Box(Modifier.fillMaxSize().background(Deck.Bg))
                } else {
                    LauncherApp(list)
                }
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        // Setelah bermain atau memasang game baru, muat ulang daftar.
        refreshTick++
    }
}
