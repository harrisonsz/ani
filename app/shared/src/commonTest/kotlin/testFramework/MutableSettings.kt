/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.testFramework

import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.repository.user.Settings

class MutableSettings<T>(
    initialValue: T
) : Settings<T> {
    override val flow: MutableStateFlow<T> = MutableStateFlow(initialValue)
    override suspend fun set(value: T) {
        flow.value = value
    }
}
