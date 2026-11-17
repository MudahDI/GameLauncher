package com.mdh.gamelauncher

import android.content.Context
import android.graphics.BitmapFactory
import android.text.Html
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GameMetadataResult(
    val title: String,
    val extract: String,
    val thumbnailUrl: String?,
    val pageUrl: String,
    val sourceName: String = "",
    val coverUrl: String? = null,
    val bannerUrl: String? = null
)

data class MetadataSourceConfig(
    val id: String,
    val name: String,
    val endpoint: String = "",
    val apiKey: String = "",
    val enabled: Boolean = false
)

private inline fun <T> runCatchingPreservingCancellation(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

private val defaultMetadataSources = listOf(
    MetadataSourceConfig("wikipedia", "Wikipedia", enabled = true),
    MetadataSourceConfig("google_play", "Google Play", enabled = true),
    MetadataSourceConfig("rawg", "RAWG"),
    MetadataSourceConfig("igdb", "IGDB"),
    MetadataSourceConfig("steamgriddb", "SteamGridDB")
)

private fun loadMetadataSources(context: Context): List<MetadataSourceConfig> {
    val raw = context.getSharedPreferences("metadata_sources", Context.MODE_PRIVATE)
        .getString("sources", null) ?: return defaultMetadataSources
    return runCatching {
        val array = JSONArray(raw)
        val loaded = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            MetadataSourceConfig(
                item.optString("id"), item.optString("name"),
                item.optString("endpoint"), item.optString("apiKey"),
                item.optBoolean("enabled")
            )
        }
        // Keep newly-added built-in providers visible when upgrading an older config.
        loaded + defaultMetadataSources.filter { default -> loaded.none { it.id == default.id } }
    }.getOrDefault(defaultMetadataSources)
}

private fun persistMetadataSources(context: Context, sources: List<MetadataSourceConfig>) {
    val array = JSONArray()
    sources.forEach { source ->
        array.put(JSONObject().apply {
            put("id", source.id)
            put("name", source.name)
            put("endpoint", source.endpoint)
            put("apiKey", source.apiKey)
            put("enabled", source.enabled)
        })
    }
    context.getSharedPreferences("metadata_sources", Context.MODE_PRIVATE)
        .edit().putString("sources", array.toString()).apply()
}

object MetadataRepository {
    fun sources(context: Context): List<MetadataSourceConfig> = loadMetadataSources(context)

    suspend fun search(context: Context, query: String, packageName: String? = null): List<GameMetadataResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        require(cleanQuery.isNotBlank()) { "Masukkan judul game terlebih dahulu." }
        val enabled = loadMetadataSources(context).filter { it.enabled }
        require(enabled.isNotEmpty()) { "Aktifkan setidaknya satu sumber database game." }

        // Jalankan provider aktif bersamaan agar pencarian gabungan tidak menunggu satu per satu.
        val outcomes = coroutineScope {
            enabled.map { source ->
                async(Dispatchers.IO) {
                    val outcome = try {
                        Result.success(when (source.id) {
                            "wikipedia" -> searchWikipedia(cleanQuery)
                            "google_play" -> searchGooglePlay(cleanQuery, packageName)
                            "rawg" -> searchRawg(cleanQuery, source.apiKey)
                            "igdb" -> searchIgdb(cleanQuery, source.apiKey)
                            "steamgriddb" -> searchSteamGridDb(cleanQuery, source.apiKey)
                            else -> if (source.id.startsWith("custom_")) searchCustom(cleanQuery, source) else emptyList()
                        })
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        Result.failure(error)
                    }
                    source to outcome
                }
            }.awaitAll()
        }

