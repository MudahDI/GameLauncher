package com.mdh.gamelauncher

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full launcher backup: SharedPreferences plus local copies of wallpaper and artwork.
 * Import replaces launcher preferences, but never touches installed applications or ROM files.
 */
object LauncherBackup {
    private const val PREFS_NAME = "launcher"
    private const val MANIFEST = "backup.json"
    private const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024

    fun export(context: Context, destination: Uri) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val values = JSONObject()
        val assets = linkedMapOf<String, ByteArray>()

        prefs.all.forEach { (key, value) ->
            val item = JSONObject()
            when {
                (key == "wallpaper_uri" || key.startsWith("artwork:")) && value is String -> {
                    val source = Uri.parse(value)
                    val assetName = "assets/asset_${assets.size}.bin"
                    val bytes = openInput(app, source).use { it.readBytes() }
                    assets[assetName] = bytes
                    item.put("type", "asset")
                    item.put("value", assetName)
                }
                value is Set<*> -> {
                    item.put("type", "string_set")
                    item.put("value", JSONArray(value.filterIsInstance<String>()))
                }
                value is String -> { item.put("type", "string"); item.put("value", value) }
                value is Boolean -> { item.put("type", "boolean"); item.put("value", value) }
                value is Int -> { item.put("type", "int"); item.put("value", value) }
                value is Long -> { item.put("type", "long"); item.put("value", value) }
                value is Float -> { item.put("type", "float"); item.put("value", value.toDouble()) }
                else -> return@forEach
            }
            values.put(key, item)
        }

        val manifest = JSONObject()
            .put("format", "mdi-launcher-backup")
            .put("version", 1)
            .put("preferences", values)
            .toString()
            .toByteArray(Charsets.UTF_8)

        app.contentResolver.openOutputStream(destination, "w")?.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifest)
                zip.closeEntry()
                assets.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        } ?: error("Tidak dapat membuat file backup.")
    }

    fun import(context: Context, source: Uri) {
        val app = context.applicationContext
        val entries = mutableMapOf<String, ByteArray>()
        var totalBytes = 0L
        app.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && !entry.name.startsWith("/") && !entry.name.contains("..")) {
                        "Struktur backup tidak valid."
                    }
                    require(entries.size < 501) { "Backup berisi terlalu banyak file." }
                    val bytes = zip.readBounded(if (entry.name == MANIFEST) MAX_MANIFEST_BYTES else 64 * 1024 * 1024)
                    totalBytes += bytes.size
                    require(totalBytes <= 256L * 1024 * 1024) { "Ukuran backup melebihi batas 256 MB." }
                    entries[entry.name] = bytes
                    zip.closeEntry()
                }
            }
        } ?: error("Tidak dapat membaca file backup.")

        val manifestBytes = entries[MANIFEST] ?: error("Manifest backup tidak ditemukan.")
        val manifest = JSONObject(String(manifestBytes, Charsets.UTF_8))
        require(manifest.optString("format") == "mdi-launcher-backup") { "File bukan backup MDI Launcher." }
        require(manifest.optInt("version") == 1) { "Versi backup tidak didukung." }

        val preferences = manifest.getJSONObject("preferences")
        val assetDir = File(app.filesDir, "backup_artwork").apply { mkdirs() }
        val editor = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear()
        val keys = preferences.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = preferences.getJSONObject(key)
            when (item.getString("type")) {
                "asset" -> {
                    val assetName = item.getString("value")
                    require(assetName.startsWith("assets/") && !assetName.contains("..")) { "Referensi aset tidak valid." }
                    val bytes = entries[assetName] ?: error("Aset backup hilang: $assetName")
                    val file = File(assetDir, "asset_${Integer.toHexString(key.hashCode())}.bin")
                    file.writeBytes(bytes)
                    editor.putString(key, Uri.fromFile(file).toString())
                }
                "string" -> editor.putString(key, item.getString("value"))
                "boolean" -> editor.putBoolean(key, item.getBoolean("value"))
                "int" -> editor.putInt(key, item.getInt("value"))
                "long" -> editor.putLong(key, item.getLong("value"))
                "float" -> editor.putFloat(key, item.getDouble("value").toFloat())
                "string_set" -> {
                    val array = item.getJSONArray("value")
                    val set = buildSet { for (i in 0 until array.length()) add(array.getString(i)) }
                    editor.putStringSet(key, set)
                }
                else -> error("Tipe data backup tidak dikenal.")
            }
        }
        check(editor.commit()) { "Gagal menyimpan data backup." }
    }

    private fun openInput(context: Context, uri: Uri) =
        if (uri.scheme == "file") FileInputStream(uri.path ?: error("Path artwork tidak valid."))
        else context.contentResolver.openInputStream(uri) ?: error("Artwork tidak dapat dibaca: $uri")
}

private fun ZipInputStream.readBounded(maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "Salah satu file di dalam backup terlalu besar." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
