package me.him188.ani.android

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import me.him188.ani.android.activity.MainActivity
import me.him188.ani.android.navigation.AndroidBrowserNavigator
import me.him188.ani.app.data.repository.SettingsRepository
import me.him188.ani.app.data.source.media.resolver.AndroidWebVideoSourceResolver
import me.him188.ani.app.data.source.media.resolver.HttpStreamingVideoSourceResolver
import me.him188.ani.app.data.source.media.resolver.LocalFileVideoSourceResolver
import me.him188.ani.app.data.source.media.resolver.TorrentVideoSourceResolver
import me.him188.ani.app.data.source.media.resolver.VideoSourceResolver
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.platform.AndroidPermissionManager
import me.him188.ani.app.platform.PermissionManager
import me.him188.ani.app.platform.notification.AndroidNotifManager
import me.him188.ani.app.platform.notification.NotifManager
import me.him188.ani.app.tools.DocumentUriParser
import me.him188.ani.app.tools.torrent.DefaultTorrentManager
import me.him188.ani.app.tools.torrent.TorrentManager
import me.him188.ani.app.tools.update.AndroidUpdateInstaller
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.app.videoplayer.ExoPlayerStateFactory
import me.him188.ani.app.videoplayer.ui.state.PlayerStateFactory
import me.him188.ani.utils.io.inSystem
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

private const val BT_CACHE_DIR_CHANGED = "BT 存储位置不可用，已切换回默认存储位置。"

fun getAndroidModules(
    getContext: () -> Context,
    defaultTorrentCacheDir: File,
    coroutineScope: CoroutineScope,
) = module {
    single<PermissionManager> {
        AndroidPermissionManager()
    }
    single<NotifManager> {
        AndroidNotifManager(
            NotificationManagerCompat.from(androidContext()),
            getContext = { androidContext() },
            activityIntent = {
                Intent(androidContext(), MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
//                androidContext().packageManager.getLaunchIntentForPackage(androidContext().packageName)
//                    ?: Intent(Intent.ACTION_MAIN).apply {
//                        setPackage(androidContext().packageName)
//                    }
            },
            coroutineScope.coroutineContext,
        ).apply { createChannels() }
    }
    single<BrowserNavigator> { AndroidBrowserNavigator() }
    single<PlayerStateFactory> { ExoPlayerStateFactory() }

    single<TorrentManager> {
        val defaultTorrentCachePath = defaultTorrentCacheDir.absolutePath
        val cacheDir = runBlocking {
            val settings = get<SettingsRepository>().mediaCacheSettings
            val dir = settings.flow.first().saveDir

            // 首次启动设置为应用内部私有目录
            if (dir == null) {
                settings.update { copy(saveDir = defaultTorrentCachePath) }
                return@runBlocking defaultTorrentCachePath
            }

            if (dir.startsWith(getContext().filesDir.absolutePath)) {
                // 在设置中保存的是私有目录，直接返回
                return@runBlocking dir
            }

            getContext().contentResolver.persistedUriPermissions.forEach { p ->
                if (DocumentUriParser.parseUriToStorage(getContext(), p.uri) == dir) {
                    return@runBlocking if (p.isReadPermission && p.isWritePermission) {
                        // 在设置中保存的外部共享目录有完整的读写权限，直接返回 dir
                        dir
                    } else {
                        // 在设置中保存的外部共享目录没有完整的读写权限，直接切换回默认的内部私有目录,
                        // 避免读写权限不足错误导致 App 崩溃
                        settings.update { copy(saveDir = defaultTorrentCachePath) }
                        Toast.makeText(getContext(), BT_CACHE_DIR_CHANGED, Toast.LENGTH_LONG).show()
                        defaultTorrentCachePath
                    }
                }
            }
            // 既不是内部私有目录也不是外部共享目录，那一定是外部私有目录
            if (getContext().getExternalFilesDir(null) == null) {
                // 外部私有目录不可用，直接切换回默认的私有目录，避免读写权限不足错误导致 App 崩溃
                settings.update { copy(saveDir = defaultTorrentCachePath) }
                Toast.makeText(getContext(), BT_CACHE_DIR_CHANGED, Toast.LENGTH_LONG).show()
                return@runBlocking defaultTorrentCachePath
            }

            // 外部私有目录可用
            dir
        }

        DefaultTorrentManager(
            coroutineScope.coroutineContext,
            saveDir = { Path(cacheDir).inSystem },
        )
    }


    factory<VideoSourceResolver> {
        VideoSourceResolver.from(
            get<TorrentManager>().engines
                .map { TorrentVideoSourceResolver(it) }
                .plus(LocalFileVideoSourceResolver())
                .plus(HttpStreamingVideoSourceResolver())
                .plus(AndroidWebVideoSourceResolver()),
        )
    }
    single<UpdateInstaller> { AndroidUpdateInstaller() }
}
