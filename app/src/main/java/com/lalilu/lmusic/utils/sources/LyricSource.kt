package com.lalilu.lmusic.utils.sources

import android.text.TextUtils
import com.lalilu.lmedia.entity.LSong
import com.lalilu.lmedia.repository.NetDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

interface LyricSource {
    suspend fun loadLyric(song: LSong): Pair<String, String?>?

}

class LyricSourceFactory(
    dataBaseLyricSource: DataBaseLyricSource,
    embeddedLyricSource: EmbeddedLyricSource,
    localLyricSource: LocalLyricSource
) : LyricSource {
    private val sources = listOf(dataBaseLyricSource, embeddedLyricSource, localLyricSource)

    override suspend fun loadLyric(song: LSong): Pair<String, String?>? =
        withContext(Dispatchers.IO) {
            for (source in sources) {
                val pair = source.loadLyric(song)
                if (pair != null) return@withContext pair
            }
            return@withContext null
        }
}

class EmbeddedLyricSource : LyricSource {
    override suspend fun loadLyric(song: LSong): Pair<String, String?>? =
        withContext(Dispatchers.IO) {
            val songData = song.pathStr ?: return@withContext null
            val file = File(songData)
            if (!file.exists()) return@withContext null
            kotlin.runCatching {
                Logger.getLogger("org.jaudiotagger").level = Level.OFF
                val tag = AudioFileIO.read(file).tag
                val lyric = tag.getFields(FieldKey.LYRICS)
                    .run { if (isNotEmpty()) get(0).toString() else null }
                    ?: return@withContext null
                return@withContext if (TextUtils.isEmpty(lyric)) null
                else Pair(lyric, null)
            }
            null
        }
}

class LocalLyricSource : LyricSource {
    override suspend fun loadLyric(song: LSong): Pair<String, String?>? =
        withContext(Dispatchers.IO) {
            val songData = song.pathStr ?: return@withContext null
            val path = songData.substring(0, songData.lastIndexOf('.')) + ".lrc"
            val lrcFile = File(path)

            if (!lrcFile.exists()) return@withContext null

            val lyric = lrcFile.readText()
            return@withContext if (TextUtils.isEmpty(lyric)) null
            else Pair(lyric, null)
        }
}

class DataBaseLyricSource(
    private val netDataRepo: NetDataRepository
) : LyricSource {
    override suspend fun loadLyric(song: LSong): Pair<String, String?>? =
        withContext(Dispatchers.IO) {
            val pair = netDataRepo.getNetDataById(song.id)
            pair?.lyric?.let { it to pair.tlyric }
        }
}