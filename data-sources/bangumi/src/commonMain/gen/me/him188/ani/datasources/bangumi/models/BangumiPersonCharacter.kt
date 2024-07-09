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

import me.him188.ani.datasources.bangumi.models.BangumiCharacterType
import me.him188.ani.datasources.bangumi.models.BangumiPersonImages

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

/**
 *
 *
 * @param id
 * @param name
 * @param type 角色，机体，舰船，组织...
 * @param subjectId
 * @param subjectName
 * @param subjectNameCn
 * @param images object with some size of images, this object maybe `null`
 * @param staff
 */
@Serializable

data class BangumiPersonCharacter(

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "name") @Required val name: kotlin.String,

    /* 角色，机体，舰船，组织... */
    @SerialName(value = "type") @Required val type: BangumiCharacterType,

    @SerialName(value = "subject_id") @Required val subjectId: kotlin.Int,

    @SerialName(value = "subject_name") @Required val subjectName: kotlin.String,

    @SerialName(value = "subject_name_cn") @Required val subjectNameCn: kotlin.String,

    /* object with some size of images, this object maybe `null` */
    @SerialName(value = "images") val images: BangumiPersonImages? = null,

    @SerialName(value = "staff") val staff: kotlin.String? = null

)

