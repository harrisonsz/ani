/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CardColors
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.session.launchAuthorize
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.icons.Passkey_24dp_E8EAED_FILL0_wght400_GRAD0_opsz24
import org.koin.core.component.KoinComponent


@Composable
fun <T : Any> LazyPagingItems<T>.rememberLoadErrorState(): State<LoadError?> {
    return remember(this) {
        derivedStateOf {
            LoadError.fromCombinedLoadStates(loadState)
        }
    }
}


/**
 * 一个卡片, 展示搜索时遇到的问题, 例如网络错误, 无搜索结果等.
 *
 * 提供按钮来解决错误, 例如 [onRetry].
 *
 * @param error See [rememberLoadErrorState]
 * @param onRetry 当用户点击重试时调用. 只会在 [LoadError.NetworkError], [LoadError.ServiceUnavailable], [LoadError.UnknownError] 时调用.
 * @param onLogin 当用户点击登录时调用. 只会在 [LoadError.RequiresLogin] 时调用. 如果你的功能不需要登录, 可以传递一个空函数给此参数.
 *
 * @see LoadErrorCardLayout
 */ // https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Main?node-id=239-2230&node-type=section&t=moZBMAKgeQpptXRI-0
@Composable
fun LoadErrorCard(
    error: LoadError?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onLogin: () -> Unit = run {
        val navigator = LocalNavigator.current
        {
            object : KoinComponent {}.launchAuthorize(navigator)
        }
    },
    shape: Shape = MaterialTheme.shapes.large, // behave like Dialogs.
    containerColor: Color = LoadErrorDefaults.containerColor,
) {
    if (error == null) return
    val role = LoadErrorCardRole.from(error)

    val retryButton = @Composable {
        SearchDefaults.IconTextButton(
            onRetry,
            leadingIcon = { iconModifier ->
                Icon(
                    Icons.Rounded.Refresh, null,
                    iconModifier,
                )
            },
            text = { Text("重试") },
        )
    }

    val content = @Composable { cardColors: CardColors ->
        val listItemColors = ListItemDefaults.colors(
            containerColor = cardColors.containerColor,
            leadingIconColor = cardColors.contentColor,
            trailingIconColor = cardColors.contentColor,
            headlineColor = cardColors.contentColor,
        )

        when (error) {
            LoadError.NetworkError -> {
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.WifiOff, null) },
                    headlineContent = { Text("网络错误") },
                    trailingContent = retryButton,
                    colors = listItemColors,
                )
            }

            LoadError.RateLimited -> {
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.ErrorOutline, null) },
                    headlineContent = { Text("操作过快，请重试") },
                    trailingContent = retryButton,
                    colors = listItemColors,
                )
            }

            LoadError.ServiceUnavailable -> {
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.CloudOff, null) },
                    headlineContent = { Text("服务暂不可用") },
                    trailingContent = retryButton,
                    colors = listItemColors,
                )
            }

            LoadError.NoResults -> {
                ListItem(
                    leadingContent = { Spacer(Modifier.size(24.dp)) }, // spacer
                    headlineContent = { Text("无搜索结果") },
                    colors = listItemColors,
                )
            }

            LoadError.RequiresLogin -> {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Passkey_24dp_E8EAED_FILL0_wght400_GRAD0_opsz24, null) },
                    headlineContent = { Text("此功能需要登录") },
                    trailingContent = {
                        SearchDefaults.IconTextButton(
                            onLogin,
                            leadingIcon = { iconModifier ->
                                Icon(
                                    Icons.AutoMirrored.Rounded.Login, null,
                                    iconModifier,
                                )
                            },
                            text = { Text("登录") },
                        )
                    },
                    colors = listItemColors,
                )
            }

            is LoadError.UnknownError -> {
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.ErrorOutline, null) },
                    headlineContent = { Text("未知错误") },
                    trailingContent = {
                        Row {
                            if (currentAniBuildConfig.isDebug) {
                                TextButton({ error.throwable?.printStackTrace() }) {
                                    Text("Dump Trace", fontStyle = FontStyle.Italic)
                                }
                            }

                            retryButton()
                        }
                    },
                    colors = listItemColors,
                )
            }
        }
    }


    LoadErrorCardLayout(
        role,
        modifier = modifier,
        shape = shape,
        containerColor = containerColor,
    ) {
        content(cardColors)
    }
}


/**
 * 一个卡片, 展示搜索时遇到的问题, 例如网络错误, 无搜索结果等.
 *
 * @param role See [rememberLoadErrorState] and [LoadErrorCardRole.from]
 * @param content 可以是一个 [ListItem]. 使用 [LoadErrorCardScope.listItemColors] 来获取颜色.
 */
@Composable
fun LoadErrorCardLayout(
    role: LoadErrorCardRole,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large, // behave like Dialogs.
    containerColor: Color = LoadErrorDefaults.containerColor,
    content: @Composable LoadErrorCardScope.() -> Unit,
) {
    role.Container(modifier, containerColor, shape, content)
}

@Stable
interface LoadErrorCardScope {
    val cardColors: CardColors
        @Composable get

    val listItemColors: ListItemColors
        @Composable get() = cardColors.run {
            ListItemDefaults.colors(
                containerColor = containerColor,
                leadingIconColor = contentColor,
                trailingIconColor = contentColor,
                headlineColor = contentColor,
            )
        }
}

@Stable
object LoadErrorDefaults {
    val containerColor
        @Composable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest
}
