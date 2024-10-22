/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video

import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PieceState
import me.him188.ani.app.torrent.api.pieces.count
import me.him188.ani.app.torrent.api.pieces.first
import me.him188.ani.app.torrent.api.pieces.last
import me.him188.ani.app.ui.framework.runComposeStateTest
import me.him188.ani.app.ui.framework.takeSnapshot
import me.him188.ani.app.videoplayer.ui.state.ChunkState
import kotlin.test.Test
import kotlin.test.assertEquals

class TorrentMediaCacheProgressStateTest {
    private val pieces = PieceList.create(16, 0) {
        1_000
    }.apply {
        assertEquals(16, count)
    }
    private val isFinished = false

    private fun runTest(block: suspend PieceList.() -> Unit) {
        kotlinx.coroutines.test.runTest {
            block(pieces)
        }
    }

    @Test
    fun `initial state`() {
        val cacheProgress = TorrentMediaCacheProgressState(
            pieces,
            isFinished = { isFinished },
        )
        assertEquals(16, cacheProgress.chunks.size)
        assertEquals(1 / 16f, cacheProgress.chunks[0].weight)
        assertEquals(ChunkState.NONE, cacheProgress.chunks[0].state)
        assertEquals(ChunkState.NONE, cacheProgress.chunks.last().state)
    }

    @Test
    fun `update no change`() = runTest {
        val cacheProgress = TorrentMediaCacheProgressState(
            pieces,
            isFinished = { isFinished },
        )
        cacheProgress.update()
        assertEquals(16, cacheProgress.chunks.size)
        assertEquals(1 / 16f, cacheProgress.chunks[0].weight)
        assertEquals(ChunkState.NONE, cacheProgress.chunks[0].state)
        assertEquals(ChunkState.NONE, cacheProgress.chunks.last().state)
    }

    @Test
    fun `update first finish`() = runTest {
        val cacheProgress = TorrentMediaCacheProgressState(
            pieces,
            isFinished = { isFinished },
        )
        pieces.first().state = PieceState.FINISHED
        cacheProgress.update()
        assertEquals(ChunkState.DONE, cacheProgress.chunks[0].state)
        assertEquals(ChunkState.NONE, cacheProgress.chunks[1].state)
        assertEquals(ChunkState.NONE, cacheProgress.chunks.last().state)
    }

    @Test
    fun `update last finish`() = runTest {
        val cacheProgress = TorrentMediaCacheProgressState(
            pieces,
            isFinished = { isFinished },
        )
        pieces.last().state = PieceState.FINISHED
        cacheProgress.update()
        assertEquals(ChunkState.NONE, cacheProgress.chunks[0].state)
        assertEquals(ChunkState.NONE, cacheProgress.chunks[1].state)
        assertEquals(ChunkState.DONE, cacheProgress.chunks.last().state)
    }

    @Test
    fun `update middle finish`() = runTest {
        val cacheProgress = TorrentMediaCacheProgressState(
            pieces,
            isFinished = { isFinished },
        )
        pieces.getByPieceIndex(5).state = PieceState.FINISHED
        cacheProgress.update()
        assertEquals(ChunkState.DONE, cacheProgress.chunks[5].state)
        assertEquals(ChunkState.NONE, cacheProgress.chunks[0].state)
        assertEquals(ChunkState.NONE, cacheProgress.chunks[1].state)
        assertEquals(ChunkState.NONE, cacheProgress.chunks.last().state)
    }

    @Test
    fun `update does not increment version if there is no change`() = runComposeStateTest {
        val cacheProgress = TorrentMediaCacheProgressState(
            pieces,
            isFinished = { isFinished },
        )
        assertEquals(0, cacheProgress.version)
        cacheProgress.update()
        takeSnapshot()
        assertEquals(0, cacheProgress.version)
    }

    @Test
    fun `update increment version if first change`() = runComposeStateTest {
        val cacheProgress = TorrentMediaCacheProgressState(
            pieces,
            isFinished = { isFinished },
        )
        assertEquals(0, cacheProgress.version)
        with(pieces) {
            pieces.first().state = PieceState.FINISHED
        }
        cacheProgress.update()
        takeSnapshot()
        assertEquals(1, cacheProgress.version)
    }

    @Test
    fun `update increment version if last change`() = runComposeStateTest {
        val cacheProgress = TorrentMediaCacheProgressState(
            pieces,
            isFinished = { isFinished },
        )
        assertEquals(0, cacheProgress.version)
        with(pieces) {
            pieces.last().state = PieceState.FINISHED
        }
        cacheProgress.update()
        takeSnapshot()
        assertEquals(1, cacheProgress.version)
    }

    @Test
    fun `update increment version only once if first change`() = runComposeStateTest {
        val cacheProgress = TorrentMediaCacheProgressState(
            pieces,
            isFinished = { isFinished },
        )
        assertEquals(0, cacheProgress.version)
        with(pieces) {
            pieces.first().state = PieceState.FINISHED
        }
        cacheProgress.update()
        takeSnapshot()
        assertEquals(1, cacheProgress.version)
        cacheProgress.update()
        takeSnapshot()
        assertEquals(1, cacheProgress.version)
    }

}