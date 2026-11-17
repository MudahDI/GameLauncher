package com.mdh.gamelauncher

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GameMetadataResult(
    val title: String,
    val extract: String,
    val thumbnailUrl: String?,
    val pageUrl: String
)

object MetadataRepository {
    suspend fun search(query: String): List<GameMetadataResult> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = URL(
            "https://en.wikipedia.org/w/api.php?action=query&generator=search" +
                "&gsrsearch=$encoded&gsrnamespace=0&gsrlimit=8" +
                "&prop=pageimages%7Cextracts&piprop=thumbnail&pithumbsize=900" +
                "&exintro=1&explaintext=1&format=json&utf8=1"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MDILauncher/2.0 (Android game metadata lookup)")
        }
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return@withContext emptyList()
            val results = mutableListOf<Pair<Int, GameMetadataResult>>()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.getJSONObject(keys.next())
                val title = page.optString("title").trim()
                if (title.isBlank()) continue
                val thumbnail = page.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.startsWith("https://") }
                val pageId = page.optLong("pageid")
                results += page.optInt("index", Int.MAX_VALUE) to GameMetadataResult(
                    title = title,
                    extract = page.optString("extract").trim(),
                    thumbnailUrl = thumbnail,
                    pageUrl = "https://en.wikipedia.org/?curid=$pageId"
                )
            }
            results.sortedBy { it.first }.map { it.second }
        } finally {
            connection.disconnect()
        }
    }

    fun downloadThumbnail(context: Context, packageName: String, imageUrl: String): Uri? = runCatching {
        val url = URL(imageUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("User-Agent", "MDILauncher/2.0")
        }
        try {
            val directory = File(context.filesDir, "metadata_artwork").apply { mkdirs() }
            val target = File(directory, "${packageName.hashCode()}_wiki.img")
            connection.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            target.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                target.delete()
                null
            } else {
                Uri.fromFile(target)
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

@Composable
fun GameMetadataDialog(
    app: AppItem,
    onDismiss: () -> Unit,
    onApply: (GameMetadataResult) -> Unit
) {
    var query by remember(app.pkg) { mutableStateOf(app.name) }
    var results by remember(app.pkg) { mutableStateOf<List<GameMetadataResult>>(emptyList()) }
    var selected by remember(app.pkg) { mutableStateOf<GameMetadataResult?>(null) }
    var loading by remember(app.pkg) { mutableStateOf(false) }
    var error by remember(app.pkg) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("CARI METADATA GAME", color = Color.White, fontWeight = FontWeight.Bold)
                Text(app.name, color = Deck.TextDim, fontSize = 11.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; selected = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nama game / judul porting") }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sumber: Wikipedia", color = Deck.TextDim, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = query.isNotBlank() && !loading,
                        onClick = {
                            scope.launch {
                                loading = true
                                error = null
                                selected = null
                                try {
                                    results = MetadataRepository.search(query)
                                    if (results.isEmpty()) error = "Tidak ada hasil. Coba judul alternatif atau nama game aslinya."
                                } catch (_: Exception) {
                                    error = "Pencarian gagal. Periksa koneksi internet lalu coba lagi."
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    ) { Text("CARI", color = Deck.Accent) }
                }
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Text("Mencari metadata…", color = Deck.TextDim, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                error?.let { Text(it, color = Color(0xFFFF9A9A), fontSize = 11.sp) }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(results) { result ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { selected = result }
                                .padding(9.dp)
                        ) {
                            Text(
                                result.title,
                                color = if (selected == result) Deck.Accent else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                result.extract.ifBlank { "Tidak ada ringkasan tersedia." },
                                color = Deck.TextDim,
                                fontSize = 10.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                selected?.let { result ->
                    Text("Dipilih: ${result.title}", color = Deck.Accent, fontSize = 11.sp)
                    Text(
                        if (result.thumbnailUrl != null) "Artwork thumbnail tersedia; akan disimpan untuk cover dan banner."
                        else "Tidak ada thumbnail. Judul dan deskripsi tetap bisa diterapkan.",
                        color = Deck.TextDim,
                        fontSize = 10.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected != null, onClick = { selected?.let(onApply) }) {
                Text("TERAPKAN", color = Deck.Accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("BATAL") } }
    )
}
