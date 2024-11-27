/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.state

import androidx.compose.runtime.mutableStateOf
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.preference.EpisodeListProgressTheme
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.network.BangumiCommentService
import me.him188.ani.app.data.network.BangumiRelatedPeopleService
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.episode.BangumiCommentRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.episode.EpisodeProgressRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.subject.SubjectRelationsRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.launchAuthorize
import me.him188.ani.app.ui.comment.CommentMapperContext.parseToUIComment
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.foundation.produceState
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.progress.EpisodeListState
import me.him188.ani.app.ui.subject.collection.progress.EpisodeListStateFactory
import me.him188.ani.app.ui.subject.collection.progress.SubjectProgressState
import me.him188.ani.app.ui.subject.collection.progress.SubjectProgressStateFactory
import me.him188.ani.app.ui.subject.details.updateRating
import me.him188.ani.app.ui.subject.rating.EditableRatingState
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.coroutines.update
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

interface SubjectDetailsStateFactory {
    fun create(subjectInfoFlow: Flow<SubjectInfo>): Flow<SubjectDetailsState>
    fun create(subjectInfo: SubjectInfo): Flow<SubjectDetailsState>

    /**
     * @param preloadSubjectInfo 通常是仅仅包含少量信息的预加载的 subject 信息.
     *        例如从探索页导航到详情页时, subject 名字和封面图时已知的, 可以作为预加载信息以第一时间显示一些东西.
     */
    fun create(
        subjectId: Int,
        preloadSubjectInfo: SubjectInfo? = null
    ): Flow<SubjectDetailsState>
    fun create(subjectCollectionInfo: SubjectCollectionInfo, scope: CoroutineScope): SubjectDetailsState
}

