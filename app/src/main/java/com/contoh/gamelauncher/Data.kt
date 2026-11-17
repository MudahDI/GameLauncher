package com.mdh.gamelauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
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
                val drawable = pm.getApplicationIcon(info.packageName)
                AppItem(
                    info.loadLabel(pm).toString(),
                    info.packageName,
                    drawable.toBitmap(192, 192).asImageBitmap(),
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
    var lastPlayedAt by mutableStateOf(readLongMap("last_played:"))
    var playtimeMs by mutableStateOf(readLongMap("playtime:"))
    private fun readLongMap(prefix: String): Map<String, Long> = sp.all.entries.mapNotNull { (key, value) ->
        if (key.startsWith(prefix) && value is Long) key.removePrefix(prefix) to value else null
    }.toMap()
    fun reloadPlayStats() {
        lastPlayedAt = readLongMap("last_played:")
        playtimeMs = readLongMap("playtime:")
    }
    var artworks by mutableStateOf(readArtworkMap())
    var descriptions by mutableStateOf(readDescriptionMap())
    var metadataTitles by mutableStateOf(readMetadataTitleMap())
    var wallpaperUri by mutableStateOf(sp.getString("wallpaper_uri", null)?.let { Uri.parse(it) })
    init {
        // Migrasi sekali: gunakan identitas biru MDI sebagai aksen awal versi ini.
        if (!sp.getBoolean("blue_branding_migrated", false)) {
            sp.edit().putString("accent_hex", "#1A9FFF")
                .putBoolean("blue_branding_migrated", true).apply()
        }
    }
    var accentHex by mutableStateOf(sp.getString("accent_hex", "#1A9FFF") ?: "#1A9FFF")
    var cardSizeDp by mutableStateOf(sp.getInt("card_size_dp", 112).coerceIn(88, 148))
    var gridSpacingDp by mutableStateOf(sp.getInt("grid_spacing_dp", 14).coerceIn(6, 24))
    var cardCornerDp by mutableStateOf(sp.getInt("card_corner_dp", 10).coerceIn(4, 20))
    var wallpaperOpacity by mutableStateOf(sp.getFloat("wallpaper_opacity", 0.65f).coerceIn(0.05f, 1.00f))
    var wallpaperOverlay by mutableStateOf(sp.getFloat("wallpaper_overlay", 0.62f).coerceIn(0.15f, 0.95f))

    fun setCardSize(value: Int) {
        cardSizeDp = value.coerceIn(88, 148)
        sp.edit().putInt("card_size_dp", cardSizeDp).apply()
    }

    fun setGridSpacing(value: Int) {
        gridSpacingDp = value.coerceIn(6, 24)
        sp.edit().putInt("grid_spacing_dp", gridSpacingDp).apply()
    }

    fun setCardCorner(value: Int) {
        cardCornerDp = value.coerceIn(4, 20)
        sp.edit().putInt("card_corner_dp", cardCornerDp).apply()
    }

    fun updateWallpaperOpacity(value: Float) {
        wallpaperOpacity = value.coerceIn(0.05f, 1.00f)
        sp.edit().putFloat("wallpaper_opacity", wallpaperOpacity).apply()
    }

    fun updateWallpaperOverlay(value: Float) {
        wallpaperOverlay = value.coerceIn(0.15f, 0.95f)
        sp.edit().putFloat("wallpaper_overlay", wallpaperOverlay).apply()
    }

    fun resetAppearance() {
        setAccent("#1A9FFF")
        setCardSize(112)
        setGridSpacing(14)
        setCardCorner(10)
        updateWallpaperOpacity(0.65f)
        updateWallpaperOverlay(0.62f)
    }

    fun setWallpaper(uri: Uri?) {
        wallpaperUri = uri
        sp.edit().putString("wallpaper_uri", uri?.toString()).apply()
    }

    fun setAccent(hex: String) {
        accentHex = hex
        sp.edit().putString("accent_hex", hex).apply()
        runCatching { Deck.Accent = Color(android.graphics.Color.parseColor(hex)) }
    }

    private fun readMetadataTitleMap(): Map<String, String> =
        sp.all.entries.mapNotNull { (key, value) ->
            if (key.startsWith("metadata_title:") && value is String) key.removePrefix("metadata_title:") to value else null
        }.toMap()

    fun displayName(pkg: String, fallback: String): String = metadataTitles[pkg]?.takeIf { it.isNotBlank() } ?: fallback

    fun setMetadata(pkg: String, title: String, description: String) {
        val cleanTitle = title.trim()
        val cleanDescription = description.trim()
        metadataTitles = if (cleanTitle.isEmpty()) metadataTitles - pkg else metadataTitles + (pkg to cleanTitle)
        descriptions = if (cleanDescription.isEmpty()) descriptions - pkg else descriptions + (pkg to cleanDescription)
        sp.edit().apply {
            if (cleanTitle.isEmpty()) remove("metadata_title:$pkg") else putString("metadata_title:$pkg", cleanTitle)
            if (cleanDescription.isEmpty()) remove("description:$pkg") else putString("description:$pkg", cleanDescription)
        }.apply()
    }

    private fun readDescriptionMap(): Map<String, String> =
        sp.all.entries.mapNotNull { (key, value) ->
            if (key.startsWith("description:") && value is String) key.removePrefix("description:") to value else null
        }.toMap()

    fun description(pkg: String): String = descriptions[pkg].orEmpty()

    fun setDescription(pkg: String, value: String) {
        val key = "description:$pkg"
        val clean = value.trim()
        descriptions = if (clean.isEmpty()) descriptions - pkg else descriptions + (pkg to clean)
        sp.edit().apply {
            if (clean.isEmpty()) remove(key) else putString(key, clean)
        }.apply()
    }

    private fun readArtworkMap(): Map<String, String> =
        sp.all.entries.mapNotNull { (key, value) ->
            if (key.startsWith("artwork:") && value is String) key.removePrefix("artwork:") to value else null
        }.toMap()

    fun artworkUri(pkg: String, kind: String): Uri? =
        artworks["$kind:$pkg"]?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun setArtwork(pkg: String, kind: String, uri: Uri) {
        val key = "$kind:$pkg"
        artworks = artworks + (key to uri.toString())
        sp.edit().putString("artwork:$key", uri.toString()).apply()
    }

    fun clearArtwork(pkg: String, kind: String) {
        val key = "$kind:$pkg"
        artworks = artworks - key
        sp.edit().remove("artwork:$key").apply()
    }

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
        val now = System.currentTimeMillis()
        recent = (listOf(pkg) + recent.filter { it != pkg }).take(50)
        lastPlayedAt = lastPlayedAt + (pkg to now)
        sp.edit().putString("recent", recent.joinToString(",")).putLong("last_played:$pkg", now)
            .putString("pending_play_pkg", pkg).putLong("pending_play_start", now).apply()
    }

    companion object {
        fun finishPendingSession(ctx: Context) {
            val prefs = ctx.applicationContext.getSharedPreferences("launcher", Context.MODE_PRIVATE)
            val pkg = prefs.getString("pending_play_pkg", null) ?: return
            val started = prefs.getLong("pending_play_start", 0L)
            val elapsed = (System.currentTimeMillis() - started).coerceIn(0L, 43_200_000L)
            if (started > 0L && elapsed >= 5_000L) {
                val key = "playtime:$pkg"
                prefs.edit().putLong(key, prefs.getLong(key, 0L) + elapsed).apply()
            }
            prefs.edit().remove("pending_play_pkg").remove("pending_play_start").apply()
        }
    }

    fun clearHistory() {
        recent = emptyList()
        val edit = sp.edit().remove("recent")
        lastPlayedAt.keys.forEach { edit.remove("last_played:$it") }
        edit.apply()
        lastPlayedAt = emptyMap()
    }

    fun resetList() {
        added = emptySet()
        removed = emptySet()
        forcedGames = emptySet()
        forcedApps = emptySet()
        persist()
    }
}
