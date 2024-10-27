/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.datasources.bangumi.client.BangumiSearchApi
import me.him188.ani.datasources.bangumi.models.BangumiSubjectType
import me.him188.ani.datasources.bangumi.models.subjects.BangumiSubjectImageSize

class SubjectSearchRepository(
    private val searchApi: Flow<BangumiSearchApi>,
    private val subjectRepository: SubjectCollectionRepository,
) {
    fun searchSubjects(
        searchQuery: SubjectSearchQuery,
        useNewApi: Boolean = false,
        pagingConfig: PagingConfig = Repository.defaultPagingConfig
    ): Flow<PagingData<SubjectInfo>> = Pager(
        config = pagingConfig,
        initialKey = 0,
//        remoteMediator = SubjectSearchRemoteMediator(useNewApi, searchQuery, pagingConfig),
        pagingSourceFactory = {
            object : PagingSource<Int, SubjectInfo>() {
                override fun getRefreshKey(state: PagingState<Int, SubjectInfo>): Int? =
                    state.anchorPosition

                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SubjectInfo> {
                    val offset = params.key ?: return LoadResult.Error(IllegalArgumentException("Key is null"))
                    return try {
                        val api = searchApi.first()
                        val res = if (useNewApi) {
                            api.searchSubjectByKeywords(
                                searchQuery.keyword,
                                offset = offset,
                                limit = params.loadSize,
                            ).page.map {
                                it.id
                            }
                        } else {
                            api.searchSubjectsByKeywordsWithOldApi(
                                searchQuery.keyword,
                                type = searchQuery.type.toBangumiSubjectType(),
                                responseGroup = BangumiSubjectImageSize.SMALL,
                                start = offset,
                                maxResults = params.loadSize,
                            ).page.map {
                                it.id
                            }
                        }

                        val subjectInfos = subjectRepository.batchGetSubjectDetails(res)

                        return LoadResult.Page(
                            subjectInfos.map {
                                it.subjectInfo
                            },
                            prevKey = offset,
                            nextKey = if (subjectInfos.isEmpty()) null else offset + params.loadSize,
                        )
                    } catch (e: RepositoryException) {
                        LoadResult.Error(e)
                    } catch (e: ResponseException) {
                        LoadResult.Error(e)
                    } catch (e: Exception) {
                        LoadResult.Error(e)
                    }
                }
            }
        },
    ).flow

//    private inner class SubjectSearchRemoteMediator(
//        val useNewApi: Boolean,
//        val searchQuery: SubjectSearchQuery,
//        val pagingConfig: PagingConfig,
//    ) : RemoteMediator<Int, SubjectInfoNew>() {
//        override suspend fun load(
//            loadType: LoadType,
//            state: PagingState<Int, SubjectInfoNew>
//        ): MediatorResult {
//            val currentPage = when (loadType) {
//                LoadType.REFRESH -> 0
//                LoadType.PREPEND -> state.anchorPosition?.minus(1) ?: 0
//                LoadType.APPEND -> state.anchorPosition?.plus(1) ?: 0
//            }
//            val api = searchApi.first()
//            val res = if (useNewApi) {
//                api.searchSubjectByKeywords(
//                    searchQuery.keyword,
//                    offset = pagingConfig.pageSize * (currentPage - 0),
//                    limit = pagingConfig.pageSize,
//                ).map {
//                    it.toSubjectInfo()
//                }
//            } else {
//                api.searchSubjectsByKeywordsWithOldApi(
//                    searchQuery.keyword,
//                    type = searchQuery.type.toBangumiSubjectType(),
//                    responseGroup = BangumiSubjectImageSize.SMALL,
//                    start = pagingConfig.pageSize * (currentPage - 0),
//                    maxResults = pagingConfig.pageSize,
//                ).map {
//                    it.toSubjectInfo()
//                }
//            }
//
//            /*
//              (findInfoboxValue("播放结束") ?: findInfoboxValue("放送结束"))
//                ?.let {
//                    PackedDate.parseFromDate(
//                        it.replace('年', '-')
//                            .replace('月', '-')
//                            .removeSuffix("日"),
//                    )
//                }
//                ?: PackedDate.Invalid
//             */
//
//            subjectInfoDao.upsert(
//                res.page.map {
//                    it.toEntity()
//                },
//            )
//            return MediatorResult.Success(
//                endOfPaginationReached = res.isEmpty(),
//            )
//        }
//    }
}

private fun SubjectType.toBangumiSubjectType(): BangumiSubjectType = when (this) {
    SubjectType.ANIME -> BangumiSubjectType.Anime
}

