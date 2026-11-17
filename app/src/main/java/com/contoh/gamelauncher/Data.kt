package com.contoh.gamelauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

// ---------- Data ----------

data class AppItem(
    val name: String,
    val pkg: String,
    val icon: ImageBitmap,
    val isGame: Boolean
)

/** Game dikenali dari kategori resmi ATAU flag lama (banyak game lawas hanya memakai flag). */
@Suppress("DEPRECATION")
private fun ApplicationInfo.looksLikeGame(): Boolean =
    category == ApplicationInfo.CATEGORY_GAME ||
        (flags and ApplicationInfo.FLAG_IS_GAME) != 0

fun loadAllApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.applicationInfo }
        .filter { it.packageName != ctx.packageName }
        .distinctBy { it.packageName }
        .mapNotNull { info ->
            // Satu app bermasalah (ikon rusak, dsb.) tidak boleh menggagalkan seluruh daftar.
            runCatching {
                AppItem(
                    info.loadLabel(pm).toString(),
                    info.packageName,
                    info.loadIcon(pm).toBitmap(192, 192).asImageBitmap(),
                    info.looksLikeGame()
                )
            }.getOrNull()
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
    // Override kategori manual; package yang tidak ada di kedua set mengikuti deteksi Android.
    var forcedGames by mutableStateOf(readSet("forced_games"))
    var forcedApps by mutableStateOf(readSet("forced_apps"))
    var recent by mutableStateOf(readList("recent"))

    fun isGame(app: AppItem): Boolean = when {
        app.pkg in forcedGames -> true
        app.pkg in forcedApps -> false
        else -> app.isGame
    }

    fun moveToGame(pkg: String) {
        forcedGames = forcedGames + pkg
        forcedApps = forcedApps - pkg
        persist()
    }

    fun moveToApps(pkg: String) {
        forcedApps = forcedApps + pkg
        forcedGames = forcedGames - pkg
        persist()
    }

    private fun persist() {
        sp.edit()
            .putStringSet("added", HashSet(added))
            .putStringSet("removed", HashSet(removed))
            .putStringSet("favorites", HashSet(favorites))
            .putStringSet("forced_games", HashSet(forcedGames))
            .putStringSet("forced_apps", HashSet(forcedApps))
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
        forcedGames = emptySet()
        forcedApps = emptySet()
        persist()
    }
}
