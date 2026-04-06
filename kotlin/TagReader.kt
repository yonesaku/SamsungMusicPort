package com.samsung.android.app.musiclibrary.tag

import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

object TagReader {

    private const val TAG = "TagReader"

    data class AudioTag(
        var title: String = "Unknown",
        var artist: String = "Unknown",
        var album: String = "Unknown",
        var duration: Long = 0,
        var genre: String = "Unknown"
    )

    fun readTagFromFile(filePath: String): AudioTag {
        return try {
            val file = File(filePath)
            if (!file.exists()) return AudioTag()

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            val header = audioFile.audioHeader

            AudioTag(
                title    = tag?.getFirst(FieldKey.TITLE)  ?: file.nameWithoutExtension,
                artist   = tag?.getFirst(FieldKey.ARTIST) ?: "Unknown",
                album    = tag?.getFirst(FieldKey.ALBUM)  ?: "Unknown",
                duration = (header?.trackLength?.toLong() ?: 0L) * 1000L,
                genre    = tag?.getFirst(FieldKey.GENRE)  ?: "Unknown"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read tag for $filePath", e)
            AudioTag()
        }
    }

    fun readMultipleTags(filePaths: List<String>): Map<String, AudioTag> {
        return filePaths.associateWith { readTagFromFile(it) }
    }
}
