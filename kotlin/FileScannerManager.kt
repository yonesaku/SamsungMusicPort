package com.samsung.android.app.musiclibrary.scanner

import android.content.Context
import android.util.Log
import com.samsung.android.app.musiclibrary.tag.TagReader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object FileScannerManager {

    private const val TAG = "FileScannerManager"
    private const val CACHE_FILE = "music_scan_cache.json"

    private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "m4a", "wav", "aac", "opus")

    // Directories to scan on the device
    private val SCAN_DIRS = listOf(
        "/storage/emulated/0/Music",
        "/storage/emulated/0/Download",
        "/sdcard/Music",
        "/sdcard/Download"
    )

    // In-memory cache so we don't re-read from disk mid-session
    private var memoryCache: Map<String, TagReader.AudioTag>? = null

    /**
     * Main entry point. Returns cached results if available,
     * otherwise scans and saves to disk cache.
     */
    fun getAudioFiles(context: Context): Map<String, TagReader.AudioTag> {
        memoryCache?.let { return it }

        val cached = loadFromDiskCache(context)
        if (cached.isNotEmpty()) {
            memoryCache = cached
            return cached
        }

        return scanAndCache(context)
    }

    /**
     * Force a fresh scan, ignoring any cached data.
     */
    fun rescan(context: Context): Map<String, TagReader.AudioTag> {
        memoryCache = null
        clearDiskCache(context)
        return scanAndCache(context)
    }

    // ---- private helpers ----

    private fun scanAndCache(context: Context): Map<String, TagReader.AudioTag> {
        val files = mutableListOf<String>()

        for (dir in SCAN_DIRS) {
            scanDirectoryForAudio(File(dir), files)
        }

        Log.d(TAG, "Found ${files.size} audio files")

        val tagMap = TagReader.readMultipleTags(files)
        saveToDiskCache(context, tagMap)
        memoryCache = tagMap
        return tagMap
    }

    private fun scanDirectoryForAudio(dir: File, results: MutableList<String>) {
        if (!dir.isDirectory) return

        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                scanDirectoryForAudio(file, results)
            } else if (file.extension.lowercase() in AUDIO_EXTENSIONS) {
                results.add(file.absolutePath)
            }
        }
    }

    // ---- disk cache (simple JSON) ----

    private fun saveToDiskCache(context: Context, tagMap: Map<String, TagReader.AudioTag>) {
        try {
            val array = JSONArray()
            for ((path, tag) in tagMap) {
                val obj = JSONObject().apply {
                    put("path", path)
                    put("title", tag.title)
                    put("artist", tag.artist)
                    put("album", tag.album)
                    put("duration", tag.duration)
                    put("genre", tag.genre)
                }
                array.put(obj)
            }
            context.openFileOutput(CACHE_FILE, Context.MODE_PRIVATE).use {
                it.write(array.toString().toByteArray())
            }
            Log.d(TAG, "Cache saved: ${tagMap.size} entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cache", e)
        }
    }

    private fun loadFromDiskCache(context: Context): Map<String, TagReader.AudioTag> {
        return try {
            val cacheFile = File(context.filesDir, CACHE_FILE)
            if (!cacheFile.exists()) return emptyMap()

            val json = cacheFile.readText()
            val array = JSONArray(json)
            val result = mutableMapOf<String, TagReader.AudioTag>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val path = obj.getString("path")
                // Skip entries whose file no longer exists
                if (!File(path).exists()) continue
                result[path] = TagReader.AudioTag(
                    title    = obj.optString("title", "Unknown"),
                    artist   = obj.optString("artist", "Unknown"),
                    album    = obj.optString("album", "Unknown"),
                    duration = obj.optLong("duration", 0L),
                    genre    = obj.optString("genre", "Unknown")
                )
            }

            Log.d(TAG, "Loaded ${result.size} entries from cache")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cache", e)
            emptyMap()
        }
    }

    private fun clearDiskCache(context: Context) {
        try {
            File(context.filesDir, CACHE_FILE).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache", e)
        }
    }
}
