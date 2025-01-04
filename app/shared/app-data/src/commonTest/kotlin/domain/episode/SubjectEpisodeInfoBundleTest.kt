/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.domain.foundation.LoadError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class SubjectEpisodeInfoBundleTest {
    private val subjectId = 1

    private fun EpisodePlayerTestSuite.createState(episodeIdFlow: Flow<Int>): SubjectEpisodeInfoBundleLoader {
        return SubjectEpisodeInfoBundleLoader(subjectId, episodeIdFlow, koin)
    }

    @Test
    fun `infoLoadErrorState initially null`() = runTest {
        val episodeIdFlow = MutableStateFlow(2)
        val suite = EpisodePlayerTestSuite(backgroundScope)
        val state = suite.createState(episodeIdFlow)
        assertEquals(null, state.infoLoadErrorState.value)
    }

    @Test
    fun `infoBundleFlow emits null first`() = runTest {
        val episodeIdFlow = MutableStateFlow(2)
        val suite = EpisodePlayerTestSuite(backgroundScope)
        val state = suite.createState(episodeIdFlow)
        assertEquals(null, state.infoBundleFlow.first()) // refreshes UI
    }

    @Test
    fun `infoBundleFlow load success`() = runTest {
        val episodeIdFlow = MutableStateFlow(2)
        val suite = EpisodePlayerTestSuite(backgroundScope)
        val state = suite.createState(episodeIdFlow)
        assertNotEquals(null, state.infoBundleFlow.drop(1).first())
        assertEquals(null, state.infoLoadErrorState.value)
    }

    @Test
    fun `infoBundleFlow completes when episodeId completes`() = runTest {
        val episodeIdFlow = flowOf(2)
        val suite = EpisodePlayerTestSuite(backgroundScope)
        val state = suite.createState(episodeIdFlow)
        state.infoBundleFlow.drop(1).toList().run {
            assertEquals(1, size)
            assertNotEquals(null, first())
        }
    }

    @Test
    fun `infoBundleFlow load failure is captured in the background amd exposed via infoLoadErrorState`() = runTest {
        val episodeIdFlow = MutableStateFlow(2)
        val (scope, backgroundException) = createExceptionCapturingSupervisorScope()
        val suite = EpisodePlayerTestSuite(scope)
        suite.registerComponent<GetSubjectEpisodeInfoBundleFlowUseCase> {
            GetSubjectEpisodeInfoBundleFlowUseCase { idsFlow ->
                idsFlow.map {
                    throw RepositoryNetworkException()
                }
            }
        }
        val state = suite.createState(episodeIdFlow)
        val job = state.infoBundleFlow.drop(1).launchIn(scope) // will hang forever
        assertEquals(LoadError.NetworkError, state.infoLoadErrorState.onEach { println(it) }.filterNotNull().first())
        job.cancel()
        scope.cancel()
        assertIs<RepositoryNetworkException>(backgroundException.await(), "should be a network error")
    }
}