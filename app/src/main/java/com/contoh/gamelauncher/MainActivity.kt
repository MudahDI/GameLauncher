package com.contoh.gamelauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

data class GameApp(val name: String, val pkg: String, val icon: ImageBitmap)

fun loadGames(ctx: Context): List<GameApp> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.applicationInfo }
        .filter { it.category == ApplicationInfo.CATEGORY_GAME }
        .distinctBy { it.packageName }
        .map {
            GameApp(
                it.loadLabel(pm).toString(),
                it.packageName,
                it.loadIcon(pm).toBitmap().asImageBitmap()
            )
        }
        .sortedBy { it.name.lowercase() }
}

@Composable
fun GameGrid(games: List<GameApp>) {
    val ctx = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.statusBarsPadding()
    ) {
        items(games, key = { it.pkg }) { g ->
            Column(
                modifier = Modifier
                    .clickable {
                        ctx.packageManager.getLaunchIntentForPackage(g.pkg)
                            ?.let { ctx.startActivity(it) }
                    }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(g.icon, g.name, Modifier.size(72.dp))
                Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    val games = remember { loadGames(this) }
                    GameGrid(games)
                }
            }
        }
    }
}
