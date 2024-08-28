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

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 
 *
 * @param id 条目ID
 * @param date 上映/开播/连载开始日期，可能为空字符串
 * @param image 封面
 * @param summary 条目描述
 * @param name 条目原名
 * @param nameCn 条目中文名
 * @param tags 
 * @param score 评分
 * @param rank 排名
 * @param type 
 */
@Serializable

data class BangumiSearchSubjects200ResponseDataInner(

    /* 条目ID */
    @SerialName(value = "id") @Required val id: kotlin.Int,

    /* 上映/开播/连载开始日期，可能为空字符串 */
    @SerialName(value = "date") @Required val date: kotlin.String,

    /* 封面 */
    @SerialName(value = "image") @Required val image: kotlin.String,

    /* 条目描述 */
    @SerialName(value = "summary") @Required val summary: kotlin.String,

    /* 条目原名 */
    @SerialName(value = "name") @Required val name: kotlin.String,

    /* 条目中文名 */
    @SerialName(value = "name_cn") @Required val nameCn: kotlin.String,

    @SerialName(value = "tags") @Required val tags: kotlin.collections.List<BangumiTag>,

    /* 评分 */
    @SerialName(value = "score") @Required val score: @Serializable(me.him188.ani.utils.serialization.BigNumAsDoubleStringSerializer::class) me.him188.ani.utils.serialization.BigNum,

    /* 排名 */
    @SerialName(value = "rank") @Required val rank: kotlin.Int,

    @SerialName(value = "type") val type: BangumiSubjectType? = null

)

