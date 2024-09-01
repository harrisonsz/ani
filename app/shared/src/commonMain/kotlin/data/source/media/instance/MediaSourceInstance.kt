package me.him188.ani.app.data.source.media.instance

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.utils.platform.Uuid
import me.him188.ani.utils.platform.annotations.SerializationOnly
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * [MediaSource], 以及它的配置, 统称为 [MediaSourceInstance].
 */
@Stable
class MediaSourceInstance(
    val instanceId: String, // uuid, to be persisted
    val factoryId: FactoryId,
    val isEnabled: Boolean,
    val config: MediaSourceConfig,
    val source: MediaSource,
) : AutoCloseable {
    override fun close() {
        source.close()
    }

    val mediaSourceId: String get() = source.mediaSourceId
}

/**
 * 用于持久化 [MediaSourceInstance]
 */
// 持久化在文件里
@Serializable
data class MediaSourceSave @SerializationOnly constructor(
    val instanceId: String,
    val mediaSourceId: String,
    val factoryId: FactoryId = FactoryId(mediaSourceId),
    val isEnabled: Boolean,
    val config: MediaSourceConfig,
    @Transient private val _primaryConstructorMarker: Int = 0,
) {
    @OptIn(SerializationOnly::class)
    constructor(
        instanceId: String,
        mediaSourceId: String,
        factoryId: FactoryId,
        isEnabled: Boolean,
        config: MediaSourceConfig,
    ) : this(instanceId, mediaSourceId, factoryId, isEnabled, config, 0)
}

@TestOnly
fun createTestMediaSourceInstance(
    source: MediaSource,
    instanceId: String = Uuid.randomString(),
    mediaSourceId: String = source.mediaSourceId,
    isEnabled: Boolean = true,
    config: MediaSourceConfig = MediaSourceConfig.Default,
    factoryId: FactoryId = FactoryId(mediaSourceId),
): MediaSourceInstance {
    return MediaSourceInstance(
        instanceId = instanceId,
        factoryId = factoryId,
        isEnabled = isEnabled,
        config = config,
        source = source,
    )
}
