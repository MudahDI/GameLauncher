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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

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
                it.loadIcon(pm).toBitmap(256, 256).asImageBitmap()
            )
        }
        .sortedBy { it.name.lowercase() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCarousel(games: List<GameApp>) {
    if (games.isEmpty()) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { games.size }
    val current = games[pagerState.currentPage]

    fun launchGame(g: GameApp) {
        ctx.packageManager.getLaunchIntentForPackage(g.pkg)
            ?.let { ctx.startActivity(it) }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0F1A))) {
        Image(
            bitmap = current.icon,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(60.dp).alpha(0.4f)
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "GAME LIBRARY  ·  ${pagerState.currentPage + 1}/${games.size}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 24.dp)
            )
            Spacer(Modifier.weight(1f))
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 90.dp),
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
                        .clickable {
                            if (page == pagerState.currentPage) launchGame(games[page])
                            else scope.launch { pagerState.animateScrollToPage(page) }
                        }
                )
            }
            Spacer(Modifier.height(28.dp))
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
            Button(onClick = { launchGame(current) }) {
                Text("MAIN", fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val games = remember { loadGames(this) }
                GameCarousel(games)
            }
        }
    }
}
