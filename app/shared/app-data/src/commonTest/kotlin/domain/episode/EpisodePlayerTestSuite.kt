/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import me.him188.ani.app.data.models.subject.TestSubjectCollections
import me.him188.ani.app.domain.media.selector.MediaSelectorTestBuilder
import org.koin.core.Koin
import org.koin.dsl.module
import org.openani.mediamp.DummyMediampPlayer

class EpisodePlayerTestSuite(
    val backgroundScope: CoroutineScope,
) {
    val player = DummyMediampPlayer()
    private val mediaSelectorTestBuilder = MediaSelectorTestBuilder()

    val koin = Koin()

    init {
        koin.loadModules(
            listOf(
                module {
                    single<GetSubjectEpisodeInfoBundleFlowUseCase> {
                        GetSubjectEpisodeInfoBundleFlowUseCase { idsFlow ->
                            idsFlow.map {
                                SubjectEpisodeInfoBundle(
                                    it.subjectId,
                                    it.episodeId,
                                    TestSubjectCollections[0].run {
                                        copy(subjectInfo = subjectInfo.copy(subjectId = subjectId))
                                    },
                                    TestSubjectCollections[0].episodes[0].run {
                                        copy(episodeInfo = episodeInfo.copy(episodeId = episodeId))
                                    },
                                )
                            }
                        }
                    }
                    single<CreateMediaFetchSelectBundleFlowUseCase> {
                        CreateMediaFetchSelectBundleFlowUseCase { _ ->
                            val mediaFetchSession =
                                mediaSelectorTestBuilder.createMediaFetchSession(mediaSelectorTestBuilder.createMediaFetcher())
                            flowOf(
                                MediaFetchSelectBundle(
                                    mediaFetchSession,
                                    mediaSelectorTestBuilder.createMediaSelector(mediaFetchSession),
                                ),
                            )
                        }
                    }
                },
            ),
        )
    }

    inline fun <reified T : Any> registerComponent(crossinline value: () -> T) {
        koin.loadModules(
            listOf(
                module {
                    single<T> { value() }
                },
            ),
        )
    }
}

fun TestScope.createExceptionCapturingSupervisorScope(): Pair<CoroutineScope, CompletableDeferred<Throwable>> {
    val backgroundException = CompletableDeferred<Throwable>()
    val scope = CoroutineScope(
        SupervisorJob(backgroundScope.coroutineContext[Job]) + CoroutineExceptionHandler { _, throwable ->
            backgroundException.complete(throwable)
        },
    )
    return Pair(scope, backgroundException)
}