class DefaultSubjectDetailsStateFactory(
    parentCoroutineContext: CoroutineContext
) : SubjectDetailsStateFactory, KoinComponent {
    private val childScope = parentCoroutineContext.childScope()
    
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val episodeProgressRepository: EpisodeProgressRepository by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val bangumiRelatedPeopleService: BangumiRelatedPeopleService by inject()
    private val subjectRelationsRepository: SubjectRelationsRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val bangumiCommentService: BangumiCommentService by inject()
    private val bangumiCommentRepository: BangumiCommentRepository by inject()
    private val animeScheduleRepository: AnimeScheduleRepository by inject()

    val sessionManager: SessionManager by inject()

    /**
     * 已经把条目详情信息缓存到本地数据库的条目 ID.
     *
     * 对于已经缓存过的条目, 创建 SubjectDetailsState flow 时不会 emit [预加载][SubjectDetailsState.preload] 条目.
     */
    private val cachedSubjectDetails: MutableStateFlow<List<Int>> = MutableStateFlow(emptyList())

    init {
        childScope.launch {
            subjectCollectionRepository.cachedValidSubjectIds().collect { newList ->
                cachedSubjectDetails.update { newList }
            }
        }
    }

    override fun create(
        subjectInfoFlow: Flow<SubjectInfo>
    ): Flow<SubjectDetailsState> = flow {
        coroutineScope {
            val authState = createAuthState()
            val subjectProgressStateFactory = createSubjectProgressStateFactory()

            subjectDetailsStateFlow(subjectInfoFlow, null, subjectProgressStateFactory, authState)
                .collect { emit(it) }
        }
    }

    override fun create(subjectInfo: SubjectInfo): Flow<SubjectDetailsState> {
        return create(flowOf(subjectInfo))
    }

    override fun create(subjectId: Int, preloadSubjectInfo: SubjectInfo?): Flow<SubjectDetailsState> = flow {
        coroutineScope {
            val authState = createAuthState()
            val subjectProgressStateFactory = createSubjectProgressStateFactory()

            subjectDetailsStateFlow(
                null,
                subjectCollectionRepository.subjectCollectionFlow(subjectId),
                subjectProgressStateFactory,
                authState,
                preloadSubjectInfo,
            ).collect { e -> emit(e) }
        }
    }

    override fun create(subjectCollectionInfo: SubjectCollectionInfo, scope: CoroutineScope): SubjectDetailsState {
        val authState = scope.createAuthState()
        val subjectProgressStateFactory = createSubjectProgressStateFactory()

        val subjectCollectionInfoFlow = MutableStateFlow(subjectCollectionInfo)
        return scope.createImpl(
            subjectCollectionInfoFlow.value.subjectInfo,
            subjectCollectionInfoFlow,
            MutableStateFlow(subjectCollectionInfo.collectionType),
            subjectProgressStateFactory,
            authState,
        )
    }

    private fun CoroutineScope.createAuthState() = AuthState(
        state = sessionManager.state.produceState(null, this),
        launchAuthorize = { navigator ->
            launchAuthorize(navigator)
        },
        retry = { sessionManager.retry() },
        this,
    )

    private fun createSubjectProgressStateFactory() = SubjectProgressStateFactory(
        episodeProgressRepository,
    )

    /**
     * 根据 [subjectInfoFlow] 或 [subjectCollectionInfoFlow] 创建 [SubjectDetailsState] flow.
     *
     * [subjectInfoFlow] 和 [subjectCollectionInfoFlow] 只需要传递其中一个即可创建.
     * 如果同时传递, 则优先使用 [subjectCollectionInfoFlow], 因为 [SubjectCollectionInfo] 包含了 [SubjectInfo].
     *
     * @param preloadSubjectInfo 预加载的条目信息, 将最先展示.
     */
    private fun CoroutineScope.subjectDetailsStateFlow(
        subjectInfoFlow: Flow<SubjectInfo>?,
        subjectCollectionInfoFlow: Flow<SubjectCollectionInfo>?,
        subjectProgressStateFactory: SubjectProgressStateFactory,
        authState: AuthState,
        preloadSubjectInfo: SubjectInfo? = null,
    ): Flow<SubjectDetailsState> = channelFlow {
        require(subjectInfoFlow != null || subjectCollectionInfoFlow != null) {
            "Both subjectCollectionInfoFlow and subjectInfoFlow are null."
        }
        // emit preload subject info if present and not cached.
        if (preloadSubjectInfo != null && !cachedSubjectDetails.value.contains(preloadSubjectInfo.subjectId)) {
            send(createPreload(preloadSubjectInfo, authState))
        }
        coroutineScope {
            if (subjectCollectionInfoFlow != null) {
                val stated = subjectCollectionInfoFlow.stateIn(this)
                stated.collectLatest { subjectCollection ->
                    // only emit actual state when subjectCollectionInfoFlow has value
                    send(
                        createImpl(
                            subjectCollection.subjectInfo,
                            stated,
                            stated.map { it.collectionType }.stateIn(this),
                            subjectProgressStateFactory,
                            authState,
                        ),
                    )
                }
            } else if (subjectInfoFlow != null) {
                subjectInfoFlow.collectLatest { subjectInfo ->
                    val subjectCollectionFlow = subjectCollectionRepository
                        .subjectCollectionFlow(subjectInfo.subjectId)
                        .shareIn(this, started = SharingStarted.Eagerly, replay = 1)

                    send(
                        createImpl(
                            subjectInfo,
                            subjectCollectionFlow,
                            subjectCollectionFlow.map { it.collectionType }.stateIn(this),
                            subjectProgressStateFactory,
                            authState,
                        ),
                    )
                }
            } else {
                error("unreachable")
            }
        }
    }
    

    private fun CoroutineScope.createImpl(
        subjectInfo: SubjectInfo,
        subjectCollectionFlow: SharedFlow<SubjectCollectionInfo>,
        selfCollectionTypeStateFlow: StateFlow<UnifiedCollectionType>,
        subjectProgressStateFactory: SubjectProgressStateFactory,
        authState: AuthState
    ): SubjectDetailsState {
        val totalStaffCountState = mutableStateOf<Int?>(null)
        val totalCharactersCountState = mutableStateOf<Int?>(null)

        val subjectId = subjectInfo.subjectId
        val subjectScope = this
        val editableSubjectCollectionTypeState = EditableSubjectCollectionTypeState(
            selfCollectionType = subjectCollectionFlow
                .map { it.collectionType }
                .produceState(UnifiedCollectionType.NOT_COLLECTED, this),
            hasAnyUnwatched = hasAnyUnwatched@{
                val collections = episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(subjectId)
                    .flowOn(Dispatchers.Default).firstOrNull() ?: return@hasAnyUnwatched true

                collections.any { !it.collectionType.isDoneOrDropped() }
            },
            onSetSelfCollectionType = {
                subjectCollectionRepository.setSubjectCollectionTypeOrDelete(
                    subjectId,
                    it,
                )
            },
            onSetAllEpisodesWatched = {
                episodeCollectionRepository.setAllEpisodesWatched(subjectId)
            },
            this,
        )

        val editableRatingState = EditableRatingState(
            ratingInfo = stateOf(subjectInfo.ratingInfo),
            selfRatingInfo = subjectCollectionFlow.map { it.selfRatingInfo }
                .produceState(SelfRatingInfo.Empty, this),
            enableEdit = subjectCollectionFlow
                .map { it.collectionType != UnifiedCollectionType.NOT_COLLECTED }
                .produceState(false, this),
            isCollected = {
                val collection =
                    subjectCollectionFlow.replayCache.firstOrNull() ?: return@EditableRatingState false
                collection.collectionType != UnifiedCollectionType.NOT_COLLECTED
            },
            onRate = { request ->
                subjectCollectionRepository.updateRating(
                    subjectId,
                    request,
                )
            },
            this,
        )

        val episodeListState = EpisodeListStateFactory(
            settingsRepository,
            episodeCollectionRepository,
            episodeProgressRepository,
            this,
        ).run {
            EpisodeListState(
                stateOf(subjectId),
                theme.produceState(EpisodeListProgressTheme.Default, subjectScope),
                episodes(subjectId).produceState(emptyList(), subjectScope),
                ::onSetEpisodeWatched,
                backgroundScope,
            )
        }


        val subjectProgressInfoState =
            subjectCollectionFlow.map { info ->
                SubjectProgressInfo.compute(
                    info.subjectInfo, info.episodes, PackedDate.now(),
                    recurrence = info.recurrence,
                )
            }.produceState(null, this)

        val subjectProgressState = subjectProgressStateFactory.run {
            SubjectProgressState(
                subjectProgressInfoState,
                episodeProgressInfoList(subjectId).produceState(emptyList(), subjectScope),
            )
        }

        val comments = bangumiCommentRepository.subjectCommentsPager(subjectId)
            .cachedIn(this)

        val subjectCommentState = CommentState(
            list = comments.map { page ->
                page.map { it.parseToUIComment() }
            },
            countState = stateOf(null),
            onSubmitCommentReaction = { _, _ -> },
            backgroundScope = this,
        )

//        val relatedPersonsFlow = bangumiRelatedPeopleService.relatedPersonsFlow(subjectId)
//            .onEach {
//                withContext(Dispatchers.Main) { totalStaffCountState.value = it.size }
//            }
//            .stateIn(this, SharingStarted.Eagerly, null)
//
        val loadingState = LoadStates(
            refresh = LoadState.Loading,
            prepend = LoadState.NotLoading(false),
            append = LoadState.NotLoading(false),
        )

//        val relatedCharactersFlow = bangumiRelatedPeopleService.relatedCharactersFlow(subjectId)
//            .onEach {
//                withContext(Dispatchers.Main) { totalCharactersCountState.value = it.size }
//            }
//            .stateIn(this, SharingStarted.Eagerly, null)

        val relatedPersonsFlow = subjectRelationsRepository.subjectRelatedPersonsFlow(subjectId)
            .onEach {
                withContext(Dispatchers.Main) { totalStaffCountState.value = it.size }
            }
            .stateIn(this, SharingStarted.Eagerly, null)

        val relatedCharactersFlow = subjectRelationsRepository.subjectRelatedCharactersFlow(subjectId)
            .onEach {
                withContext(Dispatchers.Main) { totalCharactersCountState.value = it.size }
            }
            .stateIn(this, SharingStarted.Eagerly, null)

        val state = SubjectDetailsState(
            info = subjectInfo,
            selfCollectionTypeState = selfCollectionTypeStateFlow
                .produceState(scope = this),
            airingLabelState = AiringLabelState(
                subjectCollectionFlow.map { it.airingInfo }.produceState(null, scope = this),
                subjectProgressInfoState,
            ),
            staffPager = relatedPersonsFlow
                .map {
                    PagingData.from(
                        it ?: emptyList(),
                        sourceLoadStates = loadingState,
                    )
                }
                .cachedIn(this),
            exposedStaffPager = relatedPersonsFlow
                .filterNotNull()
                .map { list ->
                    list.take(6)
                }
                .map { PagingData.from(it) }
                .cachedIn(this),
            totalStaffCountState = totalStaffCountState,
            charactersPager = relatedCharactersFlow.map {
                PagingData.from(
                    it ?: emptyList(),
                    sourceLoadStates = loadingState,
                )
            }.cachedIn(this),
            totalCharactersCountState = totalCharactersCountState,
            relatedSubjectsPager = bangumiRelatedPeopleService.relatedSubjectsFlow(subjectId)
                .map {
                    PagingData.from(it)
                }
                .cachedIn(this),
            exposedCharactersPager = relatedCharactersFlow
                .filterNotNull()
                .map { it.computeExposed() }
                .map { PagingData.from(it) }
                .cachedIn(this),
            episodeListState = episodeListState,
            authState = authState,
            editableSubjectCollectionTypeState = editableSubjectCollectionTypeState,
            editableRatingState = editableRatingState,
            subjectProgressState = subjectProgressState,
            subjectCommentState = subjectCommentState,
            preload = false,
        )
        return state
    }

    private fun CoroutineScope.createPreload(
        subjectInfo: SubjectInfo,
        authState: AuthState
    ): SubjectDetailsState {
        return SubjectDetailsState(
            info = subjectInfo,
            selfCollectionTypeState = stateOf(UnifiedCollectionType.NOT_COLLECTED),
            airingLabelState = AiringLabelState(
                airingInfoState = stateOf(null),
                progressInfoState = stateOf(null),
            ),
            staffPager = emptyFlow(),
            exposedStaffPager = emptyFlow(),
            totalStaffCountState = stateOf(null),
            charactersPager = emptyFlow(),
            exposedCharactersPager = emptyFlow(),
            totalCharactersCountState = stateOf(null),
            relatedSubjectsPager = emptyFlow(),
            episodeListState = EpisodeListState(
                subjectId = stateOf(subjectInfo.subjectId),
                theme = stateOf(EpisodeListProgressTheme.Default),
                episodeProgressInfoList = stateOf(emptyList()),
                onSetEpisodeWatched = { _, _, _ -> },
                backgroundScope = this,
            ),
            authState = authState,
            editableSubjectCollectionTypeState = EditableSubjectCollectionTypeState(
                selfCollectionType = stateOf(UnifiedCollectionType.NOT_COLLECTED),
                hasAnyUnwatched = { true },
                onSetSelfCollectionType = { },
                onSetAllEpisodesWatched = { },
                backgroundScope = this,
            ),
            editableRatingState = EditableRatingState(
                ratingInfo = stateOf(RatingInfo.Empty),
                selfRatingInfo = stateOf(SelfRatingInfo.Empty),
                enableEdit = stateOf(false),
                isCollected = { false },
                onRate = { },
                backgroundScope = this,
            ),
            subjectProgressState = SubjectProgressState(
                info = stateOf(null),
                episodeProgressInfos = stateOf(emptyList()),
            ),
            subjectCommentState = CommentState(
                list = emptyFlow(),
                countState = stateOf(null),
                onSubmitCommentReaction = { _, _ -> },
                backgroundScope = this,
            ),
            preload = true,
        )
    }
}

