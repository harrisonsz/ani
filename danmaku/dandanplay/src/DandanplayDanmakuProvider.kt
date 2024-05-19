package me.him188.ani.danmaku.dandanplay

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import me.him188.ani.app.data.subject.SubjectInfo
import me.him188.ani.danmaku.api.AbstractDanmakuProvider
import me.him188.ani.danmaku.api.DanmakuEpisode
import me.him188.ani.danmaku.api.DanmakuMatchers
import me.him188.ani.danmaku.api.DanmakuProviderConfig
import me.him188.ani.danmaku.api.DanmakuProviderFactory
import me.him188.ani.danmaku.api.DanmakuSearchRequest
import me.him188.ani.danmaku.api.DanmakuSession
import me.him188.ani.danmaku.api.TimeBasedDanmakuSession
import me.him188.ani.danmaku.dandanplay.data.SearchEpisodesAnime
import me.him188.ani.danmaku.dandanplay.data.toDanmakuOrNull
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import kotlin.coroutines.CoroutineContext

class DandanplayDanmakuProvider(
    config: DanmakuProviderConfig,
) : AbstractDanmakuProvider(config) {
    private val sessionCoroutineContext: CoroutineContext = config.coroutineContext

    companion object {
        const val ID = "弹弹play"
    }

    class Factory : DanmakuProviderFactory {
        override val id: String get() = ID

        override fun create(config: DanmakuProviderConfig): DandanplayDanmakuProvider =
            DandanplayDanmakuProvider(config)
    }

    override val id: String get() = ID

    private val dandanplayClient = DandanplayClient(client)

    override fun HttpClientConfig<*>.configureClient() {
        install(HttpRequestRetry) {
            maxRetries = 1
            delayMillis { 2000 }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000 // 弹弹服务器请求比较慢
            connectTimeoutMillis = 10_000 // 弹弹服务器请求比较慢
        }
    }

    override suspend fun fetch(
        request: DanmakuSearchRequest,
    ): DanmakuSession? {
        // 获取剧集流程:
        //
        // 1. 获取该番剧所属季度的所有番的名字, 匹配 bangumi 条目所有别名
        // 2. 若失败, 用番剧名字搜索, 匹配 bangumi 条目所有别名
        // 3. 如果按别名精准匹配到了, 那就获取该番的所有剧集
        // 4. 如果没有, 那就提交条目名字给弹弹直接让弹弹获取相关剧集 (很不准)
        //
        // 匹配剧集流程:
        // 1. 用剧集在系列中的序号 (sort) 匹配
        // 2. 用剧集在当前季度中的序号 (ep) 匹配
        // 3. 用剧集名字模糊匹配

        val episodes: List<DanmakuEpisode>? =
            kotlin.runCatching { getEpisodesByExactSubjectMatch(request) }
                .onFailure {
                    logger.error(it) { "Failed to fetch episodes by exact match" }
                }.getOrNull()
                ?: kotlin.runCatching {
                    getEpisodesByFuzzyEpisodeSearch(request)
                }.onFailure {
                    logger.error(it) { "Failed to fetch episodes by fuzzy search" }
                }.getOrNull()

        val matcher = DanmakuMatchers.mostRelevant(
            request.subjectPrimaryName,
            "第${(request.episodeEp ?: request.episodeSort).toString().removePrefix("0")}话 " + request.episodeName
        )

        // 用剧集编号匹配
        // 先用系列的, 因为系列的更大
        if (episodes != null) {
            episodes.firstOrNull { it.epOrSort != null && it.epOrSort == request.episodeSort }?.let {
                logger.info { "Matched episode by exact episodeSort: ${it.subjectName} - ${it.episodeName}" }
                return createSession(it.id.toLong(), 0)
            }
            episodes.firstOrNull { it.epOrSort != null && it.epOrSort == request.episodeEp }?.let {
                logger.info { "Matched episode by exact episodeEp: ${it.subjectName} - ${it.episodeName}" }
                return createSession(it.id.toLong(), 0)
            }
        }

        // 用名字不精确匹配
        if (!episodes.isNullOrEmpty()) {
            matcher.match(episodes)?.let {
                logger.info { "Matched episode by ep search: ${it.subjectName} - ${it.episodeName}" }
                return createSession(it.id.toLong(), 0)
            }
        }

        // 都不行, 那就用最不准的方法

        val resp = dandanplayClient.matchVideo(
            filename = request.filename,
            fileHash = request.fileHash,
            fileSize = request.fileSize,
            videoDuration = request.videoDuration
        )
        val match = if (resp.isMatched) {
            resp.matches.firstOrNull() ?: return null
        } else {
            matcher.match(resp.matches.map {
                DanmakuEpisode(
                    it.episodeId.toString(),
                    it.animeTitle,
                    it.episodeTitle,
                    null
                )
            })?.let { match ->
                resp.matches.first { it.episodeId.toString() == match.id }
            } ?: return null
        }
        logger.info { "Best match by file match: ${match.animeTitle} - ${match.episodeTitle}" }
        val episodeId = match.episodeId
        return createSession(episodeId, (match.shift * 1000L).toLong())
    }

    /**
     * 用尝试用 bangumi 给的名字 [SubjectInfo.allNames] 去精准匹配
     */
    private suspend fun DandanplayDanmakuProvider.getEpisodesByExactSubjectMatch(
        request: DanmakuSearchRequest
    ): List<DanmakuEpisode>? {
        if (!request.subjectPublishDate.isValid) return null

        // 将筛选范围缩小到季度
        val anime = getDandanplayAnimeIdOrNull(request) ?: return null
        return dandanplayClient.getBangumiEpisodes(anime.bangumiId ?: anime.animeId)
            .bangumi.episodes?.map { episode ->
                DanmakuEpisode(
                    id = episode.episodeId.toString(),
                    subjectName = request.subjectPrimaryName,
                    episodeName = episode.episodeTitle,
                    epOrSort = episode.episodeNumber?.let { EpisodeSort(it) }
                )
            }
    }

    private suspend fun getEpisodesByFuzzyEpisodeSearch(request: DanmakuSearchRequest): List<DanmakuEpisode> {
        val searchEpisodeResponse = dandanplayClient.searchEpisode(
            subjectName = request.subjectPrimaryName.trim().substringBeforeLast(" "),
            episodeName = null // 用我们的匹配算法
            //            episodeName = "第${(request.episodeEp ?: request.episodeSort).toString().removePrefix("0")}话",
            // 弹弹的是 EP 顺序
            // 弹弹数据库有时候会只有 "第x话" 没有具体标题, 所以不带标题搜索就够了
        )
        logger.info { "Ep search result: ${searchEpisodeResponse}}" }
        return searchEpisodeResponse.animes.flatMap { anime ->
            anime.episodes.map { ep ->
                DanmakuEpisode(
                    id = ep.episodeId.toString(),
                    subjectName = anime.animeTitle ?: "",
                    episodeName = ep.episodeTitle ?: "",
                    epOrSort = ep.episodeNumber?.let { EpisodeSort(it) }
                )
            }
        }
    }

    private suspend fun getDandanplayAnimeIdOrNull(request: DanmakuSearchRequest): SearchEpisodesAnime? {
        val date = request.subjectPublishDate
        val mo = date.coercedMonth
        if (mo == 0) return null

        val expectedNames = request.subjectNames.toSet()

        kotlin.runCatching {
            // 搜索这个季度的所有的番, 然后用名字匹配
            // 不建议用名字去请求弹弹 play 搜索, 它的搜索很不准
            dandanplayClient.getSeasonAnimeList(date.year, date.coercedMonth)
        }.onFailure {
            logger.error(it) { "Failed to fetch season anime list" }
        }.getOrNull()
            ?.bangumiList
            ?.firstOrNull { it.animeTitle in expectedNames }
            ?.let {
                logger.info { "Matched Dandanplay Anime in season using name: ${it.animeId} ${it.animeTitle}" }
                return it
            }


        kotlin.runCatching {
            // 用名字搜索
            dandanplayClient.searchSubject(request.subjectPrimaryName)
        }.onFailure {
            logger.error(it) { "Failed to fetch anime list by name" }
        }.getOrNull()
            ?.animes
            ?.firstOrNull { it.animeTitle in expectedNames }
            ?.let {
                logger.info { "Matched Dandanplay Anime by search using name: ${it.animeId} ${it.animeTitle}" }
                return it
            }

        return null
    }

    private suspend fun createSession(
        episodeId: Long,
        shiftMillis: Long
    ): DanmakuSession {
        val list = dandanplayClient.getDanmakuList(episodeId = episodeId)
        logger.info { "$ID Fetched danmaku list: ${list.size}" }
        return TimeBasedDanmakuSession.create(
            list.asSequence().mapNotNull { it.toDanmakuOrNull() },
            shiftMillis = shiftMillis,
            coroutineContext = sessionCoroutineContext,
        )
    }
}
