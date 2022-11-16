package com.lalilu.lmusic.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.lmedia.database.sort
import com.lalilu.lmedia.entity.LPlaylist
import com.lalilu.lmedia.entity.LSong
import com.lalilu.lmedia.entity.SongInPlaylist
import com.lalilu.lmedia.indexer.Library
import com.lalilu.lmedia.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ItemPosition
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val playlistRepo: PlaylistRepository
) : ViewModel() {
    var songs by mutableStateOf(emptyList<LSong>())
    private var tempList by mutableStateOf(emptyList<SongInPlaylist>())

    fun onMoveItem(from: ItemPosition, to: ItemPosition) {
        val toIndex = songs.indexOfFirst { it.id == to.key }
        val fromIndex = songs.indexOfFirst { it.id == from.key }

        if (toIndex < 0 || fromIndex < 0) return
        songs = songs.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    }

    fun canDragOver(draggedOver: ItemPosition, dragging: ItemPosition) :Boolean = songs.any { draggedOver.key == it.id }

    fun onDragEnd(startIndex: Int, endIndex: Int) {
        val start = startIndex - 1
        val end = endIndex - 1
        if (start !in tempList.indices || end !in tempList.indices || start == end) return
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepo.moveSongInPlaylist(
                tempList[start],
                tempList[end],
                start < end //
            )
        }
    }

    fun getPlaylistDetailById(playlistId: Long, scope: CoroutineScope) {
        songs = emptyList()
        tempList = emptyList()
        playlistRepo.getSongInPlaylists(playlistId)
            .mapLatest { it.sort(true) }
            .debounce(50)
            .onEach { list ->
                songs = list.mapNotNull { Library.getSongOrNull(it.mediaId) }
                tempList = list
            }
            .launchIn(scope)
    }

    fun getPlaylistFlow(playlistId: Long): Flow<LPlaylist?> {
        return playlistRepo.getPlaylistById(playlistId)
    }
}