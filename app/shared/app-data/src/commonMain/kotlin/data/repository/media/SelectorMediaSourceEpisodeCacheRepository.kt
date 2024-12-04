/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.media

import kotlinx.coroutines.withContext
import me.him188.ani.app.data.persistent.database.dao.WebSearchEpisodeInfoDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchEpisodeInfoEntity
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoDao
import me.him188.ani.app.data.persistent.database.dao.WebSearchSubjectInfoEntity
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.domain.mediasource.web.WebSearchEpisodeInfo
import me.him188.ani.app.domain.mediasource.web.WebSearchSubjectInfo


class SelectorMediaSourceEpisodeCacheRepository(
    private val webSubjectInfoDao: WebSearchSubjectInfoDao,
    private val webEpisodeInfoDao: WebSearchEpisodeInfoDao,
) : Repository() {
    suspend fun addCache(
        mediaSourceId: String,
        subjectName: String,
        subjectInfo: WebSearchSubjectInfo,
        episodeInfos: List<WebSearchEpisodeInfo>
    ) {
        val subjectInfoEntity = subjectInfo.toEntity(mediaSourceId, subjectName)
        val subjectId = webSubjectInfoDao.insert(subjectInfoEntity)
        webEpisodeInfoDao.upsert(episodeInfos.map { it.toEntity(subjectId) })
    }

    suspend fun clearSubjectAndEpisodeCache() = withContext(defaultDispatcher) {
        webSubjectInfoDao.clearCache()
        webSubjectInfoDao.resetAutoIncrement()
        webEpisodeInfoDao.resetAutoIncrement()
    }

    suspend fun getCache(mediaSourceId: String, subjectName: String): List<WebSearchCache> {
        val webSearchSubjectInfoAndEpisodes =
            webSubjectInfoDao.filterByMediaSourceIdAndSubjectName(mediaSourceId, subjectName)
        return webSearchSubjectInfoAndEpisodes.map { info ->
            WebSearchCache(
                subjectInfo = info.subjectInfo.toWebSearchSubjectInfo(),
                episodeInfos = info.episodeInfos.map { it.toWebSearchEpisodeInfo() },
            )
        }
    }
}

data class WebSearchCache(
    val subjectInfo: WebSearchSubjectInfo,
    val episodeInfos: List<WebSearchEpisodeInfo>
)


fun WebSearchSubjectInfo.toEntity(mediaSourceId: String, subjectName: String): WebSearchSubjectInfoEntity {
    return WebSearchSubjectInfoEntity(
        mediaSourceId = mediaSourceId,
        subjectName = subjectName,
        internalId = internalId,
        name = name,
        fullUrl = fullUrl,
        partialUrl = partialUrl,
    )
}

fun WebSearchSubjectInfoEntity.toWebSearchSubjectInfo(): WebSearchSubjectInfo {
    return WebSearchSubjectInfo(
        internalId = internalId,
        name = name,
        fullUrl = fullUrl,
        partialUrl = partialUrl,
        origin = null,
    )
}

fun WebSearchEpisodeInfo.toEntity(subjectId: Long): WebSearchEpisodeInfoEntity {
    return WebSearchEpisodeInfoEntity(
        subjectId = subjectId,
        channel = channel,
        name = name,
        episodeSortOrEp = episodeSortOrEp,
        playUrl = playUrl,
    )
}

fun WebSearchEpisodeInfoEntity.toWebSearchEpisodeInfo(): WebSearchEpisodeInfo {
    return WebSearchEpisodeInfo(
        channel = channel,
        name = name,
        episodeSortOrEp = episodeSortOrEp,
        playUrl = playUrl,
    )
}