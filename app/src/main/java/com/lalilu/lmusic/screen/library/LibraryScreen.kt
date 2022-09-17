package com.lalilu.lmusic.screen.library

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lalilu.R
import com.lalilu.lmedia.entity.LSong
import com.lalilu.lmedia.indexer.Library
import com.lalilu.lmusic.screen.MainScreenData
import com.lalilu.lmusic.screen.component.SmartBar
import com.lalilu.lmusic.screen.component.card.ExpendableTextCard
import com.lalilu.lmusic.screen.component.card.RecommendCard
import com.lalilu.lmusic.screen.component.card.RecommendCard2
import com.lalilu.lmusic.service.LMusicBrowser
import com.lalilu.lmusic.service.LMusicRuntime
import com.lalilu.lmusic.utils.extension.LocalNavigatorHost
import com.lalilu.lmusic.utils.extension.dayNightTextColor
import com.lalilu.lmusic.viewmodel.LibraryViewModel

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel
) {
    val navController = LocalNavigatorHost.current
    val dailyRecommends = remember { viewModel.requireDailyRecommends() }
    val recentlyAdded = remember { Library.getSongs(15) }
    val randomRecommends = remember { Library.getSongs(15, true) }
    val lastPlayedStack by viewModel.lastPlayedStack.observeAsState()

    val currentPlaying by LMusicRuntime.currentPlayingFlow.collectAsState()
    val currentIsPlaying by LMusicRuntime.currentIsPlayingFlow.collectAsState()

    val playSong = remember {
        { mediaId: String ->
            currentPlaying.takeIf { it != null && it.id == mediaId && currentIsPlaying }
                ?.let { LMusicBrowser.pause() }
                ?: LMusicBrowser.addAndPlay(mediaId)
        }
    }

    val showDetail = remember {
        { mediaId: String ->
            navController.navigate("${MainScreenData.SongsDetail.name}/$mediaId") {
                launchSingleTop = true
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = SmartBar.rememberContentPadding()
    ) {
        dailyRecommends.takeIf { it.isNotEmpty() }?.let {
            item {
                RecommendTitle("每日推荐", onClick = { })
            }
            item {
                RecommendRow(
                    items = it
                ) {
                    RecommendCard2(
                        data = { it },
                        getId = { it.id },
                        width = 250.dp,
                        height = 250.dp,
                        onShowDetail = showDetail
                    ) {
                        ExpendableTextCard(
                            title = it.name,
                            subTitle = it._artist,
                            defaultState = true,
                            titleColor = Color.White,
                            subTitleColor = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        recentlyAdded.takeIf { it.isNotEmpty() }?.let {
            item {
                // 最近添加
                RecommendTitle("最近添加", onClick = { })
            }
            item {
                RecommendRow(
                    items = it
                ) {
                    RecommendCardWithOutSideText(
                        song = it,
                        isPlaying = (currentIsPlaying && currentPlaying != null && it.id == currentPlaying?.id),
                        onPlaySong = playSong,
                        onShowDetail = showDetail
                    )
                }
            }
        }


        lastPlayedStack?.takeIf { it.isNotEmpty() }?.let {
            item {
                RecommendTitle("最近播放")
            }
            item {
                RecommendRow(
                    items = it
                ) {
                    RecommendCardWithOutSideText(
                        song = it,
                        width = 125.dp,
                        height = 125.dp,
                        isPlaying = (currentIsPlaying && currentPlaying != null && it.id == currentPlaying?.id),
                        onPlaySong = playSong,
                        onShowDetail = showDetail
                    )
                }
            }
        }

        randomRecommends.takeIf { it.isNotEmpty() }?.let {
            item {
                RecommendTitle("随机推荐", onClick = { })
            }
            item {
                RecommendRow(
                    items = it
                ) {
                    RecommendCard2(
                        data = { it },
                        getId = { it.id },
                        width = 125.dp,
                        height = 250.dp,
                        onShowDetail = showDetail
                    ) {
                        ExpendableTextCard(
                            title = it.name,
                            subTitle = it._artist,
                            titleColor = Color.White,
                            subTitleColor = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendTitle(title: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            modifier = Modifier
                .weight(1f),
            text = title,
            style = MaterialTheme.typography.h6,
            color = dayNightTextColor()
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_right_s_line),
            contentDescription = "",
            tint = dayNightTextColor()
        )
    }
}

@Composable
fun <I> RecommendRow(
    items: Collection<I>,
    itemContent: @Composable LazyItemScope.(item: I) -> Unit
) {
    LazyRow(
        modifier = Modifier.animateContentSize(
            animationSpec = SpringSpec(stiffness = Spring.StiffnessLow)
        ),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        items.forEach {
            item { itemContent(it) }
        }
    }
}

@Composable
fun RecommendCardWithOutSideText(
    modifier: Modifier = Modifier,
    song: LSong,
    width: Dp = 200.dp,
    height: Dp = 125.dp,
    isPlaying: Boolean = false,
    onShowDetail: (String) -> Unit = {},
    onPlaySong: (String) -> Unit = {}
) {
    Column(
        modifier = modifier.width(IntrinsicSize.Min),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RecommendCard(
            data = { song },
            getId = { song.id },
            isPlaying = isPlaying,
            width = width,
            height = height,
            onPlay = onPlaySong,
            onShowDetail = onShowDetail
        )
        ExpendableTextCard(
            title = song.name,
            subTitle = song._artist
        )
    }
}