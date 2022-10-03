package com.lalilu.lmusic.screen.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.hilt.navigation.compose.hiltViewModel
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.TimeUtils
import com.lalilu.R
import com.lalilu.databinding.FragmentInputerBinding
import com.lalilu.lmedia.entity.LSong
import com.lalilu.lmusic.apis.NetworkSong
import com.lalilu.lmusic.apis.NetworkSource
import com.lalilu.lmusic.screen.component.NavigatorHeader
import com.lalilu.lmusic.screen.component.SmartBar
import com.lalilu.lmusic.screen.component.SmartContainer
import com.lalilu.lmusic.screen.component.SmartModalBottomSheet
import com.lalilu.lmusic.utils.extension.LocalNavigatorHost
import com.lalilu.lmusic.utils.extension.getActivity
import com.lalilu.lmusic.viewmodel.NetworkDataViewModel

@Composable
fun MatchNetworkDataScreen(
    song: LSong,
    viewModel: NetworkDataViewModel = hiltViewModel()
) {
    val keyword = "${song.name} ${song._artist}"
    val msg = remember { mutableStateOf("") }
    val songs = remember { mutableStateListOf<NetworkSong>() }
    var selectedIndex by remember { mutableStateOf(-1) }
    val navController = LocalNavigatorHost.current

    LaunchedEffect(Unit) {
        SmartBar.setExtraBar {
            SearchInputBar(keyword, onSearchFor = {
                if (it.isNotEmpty()) {
                    viewModel.getSongResult(
                        keyword = it,
                        items = songs,
                        msg = msg
                    )
                }
            }, onChecked = {
                viewModel.saveMatchNetworkData(
                    mediaId = song.id,
                    networkSong = songs.getOrNull(selectedIndex),
                    success = { navController.navigateUp() }
                )
            })
        }
        viewModel.getSongResult(
            keyword = keyword,
            items = songs,
            msg = msg
        )
    }

    SmartContainer.LazyColumn {
        item {
            NavigatorHeader(
                title = stringResource(id = R.string.destination_label_match_network_data),
                subTitle = songs.size.takeIf { it > 0 }?.let { "共搜索到 ${songs.size} 条结果" }
                    ?: msg.value,
            )
        }
        itemsIndexed(songs) { index, item ->
            LyricCard(
                song = item,
                selected = index == selectedIndex,
                onClick = { selectedIndex = index }
            )
        }
    }
}

@Composable
fun SearchInputBar(
    value: String,
    onSearchFor: (String) -> Unit,
    onChecked: () -> Unit
) {
    var text by remember { mutableStateOf(value) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        AndroidViewBinding(
            modifier = Modifier.weight(1f),
            factory = { inflater, parent, attachToParent ->
                FragmentInputerBinding.inflate(inflater, parent, attachToParent).apply {
                    val activity = parent.context.getActivity()!!
                    searchForLyricKeyword.setText(value)

                    searchForLyricKeyword.setOnEditorActionListener { textView, _, _ ->
                        text = textView.text.toString()
                        onSearchFor(text)
                        textView.clearFocus()
                        KeyboardUtils.hideSoftInput(textView)
                        return@setOnEditorActionListener true
                    }

                    KeyboardUtils.registerSoftInputChangedListener(activity) {
                        if (searchForLyricKeyword.isFocused && it > 0) {
                            SmartModalBottomSheet.expend()
                            return@registerSoftInputChangedListener
                        }
                        if (searchForLyricKeyword.isFocused) {
                            searchForLyricKeyword.onEditorAction(0)
                        }
                    }
                }
            })

        IconButton(onClick = { onSearchFor(text) }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_search_2_line),
                contentDescription = "搜索按钮"
            )
        }

        IconButton(onClick = onChecked) {
            Icon(
                painter = painterResource(id = R.drawable.ic_check_line),
                contentDescription = "搜索按钮"
            )
        }
    }
}

@Composable
fun LyricCard(
    song: NetworkSong,
    selected: Boolean,
    onClick: () -> Unit
) = LyricCard(
    title = song.songTitle,
    artist = song.songArtist,
    albumTitle = song.songAlbum,
    duration = TimeUtils.millis2String(
        song.songDuration,
        "mm:ss"
    ) + " " + NetworkSource.of(song.fromPlatform).text,
    selected = selected,
    onClick = onClick
)

@Composable
fun LyricCard(
    title: String,
    artist: String,
    albumTitle: String?,
    duration: String?,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val textColor = contentColorFor(backgroundColor = MaterialTheme.colors.background)
    val color: Color by animateColorAsState(
        if (selected) contentColorFor(
            backgroundColor = MaterialTheme.colors.background
        ).copy(0.2f) else Color.Transparent
    )

    Surface(color = color) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .clickable(
                    onClick = onClick,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                duration?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = textColor,
                        textAlign = TextAlign.End
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = artist,
                    fontSize = 12.sp,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                albumTitle?.let {
                    Text(
                        text = it,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptySearchForLyricScreen() {
    Text(text = "无法获取该歌曲信息", modifier = Modifier.padding(20.dp))
}