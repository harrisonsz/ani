package me.him188.ani.datasources.api

import kotlinx.serialization.Serializable
import me.him188.ani.datasources.api.EpisodeSort.Normal
import me.him188.ani.datasources.api.EpisodeSort.Special
import me.him188.ani.datasources.api.EpisodeType.ED
import me.him188.ani.datasources.api.EpisodeType.MAD
import me.him188.ani.datasources.api.EpisodeType.MainStory
import me.him188.ani.datasources.api.EpisodeType.OAD
import me.him188.ani.datasources.api.EpisodeType.OP
import me.him188.ani.datasources.api.EpisodeType.OVA
import me.him188.ani.datasources.api.EpisodeType.PV
import me.him188.ani.datasources.api.EpisodeType.SP
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.utils.serialization.BigNum

/**
 * 剧集序号, 例如 "01", "24.5", "OVA".
 *
 * - [Normal] 代表普通正片剧集, 例如 "01", "24.5". 注意, 只有整数和 ".5" 的浮点数会被解析为 Normal 类型.
 * - [Special] 代表任何其他剧集, 统称为特殊剧集, 例如 "OVA", "SP".
 *
 *
 * 在使用 [EpisodeSort] 时, 建议根据用途定义不同的变量名:
 * - `val episodeSort: EpisodeSort`: 在系列中的集数, 例如第二季的第一集为 26
 * - `val episodeEp: EpisodeSort`: 在当前季度中的集数, 例如第二季的第一集为 01
 *
 * @see EpisodeRange
 */
@Serializable // do not change package name!
sealed class EpisodeSort : Comparable<EpisodeSort> {
    /**
     * 若是普通剧集, 则返回序号, 例如 ``, 否则返回 null.
     */
    abstract val number: Float?

    /**
     * "1", "1.5", "SP". 对于小于 10 的序号, 前面没有 "0".
     *
     * @see toString
     */
    internal abstract val raw: String

    /**
     * 返回该剧集的人类易读名称.
     *
     * 为普通剧集补零了的字符串.
     * 例如 1 -> "01", 1.5 -> "1.5", SP -> "SP".
     */
    abstract override fun toString(): String

    protected fun getNumberStr(number: Float?): String {
        if (number == null) {
            return ""
        }
        if (number.toInt().toFloat() == number) {
            if (number < 10 && number >= 0) {
                return "0${number.toInt()}"
            }
            return number.toInt().toString()
        }
        return number.toString()
    }

    /**
     * An integer or a `.5` float.
     */
    @Serializable
    class Normal internal constructor(
        override val number: Float, // Luckily ".5" can be precisely represented in IEEE 754
    ) : EpisodeSort() {
        override val raw: String
            get() {
                if (number.toInt().toFloat() == number) return number.toInt().toString()
                return number.toString()
            }

        val isPartial: Boolean get() = number % 1f == 0.5f

        override fun toString(): String = getNumberStr(number)
    }

    @Serializable
    class Special internal constructor(
        val type: EpisodeType,
        override val number: Float?,
    ) : EpisodeSort() {
        override val raw: String get() = "${type.value}${getNumberStr(number)}" // "SP01"
        override fun toString(): String = raw
    }

    @Serializable
    class Unknown internal constructor(
        override val raw: String
    ) : EpisodeSort() {
        override val number: Float? = null
        override fun toString(): String = raw
    }

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other === null) return false
        if (other !is EpisodeSort) return false

        val otherFloat = other.number
        val thisFloat = number
        if (otherFloat != null && thisFloat != null && other.raw == raw) return otherFloat == thisFloat
        if (otherFloat != null || thisFloat != null) return false // one Normal one Special
        return other.raw == raw
    }

    final override fun hashCode(): Int {
        if (number != null) return number.hashCode() + raw.hashCode()
        return raw.hashCode()
    }

    final override fun compareTo(other: EpisodeSort): Int {
        if (this is Normal) {
            if (other is Normal) return number.compareTo(other.number) // Normal and Normal
            return -1 // Normal < Special
        }
        if (other is Normal) return 1 // Special > Normal

        // Special and Special
        return raw.compareTo(other.raw)
    }

    companion object {
//        fun parseRange(range: String): List<EpisodeSort> {
//            
//        }
    }
}

private fun getSpecialByRaw(raw: String): EpisodeSort {
    val type = EpisodeType.entries.firstOrNull { entry -> raw.startsWith(entry.value) }
    if (type == null || raw.length <= type.value.length) return EpisodeSort.Unknown(raw)
    val numStr = raw.substringAfter(type.value);
    return try {
        Special(type, numStr.toFloat())
    } catch (e: NumberFormatException) {
        EpisodeSort.Unknown(raw)
    }
}

fun EpisodeSort(raw: String): EpisodeSort {
    val float = raw.toFloatOrNull() ?: return getSpecialByRaw(raw)
    if (float < 0) return EpisodeSort.Unknown(raw)
    return if (float.toInt().toFloat() == float || float % 0.5f == 0f) {
        Normal(float)
    } else {
        EpisodeSort.Unknown(raw)
    }
}

fun EpisodeSort(int: Int, type: EpisodeType = MainStory): EpisodeSort {
    return EpisodeSort(BigNum(int), type)
}

/**
 * @see EpisodeType
 */
fun EpisodeSort(int: BigNum, type: EpisodeType? = MainStory): EpisodeSort {
    if (int.isNegative()) return EpisodeSort.Unknown(int.toString())
    return when (type) {
        MainStory -> Normal(int.toFloat())
        SP, OP, ED, PV, MAD, OVA, OAD -> Special(type, int.toFloat())
        null -> EpisodeSort.Unknown(int.toString())
    }
}