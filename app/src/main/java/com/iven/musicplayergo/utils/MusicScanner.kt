package com.iven.musicplayergo.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.iven.musicplayergo.models.Music

object MusicScanner {
    private val supportedExtensions = listOf(".mp3", ".flac", ".wav", ".m4a")

    fun scanFolder(context: Context, uri: Uri): List<Music> {
        val folder = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
        val foundMusic = mutableListOf<Music>()

        fun recurse(doc: DocumentFile) {
            if (doc.isDirectory) {
                doc.listFiles().forEach { recurse(it) }
            } else if (doc.isFile && supportedExtensions.any { doc.name?.endsWith(it, true) == true }) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, doc.uri)
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: doc.name
                    val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "未知艺术家"
                    val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "未知专辑"
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

                    val music = Music(
                        artist = artist,
                        year = 0,
                        track = 0,
                        title = title,
                        displayName = doc.name ?: "unknown",
                        duration = duration,
                        album = album,
                        albumId = 0L,
                        relativePath = "手动导入",
                        id = System.currentTimeMillis(),
                        launchedBy = "manual", // ✅ 正确传入字符串
                        startFrom = 0,
                        dateAdded = 0
                    )
                    foundMusic.add(music)
                    Log.d("MusicScanner", "✅ 找到歌曲: $title - $artist")
                } catch (e: Exception) {
                    Log.e("MusicScanner", "❌ 无法读取文件: ${doc.uri}")
                } finally {
                    retriever.release()
                }
            }
        }

        recurse(folder)
        return foundMusic
    }
}