@TestOnly
class TestSubjectDetailsStateFactory : SubjectDetailsStateFactory {
    @TestOnly
    override fun create(subjectInfoFlow: Flow<SubjectInfo>): Flow<SubjectDetailsState> {
        return emptyFlow()
//        return flowOf(
//            SubjectDetailsState(
//                info = SubjectInfo.Empty,
//                selfCollectionTypeState = stateOf(UnifiedCollectionType.WISH),
//                airingLabelState = createTestAiringLabelState(),
//                staffPager = flowOf(PagingData.empty()),
//                totalStaffCountState = stateOf(null),
//                charactersPager = flowOf(PagingData.empty()),
//                totalCharactersCountState = stateOf(null),
//                relatedSubjectsPager = flowOf(PagingData.empty()),
//                episodeListState = EpisodeListState(
//                    subjectId = stateOf(0),
//                    theme = stateOf(EpisodeListProgressTheme.Default),
//                    episodeProgressInfoList = stateOf(emptyList()),
//                    onSetEpisodeWatched = {},
//                    backgroundScope = CoroutineScope(Dispatchers.Default),
//                ),
//                authState = AuthState(
//                    state = produceState(null, CoroutineScope(Dispatchers.Default)),
//                    launchAuthorize = {},
//                    retry = {},
//                    CoroutineScope(Dispatchers.Default),
//                ),
//                editableSubjectCollectionTypeState = EditableSubjectCollectionTypeState(
//                    selfCollectionType = produceState(
//                        UnifiedCollectionType.NOT_COLLECTED,
//                        CoroutineScope(Dispatchers.Default),
//                    ),
//                    hasAnyUnwatched = { true },
//                    onSetSelfCollectionType = {},
//                    onSetAllEpisodesWatched = {},
//                    CoroutineScope(Dispatchers.Default),
//                ),
//                editableRatingState = EditableRatingState(
//                    ratingInfo = stateOf(null),
//                    selfRatingInfo = produceState(SelfRatingInfo.Empty, CoroutineScope(Dispatchers.Default)),
//                    enableEdit = produceState(false, CoroutineScope(Dispatchers.Default)),
//                    isCollected = { false },
//                    onRate = {},
//                    CoroutineScope(Dispatchers.Default),
//                ),
//                subjectProgressState = SubjectProgressState(
//                    subjectProgressInfoState = stateOf(null),
//                    episodeProgressInfoList = produceState(emptyList(), CoroutineScope(Dispatchers.Default)),
//                ),
//                subjectCommentState = CommentState(
//                    sourceVersion = produceState(null, CoroutineScope(Dispatchers.Default)),
//                    list = produceState(emptyList(), CoroutineScope(Dispatchers.Default)),
//                    hasMore = produceState(true, CoroutineScope(Dispatchers.Default)),
//                    onReload = {},
//                    onLoadMore = {},
//                    onSubmitCommentReaction = { _, _ -> },
//                    CoroutineScope(Dispatchers.Default),
//                ),
//            ),
//        )
    }

    override fun create(subjectInfo: SubjectInfo): Flow<SubjectDetailsState> {
        return emptyFlow()
    }

    override fun create(subjectId: Int, preloadSubjectInfo: SubjectInfo?): Flow<SubjectDetailsState> {
        return emptyFlow()
    }

    override fun create(subjectCollectionInfo: SubjectCollectionInfo, scope: CoroutineScope): SubjectDetailsState {
        throw UnsupportedOperationException()
    }

}

private fun List<RelatedCharacterInfo>.computeExposed(): List<RelatedCharacterInfo> {
    val list = this
    // 显示前六个主角, 否则显示前六个
    return if (list.any { it.isMainCharacter() }) {
        val res = list.asSequence().filter { it.isMainCharacter() }.take(6).toList()
        if (res.size >= 4 || list.size < 4) {
            res // 有至少四个主角
        } else {
            list.take(4) // 主角不足四个, 就显示前四个包含非主角的
        }
    } else {
        list.take(6) // 没有主角
    }
}