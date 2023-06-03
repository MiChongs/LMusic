package com.lalilu.lmusic.compose.new_screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lalilu.R
import com.lalilu.lmusic.api.lrcshare.SongResult
import com.lalilu.lmusic.compose.component.SmartBar
import com.lalilu.lmusic.compose.component.SmartContainer
import com.lalilu.lmusic.compose.component.card.SearchInputBar
import com.lalilu.lmusic.compose.component.navigate.NavigatorHeader
import com.lalilu.lmusic.utils.extension.dayNightTextColor
import com.lalilu.lmusic.viewmodel.SearchLyricViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.get

@Destination
@Composable
fun SearchLyricScreen(
    mediaId: String,
    keywords: String,
    searchLyricVM: SearchLyricViewModel = get(),
    navigator: DestinationsNavigator
) {
    val scope = rememberCoroutineScope { Dispatchers.Main }
    val showSearchBar = remember { mutableStateOf(true) }
    val selectedId = remember { mutableStateOf(-1) }

    LaunchedEffect(Unit) {
        // 若历史搜索的ID和当前ID不一致，则触发一次搜索
        if (searchLyricVM.lastSearchMediaId.value != mediaId) {
            searchLyricVM.searchFor(keywords)
        }
        searchLyricVM.lastSearchMediaId.value = mediaId
    }

    SmartBar.RegisterExtraBarContent(showState = showSearchBar) {
        SearchInputBar(
            value = keywords,
            onSearchFor = {
                searchLyricVM.searchFor(it)
            },
            onChecked = {
                searchLyricVM.saveLyricInto(selectedId.value, mediaId) {
                    scope.launch {
                        navigator.popBackStack()
                    }
                }
            }
        )
    }

    SmartContainer.LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            NavigatorHeader(
                title = "搜索LrcShare",
                subTitle = when (searchLyricVM.searchState.value) {
                    SearchLyricViewModel.SearchState.Idle -> "随便搜索看看"
                    SearchLyricViewModel.SearchState.Searching -> "搜索中..."
                    SearchLyricViewModel.SearchState.Error -> "搜索失败..."
                    SearchLyricViewModel.SearchState.Downloading -> "歌词保存中..."
                    SearchLyricViewModel.SearchState.Finished -> {
                        if (searchLyricVM.songResults.isEmpty()) "无结果" else "搜索到${searchLyricVM.songResults.size}条结果"
                    }
                }
            )
        }

        if (searchLyricVM.songResults.isNotEmpty()) {
            items(
                items = searchLyricVM.songResults,
                key = { it.id },
                contentType = { SongResult::class.java }
            ) {
                LyricCard(
                    title = it.song,
                    subTitle = it.artist,
                    caption = it.album ?: "",
                    imageData = it.cover,
                    selected = { selectedId.value == it.id },
                    onClick = { selectedId.value = it.id }
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun LyricCard(
    title: String,
    subTitle: String,
    caption: String,
    imageData: Any?,
    selected: () -> Boolean,
    onClick: () -> Unit
) {
    val color: Color by animateColorAsState(
        targetValue = if (selected()) contentColorFor(backgroundColor = MaterialTheme.colors.background)
            .copy(0.2f) else Color.Transparent,
        label = ""
    )

    Surface(
        color = color,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Surface(
                elevation = 2.dp,
                shape = RoundedCornerShape(5.dp)
            ) {
                AsyncImage(
                    modifier = Modifier
                        .size(64.dp)
                        .aspectRatio(1f),
                    model = ImageRequest.Builder(LocalContext.current)
                        .size(400)
                        .data(imageData)
                        .placeholder(R.drawable.ic_music_line_bg_64dp)
                        .error(R.drawable.ic_music_line_bg_64dp)
                        .crossfade(true)
                        .build(),
                    contentScale = ContentScale.Crop,
                    contentDescription = "Song Card Image"
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterVertically)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = dayNightTextColor(),
                        style = MaterialTheme.typography.subtitle1
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = subTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = dayNightTextColor(0.5f),
                        style = MaterialTheme.typography.caption,
                    )
                    Text(
                        modifier = Modifier.padding(start = 5.dp),
                        text = caption,
                        fontSize = 12.sp,
                        letterSpacing = 0.05.em,
                        color = dayNightTextColor(0.7f)
                    )
                }
            }
        }
    }
}