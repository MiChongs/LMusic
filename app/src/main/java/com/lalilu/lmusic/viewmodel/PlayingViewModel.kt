package com.lalilu.lmusic.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.lalilu.lmedia.entity.LSong
import com.lalilu.lmusic.datastore.SettingsSp
import com.lalilu.lmusic.repository.LyricRepository
import com.lalilu.lmusic.service.LMusicBrowser
import com.lalilu.lmusic.service.LMusicRuntime
import com.lalilu.lmusic.utils.extension.toState
import com.lalilu.lplayer.playback.Playback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayingViewModel(
    val runtime: LMusicRuntime,
    val browser: LMusicBrowser,
    val settingsSp: SettingsSp,
    val lyricRepository: LyricRepository
) : ViewModel() {
    private val playing = runtime.playingFlow.toState(viewModelScope)
    private val isPlaying = runtime.isPlayingFlow.toState(false, viewModelScope)

    val currentLyric = lyricRepository.currentLyric.asLiveData(viewModelScope.coroutineContext)
    val currentPosition = runtime.positionFlow.asLiveData(viewModelScope.coroutineContext)
    val currentPlaying = runtime.playingFlow.asLiveData(viewModelScope.coroutineContext)
    val currentSongs = runtime.songsFlow.asLiveData(viewModelScope.coroutineContext)

    fun play(
        song: LSong,
        songs: List<LSong>? = null,
        playOrPause: Boolean = false,
        addToNext: Boolean = false
    ) = play(song.id, songs?.map(LSong::id), playOrPause, addToNext)

    /**
     * 综合播放操作
     *
     * @param mediaId 目标歌曲的ID
     * @param mediaIds 歌曲ID列表
     * @param playOrPause 当前正在播放则暂停，暂停则开始播放
     * @param addToNext 是否在播放前将该歌曲移动到下一首播放的位置
     */
    fun play(
        mediaId: String,
        mediaIds: List<String>? = null,
        playOrPause: Boolean = false,
        addToNext: Boolean = false
    ) = viewModelScope.launch {
        if (mediaIds != null) {
            browser.setSongs(mediaIds)
        }
        if (addToNext) {
            browser.addToNext(mediaId)
        }

        when {
            mediaId == runtime.getPlayingId() && playOrPause -> {
                browser.sendCustomAction(Playback.PlaybackAction.PlayPause)
            }

            else -> {
                // TODO 存在播放列表中不存在该歌曲的情况
                browser.playById(mediaId)
            }
        }
    }

    fun isSongPlaying(mediaId: String): Boolean {
        if (!isPlaying.value) return false
        return playing.value?.let { it.id == mediaId } ?: false
    }

    fun isAlbumPlaying(albumId: String): Boolean {
        if (!isPlaying.value) return false
        return playing.value?.let { it.album?.id == albumId } ?: false
    }

    fun isArtistPlaying(artistName: String): Boolean {
        if (!isPlaying.value) return false
        return playing.value?.let { song -> song.artists.any { it.name == artistName } } ?: false
    }

    fun requireLyric(item: LSong, callback: (hasLyric: Boolean) -> Unit) {
        viewModelScope.launch {
            if (isActive) {
                val hasLyric = lyricRepository.hasLyric(item)
                withContext(Dispatchers.Main) { callback(hasLyric) }
            }
        }
    }

    fun requireHasLyricState(item: LSong): MutableState<Boolean> {
        return mutableStateOf(false).also {
            viewModelScope.launch {
                if (isActive) {
                    it.value = lyricRepository.hasLyric(item)
                }
            }
        }
    }
}