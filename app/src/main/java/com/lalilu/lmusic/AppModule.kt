package com.lalilu.lmusic

import androidx.room.Room
import com.lalilu.lmedia.LMedia
import com.lalilu.lmedia.database.LDatabase
import com.lalilu.lmedia.repository.FavoriteRepository
import com.lalilu.lmedia.repository.HistoryRepository
import com.lalilu.lmedia.repository.NetDataRepository
import com.lalilu.lmedia.repository.PlaylistRepository
import com.lalilu.lmedia.repository.impl.HistoryRepositoryImpl
import com.lalilu.lmedia.repository.impl.NetDataRepositoryImpl
import com.lalilu.lmedia.repository.impl.PlaylistRepositoryImpl
import com.lalilu.lmusic.apis.KugouDataSource
import com.lalilu.lmusic.apis.KugouLyricSource
import com.lalilu.lmusic.apis.KugouSongsSource
import com.lalilu.lmusic.apis.NeteaseDataSource
import com.lalilu.lmusic.datastore.LMusicSp
import com.lalilu.lmusic.datastore.LastPlayedSp
import com.lalilu.lmusic.repository.CoverRepository
import com.lalilu.lmusic.repository.LMediaRepository
import com.lalilu.lmusic.repository.LyricRepository
import com.lalilu.lmusic.service.LMusicBrowser
import com.lalilu.lmusic.service.notification.LMusicNotifier
import com.lalilu.lmusic.service.playback.helper.LMusicAudioFocusHelper
import com.lalilu.lmusic.service.playback.helper.LMusicNoisyReceiver
import com.lalilu.lmusic.service.playback.impl.LocalPlayer
import com.lalilu.lmusic.service.runtime.LMusicRuntime
import com.lalilu.lmusic.utils.sources.DataBaseLyricSource
import com.lalilu.lmusic.utils.sources.EmbeddedLyricSource
import com.lalilu.lmusic.utils.sources.LocalLyricSource
import com.lalilu.lmusic.utils.sources.LyricSourceFactory
import com.lalilu.lmusic.viewmodel.AlbumsViewModel
import com.lalilu.lmusic.viewmodel.ArtistsViewModel
import com.lalilu.lmusic.viewmodel.DictionariesViewModel
import com.lalilu.lmusic.viewmodel.HistoryViewModel
import com.lalilu.lmusic.viewmodel.LMediaViewModel
import com.lalilu.lmusic.viewmodel.LibraryViewModel
import com.lalilu.lmusic.viewmodel.NetDataViewModel
import com.lalilu.lmusic.viewmodel.PlayingViewModel
import com.lalilu.lmusic.viewmodel.PlaylistDetailViewModel
import com.lalilu.lmusic.viewmodel.PlaylistsViewModel
import com.lalilu.lmusic.viewmodel.SearchViewModel
import com.lalilu.lmusic.viewmodel.SongsViewModel
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val AppModule = module {
    single { LMusicSp(androidApplication()) }
    single { LastPlayedSp(androidApplication()) }
}

val DatabaseModule = module {
    single {
        Room.databaseBuilder(androidApplication(), LDatabase::class.java, "lmedia_database.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    single<NetDataRepository> { NetDataRepositoryImpl(get<LDatabase>().netDataDao()) }
    single<HistoryRepository> { HistoryRepositoryImpl(get<LDatabase>().historyDao()) }
    single<PlaylistRepository> { get<PlaylistRepositoryImpl>() }
    single<FavoriteRepository> { get<PlaylistRepositoryImpl>() }
    single {
        PlaylistRepositoryImpl(
            playlistDao = get<LDatabase>().playlistDao(),
            songInPlaylistDao = get<LDatabase>().songInPlaylistDao(),
            getSongOrNull = LMedia::getSongOrNull
        )
    }
}

val ViewModelModule = module {
    single { NetDataViewModel(get(), get(), get(), get()) }
    single { PlayingViewModel(get(), get(), get(), get()) }
    single { LibraryViewModel(get(), get()) }
    single { LMediaViewModel(get()) }
    single { PlaylistDetailViewModel(get(), get()) }
    single { PlaylistsViewModel(get(), get()) }
    single { SearchViewModel(get(), get()) }
    single { AlbumsViewModel(get()) }
    single { ArtistsViewModel(get()) }
    single { DictionariesViewModel(get(), get()) }
    single { HistoryViewModel(get(), get()) }
    single { SongsViewModel(get(), get()) }
}

val PlayerModule = module {
    single { LMusicAudioFocusHelper(androidApplication(), get()) }
    single { LMusicNoisyReceiver(androidApplication()) }
    single { LocalPlayer(androidApplication()) }
}

val RuntimeModule = module {
    single { LMusicNotifier(get(), get(), get(), androidApplication()) }
    single { LMusicBrowser(get(), get(), get(), get()) }
    single { LMusicRuntime(get()) }
    single { CoverRepository(get()) }
    single { LyricRepository(get(), get()) }
    single { LMediaRepository() }

    single { LyricSourceFactory(get(), get(), get()) }
    single { EmbeddedLyricSource() }
    single { LocalLyricSource() }
    single { DataBaseLyricSource(get()) }
}

val ApiModule = module {
    single { GsonConverterFactory.create() }

    single {
        // TODO kugou的接口存在证书错误问题，暂时只能本机做忽略证书校验
        OkHttpClient.Builder()
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    single {
        Retrofit.Builder()
            .baseUrl(Config.BASE_NETEASE_URL)
            .client(get())
            .addConverterFactory(get<GsonConverterFactory>())
            .build()
            .create(NeteaseDataSource::class.java)
    }

    single {
        Retrofit.Builder()
            .baseUrl(Config.BASE_KUGOU_SONGS_URL)
            .client(get())
            .addConverterFactory(get<GsonConverterFactory>())
            .build()
            .create(KugouSongsSource::class.java)
    }

    single {
        Retrofit.Builder()
            .baseUrl(Config.BASE_KUGOU_LYRIC_URL)
            .client(get())
            .addConverterFactory(get<GsonConverterFactory>())
            .build()
            .create(KugouLyricSource::class.java)
    }

    single { KugouDataSource(get(), get()) }
}