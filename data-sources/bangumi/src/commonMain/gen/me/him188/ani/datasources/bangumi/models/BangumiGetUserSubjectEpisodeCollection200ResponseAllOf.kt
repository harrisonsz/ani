/**
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 *
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport",
)

package me.him188.ani.datasources.bangumi.models

import me.him188.ani.datasources.bangumi.models.BangumiUserEpisodeCollection

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

/**
 *
 *
 * @param `data`
 */
@Serializable

data class BangumiGetUserSubjectEpisodeCollection200ResponseAllOf(

    @SerialName(value = "data") val `data`: kotlin.collections.List<BangumiUserEpisodeCollection>? = null

)