        val results = mutableListOf<GameMetadataResult>()
        val errors = mutableListOf<String>()
        outcomes.forEach { (source, outcome) ->
            outcome.onSuccess { results += it }
                .onFailure { errors += "${source.name}: ${it.message ?: "gagal"}" }
        }
        if (results.isEmpty() && errors.isNotEmpty()) {
            throw IllegalStateException(errors.joinToString("\n"))
        }
        results.distinctBy { it.title.lowercase() to it.sourceName }.take(30)
    }

    private fun connection(url: String, method: String = "GET", headers: Map<String, String> = emptyMap()): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            requestMethod = method
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            setRequestProperty("User-Agent", "MDILauncher/2.1 (Android game metadata)")
        }

    private fun readBody(conn: HttpURLConnection): String {
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            val detail = runCatching {
                val json = JSONObject(body)
                json.optString("message").ifBlank { json.optString("error") }
            }.getOrDefault("")
            throw IllegalStateException(detail.takeIf { it.isNotBlank() }?.take(220) ?: "HTTP $status")
        }
        return body
    }

    private fun searchWikipedia(query: String): List<GameMetadataResult> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded" +
            "&gsrnamespace=0&gsrlimit=8&prop=pageimages%7Cextracts&piprop=thumbnail&pithumbsize=900" +
            "&exintro=1&explaintext=1&format=json&utf8=1"
        val conn = connection(url)
        try {
            val body = readBody(conn)
            val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
            val results = mutableListOf<Pair<Int, GameMetadataResult>>()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.getJSONObject(keys.next())
                val title = page.optString("title").trim()
                if (title.isBlank()) continue
                val thumbnail = page.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.startsWith("https://") }
                val pageId = page.optLong("pageid")
                results += page.optInt("index", Int.MAX_VALUE) to GameMetadataResult(
                    title, page.optString("extract").trim(), thumbnail,
                    "https://en.wikipedia.org/?curid=$pageId", "Wikipedia"
                )
            }
            return results.sortedBy { it.first }.map { it.second }
        } finally { conn.disconnect() }
    }

    /**
     * Reads publicly visible Play Store listing metadata. This is intentionally a best-effort
     * HTML adapter (Google does not provide a public catalog-search API for third-party launchers).
     * If Google changes/blocks the page markup, other enabled providers remain available.
     */
    private fun searchGooglePlay(query: String, packageName: String?): List<GameMetadataResult> {
        val packages = linkedSetOf<String>()
        packageName?.trim()?.takeIf { it.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) }?.let { packages += it }

        if (packages.isEmpty() || query.isNotBlank()) {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val searchUrl = "https://play.google.com/store/search?q=$encoded&c=apps&hl=en&gl=US"
            val conn = connection(searchUrl, headers = mapOf("Accept-Language" to "en-US,en;q=0.9"))
            try {
                val html = readBody(conn)
                val pattern = Regex("""(?:/store/apps/details\?id=|https://play\.google\.com/store/apps/details\?id=)([A-Za-z0-9._]+)""")
                pattern.findAll(html).take(8).forEach { match ->
                    val id = match.groupValues[1].trimEnd('.', ',')
                    if (id.contains('.')) packages += id
                }
            } finally { conn.disconnect() }
        }

        return packages.take(8).mapNotNull { id ->
            runCatchingPreservingCancellation {
                val url = "https://play.google.com/store/apps/details?id=${URLEncoder.encode(id, "UTF-8")}&hl=en&gl=US"
                val conn = connection(url, headers = mapOf("Accept-Language" to "en-US,en;q=0.9"))
                try {
                    val html = readBody(conn)
                    fun meta(key: String): String? {
                        val tag = Regex("""<meta\b[^>]*(?:property|name)=["']${Regex.escape(key)}["'][^>]*>""", RegexOption.IGNORE_CASE)
                            .findAll(html).firstOrNull()?.value ?: return null
                        val raw = Regex("""content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
                            .find(tag)?.groupValues?.getOrNull(1) ?: return null
                        return Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().trim().takeIf { it.isNotBlank() }
                    }
                    val title = meta("og:title")?.substringBefore(" - Apps on Google Play")
                        ?: return@runCatchingPreservingCancellation null
                    val description = meta("og:description") ?: meta("description").orEmpty()
                    val image = meta("og:image")?.takeIf { it.startsWith("https://") }
                    GameMetadataResult(title, description, image, url, "Google Play", coverUrl = image)
                } finally { conn.disconnect() }
            }.getOrNull()
        }
    }

    private fun searchRawg(query: String, apiKey: String): List<GameMetadataResult> {
        require(apiKey.isNotBlank()) { "API key RAWG belum diisi." }
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val conn = connection("https://api.rawg.io/api/games?key=${URLEncoder.encode(apiKey, "UTF-8")}&search=$encoded&page_size=8")
        try {
            val body = JSONObject(readBody(conn))
            val array = body.optJSONArray("results") ?: JSONArray()
            return (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                val title = item.optString("name").trim()
                if (title.isBlank()) return@mapNotNull null
                val background = item.optString("background_image").takeIf { it.startsWith("https://") }
                GameMetadataResult(title, item.optString("description_raw"), background,
                    "https://rawg.io/games/${item.optString("slug")}", "RAWG", bannerUrl = background)
            }
        } finally { conn.disconnect() }
    }

    // IGDB credentials are entered as: Twitch Client ID:Access Token.
    private fun searchIgdb(query: String, credentials: String): List<GameMetadataResult> {
        val parts = credentials.split(":", limit = 2)
        require(parts.size == 2 && parts.all { it.isNotBlank() }) { "Isi Client ID:Access Token IGDB." }
        val conn = connection("https://api.igdb.com/v4/games", "POST", mapOf(
            "Client-ID" to parts[0].trim(),
            "Authorization" to "Bearer ${parts[1].trim()}",
            "Accept" to "application/json",
            "Content-Type" to "text/plain"
        ))
        try {
            conn.doOutput = true
            val term = query.replace("\\", "").replace("\"", "")
            conn.outputStream.bufferedWriter().use {
                it.write("search \"$term\"; fields name,summary,cover.url,url; limit 8;")
            }
            val array = JSONArray(readBody(conn))
            return (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                val title = item.optString("name").trim()
                if (title.isBlank()) return@mapNotNull null
                val cover = item.optJSONObject("cover")?.optString("url")?.let {
                    if (it.startsWith("//")) "https:$it" else it
                }?.replace("t_thumb", "t_cover_big")
                GameMetadataResult(title, item.optString("summary"), cover, item.optString("url"), "IGDB", coverUrl = cover)
            }
        } finally { conn.disconnect() }
    }

    private fun steamGridArtwork(apiKey: String, gameId: String, kind: String): String? {
        val conn = connection(
            "https://www.steamgriddb.com/api/v2/$kind/game/$gameId?limit=1",
            headers = mapOf("Authorization" to "Bearer $apiKey")
        )
        try {
            val root = JSONObject(readBody(conn))
            val item = root.optJSONArray("data")?.optJSONObject(0) ?: return null
            return item.optString("url").takeIf { it.startsWith("https://") }
        } finally { conn.disconnect() }
    }

    private fun searchSteamGridDb(query: String, apiKey: String): List<GameMetadataResult> {
        require(apiKey.isNotBlank()) { "API key SteamGridDB belum diisi." }
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val conn = connection("https://www.steamgriddb.com/api/v2/search/autocomplete/$encoded",
            headers = mapOf("Authorization" to "Bearer $apiKey"))
        try {
            val root = JSONObject(readBody(conn))
            val array = root.optJSONArray("data") ?: JSONArray()
            return (0 until minOf(array.length(), 5)).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                val title = item.optString("name").trim()
                val id = item.optString("id").takeIf { it.matches(Regex("[0-9]+")) }
                    ?: return@mapNotNull null
                if (title.isBlank()) return@mapNotNull null
                val cover = runCatchingPreservingCancellation { steamGridArtwork(apiKey, id, "grids") }.getOrNull()
                val banner = runCatchingPreservingCancellation { steamGridArtwork(apiKey, id, "heroes") }.getOrNull()
                GameMetadataResult(
                    title,
                    "Artwork SteamGridDB dengan rasio cover dan banner terpisah.",
                    cover ?: banner,
                    "https://www.steamgriddb.com/game/$id",
                    "SteamGridDB",
                    coverUrl = cover,
                    bannerUrl = banner
                )
            }
        } finally { conn.disconnect() }
    }

    private fun searchCustom(query: String, source: MetadataSourceConfig): List<GameMetadataResult> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val endpoint = if (source.endpoint.contains("{query}")) source.endpoint.replace("{query}", encoded)
            else source.endpoint + (if (source.endpoint.contains("?")) "&" else "?") + "search=$encoded"
        require(endpoint.startsWith("https://")) { "Custom API wajib menggunakan HTTPS." }
        val headers = if (source.apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${source.apiKey}")
        val conn = connection(endpoint, headers = headers)
        try {
            val root = JSONObject(readBody(conn))
            val array = root.optJSONArray("results") ?: root.optJSONArray("data") ?: JSONArray().put(root)
            return (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                val title = (item.optString("name").takeIf { it.isNotBlank() }
                    ?: item.optString("title")).trim()
                if (title.isBlank()) return@mapNotNull null
                val image = item.optString("background_image").takeIf { it.isBlank().not() }
                    ?: item.optString("cover").takeIf { it.isBlank().not() }
                    ?: item.optString("image").takeIf { it.isBlank().not() }
                val safeImage = image?.takeIf { it.startsWith("https://") }
                GameMetadataResult(title, item.optString("description").ifBlank { item.optString("summary") },
                    safeImage, item.optString("url").ifBlank { source.endpoint }, source.name, coverUrl = safeImage)
            }
        } finally { conn.disconnect() }
    }

    fun downloadArtwork(context: Context, packageName: String, kind: String, imageUrl: String): Uri? = runCatching {
        require(imageUrl.startsWith("https://")) { "Artwork harus menggunakan HTTPS." }
        val conn = connection(imageUrl)
        var temporaryFile: File? = null
        try {
            val status = conn.responseCode
            require(status in 200..299) { "Gagal mengunduh artwork (HTTP $status)." }
            val contentType = conn.contentType.orEmpty()
            require(contentType.isBlank() || contentType.startsWith("image/", ignoreCase = true)) {
                "Sumber tidak mengirim file gambar."
            }
            val directory = File(context.filesDir, "metadata_artwork").apply { mkdirs() }
            val safeKind = kind.filter { it.isLetterOrDigit() }.take(20).ifBlank { "artwork" }
            val target = File(directory, "${packageName.hashCode()}_$safeKind.img")
            // Temp unik mencegah dua pencarian untuk game yang sama saling menimpa.
            val temp = File.createTempFile("${target.name}.", ".tmp", directory)
            temporaryFile = temp
            var total = 0L
            conn.inputStream.use { input ->
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 12L * 1024L * 1024L) { "Ukuran artwork melebihi 12 MB." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            temp.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth in 1..16000 && bounds.outHeight in 1..16000) { "File artwork tidak valid." }
            // Temp dan target berada di direktori yang sama, sehingga rename menjadi
            // penggantian file atomik: artwork lama tetap utuh jika unduhan gagal.
            check(temp.renameTo(target)) { "Tidak dapat menyimpan artwork." }
            temporaryFile = null
            Uri.fromFile(target)
        } finally {
            conn.disconnect()
            temporaryFile?.delete()
        }
    }.getOrNull()

    fun downloadThumbnail(context: Context, packageName: String, imageUrl: String): Uri? =
        downloadArtwork(context, packageName, "metadata", imageUrl)
}

@Composable
fun GameMetadataDialog(app: AppItem, onDismiss: () -> Unit, onApply: (GameMetadataResult) -> Unit) {
    val context = LocalContext.current
    var query by remember(app.pkg) { mutableStateOf(app.name) }
    var results by remember(app.pkg) { mutableStateOf<List<GameMetadataResult>>(emptyList()) }
    var selected by remember(app.pkg) { mutableStateOf<GameMetadataResult?>(null) }
    var loading by remember(app.pkg) { mutableStateOf(false) }
    var error by remember(app.pkg) { mutableStateOf<String?>(null) }
    var activeNames by remember(app.pkg) { mutableStateOf(MetadataRepository.sources(context).filter { it.enabled }.joinToString { it.name }) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Column {
            Text("CARI METADATA GAME", color = Color.White, fontWeight = FontWeight.Bold)
            Text(app.name, color = Deck.TextDim, fontSize = 11.sp)
        } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it; selected = null },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nama game / judul porting") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sumber aktif: ${activeNames.ifBlank { "tidak ada" }}", color = Deck.TextDim, fontSize = 10.sp,
                        modifier = Modifier.weight(1f))
                    TextButton(enabled = query.isNotBlank() && !loading, onClick = {
                        scope.launch {
                            loading = true; error = null; selected = null
                            try {
                                activeNames = MetadataRepository.sources(context).filter { it.enabled }.joinToString { it.name }
                                results = MetadataRepository.search(context, query, app.pkg)
                                if (results.isEmpty()) error = "Tidak ada hasil dari sumber yang aktif."
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message?.take(220) ?: "Pencarian gagal. Periksa koneksi dan konfigurasi API."
                            } finally { loading = false }
                        }
                    }) { Text("CARI", color = Deck.Accent) }
                }
                if (loading) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text("Mencari dari sumber aktif…", color = Deck.TextDim, modifier = Modifier.padding(start = 10.dp))
                }
                error?.let { Text(it, color = Color(0xFFFF9A9A), fontSize = 11.sp) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 230.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(results) { result ->
                        Column(Modifier.fillMaxWidth().clickable { selected = result }.padding(9.dp)) {
                            Text(result.title, color = if (selected == result) Deck.Accent else Color.White,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(result.sourceName, color = Deck.Accent, fontSize = 9.sp)
                            Text(result.extract.ifBlank { "Tidak ada deskripsi dari sumber ini." }, color = Deck.TextDim,
                                fontSize = 10.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                selected?.let { result ->
                    Text("Dipilih: ${result.title} · ${result.sourceName}", color = Deck.Accent, fontSize = 11.sp)
                    val artworkStatus = when {
                        result.coverUrl != null && result.bannerUrl != null -> "Cover portrait dan banner landscape tersedia."
                        result.coverUrl != null -> "Artwork cover tersedia."
                        result.bannerUrl != null -> "Artwork banner tersedia."
                        result.thumbnailUrl != null -> "Artwork tersedia dari sumber ini."
                        else -> "Tidak ada artwork. Judul dan deskripsi tetap bisa diterapkan."
                    }
                    Text(artworkStatus, color = Deck.TextDim, fontSize = 10.sp)
                }
            }
        },
        confirmButton = { TextButton(enabled = selected != null, onClick = { selected?.let(onApply) }) {
            Text("TERAPKAN", color = Deck.Accent)
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("BATAL") } }
    )
}

@Composable
fun MetadataSourcesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var sources by remember { mutableStateOf(loadMetadataSources(context)) }
    var showCustom by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    fun update(source: MetadataSourceConfig) {
        sources = sources.map { if (it.id == source.id) source else it }
        persistMetadataSources(context, sources)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SUMBER DATABASE GAME", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Hanya sumber aktif yang akan dipanggil saat pencarian.", color = Deck.TextDim, fontSize = 11.sp)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(sources, key = { it.id }) { source ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(source.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(when (source.id) {
                                        "wikipedia" -> "Adapter aktif · tanpa API key"
                                        "google_play" -> "Adapter publik · package name otomatis + pencarian judul · tanpa API key"
                                        "rawg" -> "Adapter aktif · membutuhkan API key"
                                        "igdb" -> "Adapter aktif · Client ID:Access Token"
                                        "steamgriddb" -> "Adapter aktif · membutuhkan API key"
                                        else -> if (source.id.startsWith("custom_")) "Custom GET · query {query} atau parameter search" else "Provider tidak dikenal"
                                    }, color = Deck.TextDim, fontSize = 10.sp)
                                }
                                Switch(checked = source.enabled, onCheckedChange = { update(source.copy(enabled = it)) })
                            }
                            if (source.id in listOf("rawg", "igdb", "steamgriddb")) {
                                OutlinedTextField(value = source.apiKey, onValueChange = { update(source.copy(apiKey = it)) },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    label = { Text(when (source.id) {
                                        "rawg" -> "RAWG API key"
                                        "igdb" -> "IGDB Client ID:Access Token"
                                        else -> "SteamGridDB API key"
                                    }) })
                            }
                        }
                    }
                }
                TextButton(onClick = { showCustom = !showCustom }) {
                    Text(if (showCustom) "TUTUP FORM" else "+ TAMBAH CUSTOM API", color = Deck.Accent)
                }
                if (showCustom) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama database") }, singleLine = true)
                    OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("Endpoint HTTPS (gunakan {query})") }, singleLine = true)
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("Bearer token (opsional)") }, singleLine = true)
                    Text("Custom adapter GET membaca JSON results/data dengan field name/title, description/summary, dan image/cover/background_image.", color = Deck.TextDim, fontSize = 10.sp)
                    TextButton(enabled = name.isNotBlank() && endpoint.trim().startsWith("https://"),
                        onClick = {
                            val custom = MetadataSourceConfig("custom_${System.currentTimeMillis()}", name.trim(),
                                endpoint.trim(), apiKey.trim(), true)
                            sources = sources + custom
                            persistMetadataSources(context, sources)
                            name = ""; endpoint = ""; apiKey = ""; showCustom = false
                        }) { Text("SIMPAN SUMBER", color = Deck.Accent) }
                }
                Text("Catatan: API key saat ini disimpan di penyimpanan lokal aplikasi; jangan gunakan token sensitif yang tidak boleh tersimpan di perangkat.", color = Deck.TextDim, fontSize = 9.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("SELESAI", color = Deck.Accent) } }
    )
}
