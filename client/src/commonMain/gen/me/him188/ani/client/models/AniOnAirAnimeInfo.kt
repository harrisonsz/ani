/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

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

package me.him188.ani.client.models

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 
 *
 * @param aliases
 * @param bangumiId
 * @param name 
 * @param begin 
 * @param end 
 * @param mikanId 
 * @param recurrence 
 */
@Serializable

data class AniOnAirAnimeInfo(

    @SerialName(value = "aliases") @Required val aliases: kotlin.collections.List<kotlin.String>,

    @SerialName(value = "bangumiId") @Required val bangumiId: kotlin.Int,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "begin") val begin: kotlin.String? = null,

    @SerialName(value = "end") val end: kotlin.String? = null,

    @SerialName(value = "mikanId") val mikanId: kotlin.Int? = null,

    @SerialName(value = "recurrence") val recurrence: AniAnimeRecurrence? = null

)

