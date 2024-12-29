/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.jellyfin

import io.ktor.client.call.body
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.Subtitle
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.HttpMediaSource
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.source.matches
import me.him188.ani.datasources.api.source.useHttpClient
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation

abstract class BaseJellyfinMediaSource(config: MediaSourceConfig) : HttpMediaSource() {
    abstract val baseUrl: String
    abstract val userId: String
    abstract val apiKey: String

    protected val client = useHttpClient(config) {
        defaultRequest {
            header(
                HttpHeaders.Authorization,
                "MediaBrowser Token=\"$apiKey\"",
            )
        }
    }

    override suspend fun checkConnection(): ConnectionStatus {
        try {
            doSearch("AA测试BB")
            return ConnectionStatus.SUCCESS
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return ConnectionStatus.FAILED
        }
    }

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> {
        return SinglePagePagedSource {
            query.subjectNames
                .asFlow()
                .flatMapConcat { subjectName ->
                    val resp = doSearch(subjectName)
                    resp.Items.asFlow()
                }
                .flatMapMerge {
                    when (it.Type) {
                        "Season" -> doSearch(parentId = it.Id).Items.asFlow()
                        "Episode" -> flowOf(it)
                        "Movie" -> flowOf(it)
                        else -> emptyFlow()
                    }
                }
                .filter { (it.Type == "Episode" || it.Type == "Movie") && it.CanDownload }
                .toList()
                .distinctBy { it.Id }
                .mapNotNull { item ->
                    val (originalTitle, episodeRange) = when (item.Type) {
                        "Episode" -> {
                            val indexNumber = item.IndexNumber ?: return@mapNotNull null
                            Pair(
                                "$indexNumber ${item.Name}",
                                EpisodeRange.single(EpisodeSort(indexNumber)),
                            )
                        }

                        "Movie" -> Pair(
                            item.Name,
                            EpisodeRange.unknownSeason(),
                        )

                        else -> return@mapNotNull null
                    }

                    MediaMatch(
                        media = DefaultMedia(
                            mediaId = item.Id,
                            mediaSourceId = mediaSourceId,
                            originalUrl = "$baseUrl/Items/${item.Id}",
                            download = ResourceLocation.HttpStreamingFile(
                                uri = getDownloadUri(item.Id),
                            ),
                            originalTitle = originalTitle,
                            publishedTime = 0,
                            properties = MediaProperties(
                                subjectName = null,
                                episodeName = null, // TODO: Maybe we can get the names from Jellyfin
                                subtitleLanguageIds = listOf("CHS"),
                                resolution = "1080P",
                                alliance = mediaSourceId,
                                size = FileSize.Unspecified,
                                subtitleKind = SubtitleKind.EXTERNAL_PROVIDED,
                            ),
                            extraFiles = MediaExtraFiles(
                                subtitles = getSubtitles(item.Id, item.MediaStreams),
                            ),
                            episodeRange = episodeRange,
                            location = MediaSourceLocation.Lan,
                            kind = MediaSourceKind.WEB,
                        ),
                        kind = MatchKind.FUZZY,
                    )
                }
                .filter { it.matches(query) != false }
                .asFlow()
        }
    }

    protected abstract fun getDownloadUri(itemId: String): String

    private fun getSubtitles(itemId: String, mediaStreams: List<MediaStream>): List<Subtitle> {
        return mediaStreams
            .filter { it.Type == "Subtitle" }
            .map { stream ->
                Subtitle(
                    uri = getSubtitleUri(itemId, stream.Index, stream.Codec),
                    language = stream.Title,
                    mimeType = when (stream.Codec.lowercase()) {
                        "ass" -> "text/x-ass"
                        else -> "application/octet-stream"  // 默认二进制流
                    },
                )
            }
    }

    private fun getSubtitleUri(itemId: String, index: Int, codec: String): String {
        return "$baseUrl/Videos/$itemId/$itemId/Subtitles/$index/0/Stream.$codec"
    }


    private suspend fun doSearch(
        subjectName: String? = null,
        recursive: Boolean = true,
        parentId: String? = null,
    ) = client.get("$baseUrl/Items") {
        parameter("userId", userId)
        parameter("enableImages", false)
        parameter("recursive", recursive)
        parameter("searchTerm", subjectName)
        parameter("fields", "CanDownload")
        parameter("fields", "MediaStreams")
        parameter("parentId", parentId)
    }.body<SearchResponse>()
}

@Serializable
private class SearchResponse(
    val Items: List<Item> = emptyList(),
)

@Serializable
@Suppress("PropertyName")
private data class MediaStream(
    val Title: String? = null, // 除了字幕以外其他可能没有
    val Type: String,
    val Codec: String,
    val Index: Int,
    val IsTextSubtitleStream: Boolean,
)

@Serializable
@Suppress("PropertyName")
private data class Item(
    val Name: String,
    val Id: String,
    val OriginalTitle: String? = null, // 日文
    val IndexNumber: Int? = null,
    val ParentIndexNumber: Int? = null,
    val Type: String, // "Episode", "Series", ...
    val CanDownload: Boolean = false,
    val MediaStreams: List<MediaStream> = emptyList(),
)
