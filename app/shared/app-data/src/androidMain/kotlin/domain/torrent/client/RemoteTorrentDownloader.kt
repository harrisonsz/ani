/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.client

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.io.files.Path
import me.him188.ani.app.domain.torrent.IDisposableHandle
import me.him188.ani.app.domain.torrent.IRemoteTorrentDownloader
import me.him188.ani.app.domain.torrent.IRemoteTorrentSession
import me.him188.ani.app.domain.torrent.callback.ITorrentDownloaderStatsCallback
import me.him188.ani.app.domain.torrent.cont.ContTorrentDownloaderFetchTorrent
import me.him188.ani.app.domain.torrent.cont.ContTorrentDownloaderStartDownload
import me.him188.ani.app.domain.torrent.parcel.PEncodedTorrentInfo
import me.him188.ani.app.domain.torrent.parcel.PTorrentDownloaderStats
import me.him188.ani.app.domain.torrent.parcel.RemoteContinuationException
import me.him188.ani.app.domain.torrent.parcel.toParceled
import me.him188.ani.app.torrent.api.TorrentDownloader
import me.him188.ani.app.torrent.api.TorrentLibInfo
import me.him188.ani.app.torrent.api.TorrentSession
import me.him188.ani.app.torrent.api.files.EncodedTorrentInfo
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.inSystem
import kotlin.coroutines.CoroutineContext

@RequiresApi(Build.VERSION_CODES.O_MR1)
class RemoteTorrentDownloader(
    private val fetchRemoteScope: CoroutineScope,
    connectivityAware: ConnectivityAware,
    getRemote: suspend () -> IRemoteTorrentDownloader
) : TorrentDownloader,
    RemoteCall<IRemoteTorrentDownloader> by RetryRemoteCall(fetchRemoteScope, getRemote),
    ConnectivityAware by connectivityAware {
    override val totalStats: Flow<TorrentDownloader.Stats> = callbackFlow {
        var disposable: IDisposableHandle? = null
        val callback = object : ITorrentDownloaderStatsCallback.Stub() {
            override fun onEmit(stat: PTorrentDownloaderStats?) {
                if (stat != null) trySend(stat.toStats())
            }
        }

        // todo: not thread-safe
        disposable = call { getTotalStatus(callback) }
        val transform = registerStateTransform(false, true) {
            disposable?.callOnceOrNull { dispose() }
            disposable = call { getTotalStatus(callback) }
        }

        awaitClose {
            disposable?.callOnceOrNull { dispose() }
            unregister(transform)
        }
    }

    override val vendor: TorrentLibInfo get() = call { vendor.toTorrentLibInfo() }

    override suspend fun fetchTorrent(uri: String, timeoutSeconds: Int): EncodedTorrentInfo =
        callSuspendCancellable { resolve, reject ->
            fetchTorrent(
                uri, timeoutSeconds,
                object : ContTorrentDownloaderFetchTorrent.Stub() {
                    override fun resume(value: PEncodedTorrentInfo?) = resolve(value?.toEncodedTorrentInfo())
                    override fun resumeWithException(exception: RemoteContinuationException?) = reject(exception)
                },
            )
        }

    override suspend fun startDownload(
        data: EncodedTorrentInfo,
        parentCoroutineContext: CoroutineContext,
        overrideSaveDir: SystemPath?
    ): TorrentSession {
        return RemoteTorrentSession(fetchRemoteScope, this@RemoteTorrentDownloader) {
            callSuspendCancellable { resolve, reject ->
                startDownload(
                    data.toParceled(),
                    overrideSaveDir?.absolutePath,
                    object : ContTorrentDownloaderStartDownload.Stub() {
                        override fun resume(value: IRemoteTorrentSession?) = resolve(value)
                        override fun resumeWithException(exception: RemoteContinuationException?) =
                            reject(exception)
                    },
                )
            }
        }
    }

    override fun getSaveDirForTorrent(data: EncodedTorrentInfo): SystemPath {
        val remotePath = call { getSaveDirForTorrent(data.toParceled()) }
        return Path(remotePath).inSystem
    }

    override fun listSaves(): List<SystemPath> {
        return call { listSaves() }.map { Path(it).inSystem }
    }

    override fun close() {
        return call { close() }
    }
}