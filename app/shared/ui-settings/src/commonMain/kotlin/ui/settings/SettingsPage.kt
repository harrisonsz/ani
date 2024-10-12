/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.layout.AnimatedPane1
import me.him188.ani.app.ui.foundation.layout.Zero
import me.him188.ani.app.ui.foundation.layout.cardVerticalPadding
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.TopAppBarGoBackButton
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.rendering.P2p
import me.him188.ani.app.ui.settings.tabs.AboutTab
import me.him188.ani.app.ui.settings.tabs.DebugTab
import me.him188.ani.app.ui.settings.tabs.app.AppearanceGroup
import me.him188.ani.app.ui.settings.tabs.app.PlayerGroup
import me.him188.ani.app.ui.settings.tabs.app.SoftwareUpdateGroup
import me.him188.ani.app.ui.settings.tabs.media.CacheDirectoryGroup
import me.him188.ani.app.ui.settings.tabs.media.MediaSelectionGroup
import me.him188.ani.app.ui.settings.tabs.media.TorrentEngineGroup
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceGroup
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceSubscriptionGroup
import me.him188.ani.app.ui.settings.tabs.network.DanmakuGroup
import me.him188.ani.app.ui.settings.tabs.network.GlobalProxyGroup
import me.him188.ani.utils.platform.hasScrollingBug

/**
 * @see renderPreferenceTab 查看名称
 */
typealias SettingsTab = me.him188.ani.app.navigation.SettingsTab

@Composable
fun SettingsPage(
    vm: SettingsViewModel,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    val navigator: ThreePaneScaffoldNavigator<SettingsTab> = rememberListDetailPaneScaffoldNavigator<SettingsTab>()
    val currentTab by remember(navigator) {
        derivedStateOf {
            navigator.currentDestination?.content
        }
    }

    SettingsPageLayout(
        navigator,
        listPane = { topAppBarScrollBehavior ->
            PermanentDrawerSheet(
                Modifier
                    .padding(vertical = currentWindowAdaptiveInfo().windowSizeClass.paneVerticalPadding)
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState()),
                drawerContainerColor = Color.Unspecified,
            ) {
                @Composable
                fun Item(item: SettingsTab) {
                    NavigationDrawerItem(
                        icon = { Icon(getIcon(item), contentDescription = null) },
                        label = { Text(getName(item)) },
                        selected = item == currentTab,
                        onClick = { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, item) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                @Composable
                fun Title(text: String) {
                    Text(
                        text,
                        Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 20.dp, bottom = 12.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Title("应用与界面")
                Item(SettingsTab.APPEARANCE)
                Item(SettingsTab.UPDATE)

                Title("数据源与播放")
                Item(SettingsTab.PLAYER)
                Item(SettingsTab.MEDIA_SUBSCRIPTION)
                Item(SettingsTab.MEDIA_SOURCE)
                Item(SettingsTab.MEDIA_SELECTOR)
                Item(SettingsTab.DANMAKU)

                Title("网络与存储")
                Item(SettingsTab.PROXY)
                Item(SettingsTab.BT)
                Item(SettingsTab.CACHE)
                Item(SettingsTab.STORAGE)

                Title("其他")
                Item(SettingsTab.ABOUT)
                Item(SettingsTab.DEBUG)
            }
        },
        detailPaneTitle = { currentTab ->
            Text(getName(currentTab))
        },
        detailPaneContent = { currentTab ->
            when (currentTab) {
                SettingsTab.ABOUT -> AboutTab()
                SettingsTab.DEBUG -> DebugTab(vm.debugSettingsState)
                else -> SettingsTab(Modifier.padding(all = 16.dp)) {
                    when (currentTab) {
                        SettingsTab.APPEARANCE -> AppearanceGroup(vm.uiSettings)
                        SettingsTab.UPDATE -> SoftwareUpdateGroup(vm.softwareUpdateGroupState)
                        SettingsTab.PLAYER -> PlayerGroup(
                            vm.videoScaffoldConfig,
                            vm.danmakuFilterConfigState,
                            vm.danmakuRegexFilterState,
                            vm.isInDebugMode,
                        )

                        SettingsTab.MEDIA_SUBSCRIPTION -> MediaSourceSubscriptionGroup(
                            vm.mediaSourceSubscriptionGroupState,
                        )

                        SettingsTab.MEDIA_SOURCE -> MediaSourceGroup(
                            vm.mediaSourceGroupState,
                            vm.editMediaSourceState,
                        )

                        SettingsTab.MEDIA_SELECTOR -> MediaSelectionGroup(vm.mediaSelectionGroupState)
                        SettingsTab.DANMAKU -> DanmakuGroup(vm.danmakuSettingsState, vm.danmakuServerTesters)
                        SettingsTab.PROXY -> GlobalProxyGroup(vm.proxySettingsState)
                        SettingsTab.BT -> TorrentEngineGroup(vm.torrentSettingsState)
                        SettingsTab.CACHE -> CacheDirectoryGroup(vm.cacheDirectoryGroupState)
                        SettingsTab.STORAGE -> CacheDirectoryGroup(vm.cacheDirectoryGroupState)
                        SettingsTab.ABOUT -> {} // see above
                        SettingsTab.DEBUG -> {}
                        null -> {}
                    }
                }
            }
        },
        modifier,
        contentWindowInsets,
    )
}

@Composable
internal fun SettingsPageLayout(
    navigator: ThreePaneScaffoldNavigator<SettingsTab>,
    listPane: @Composable (topAppBarScrollBehavior: TopAppBarScrollBehavior) -> Unit,
    detailPaneTitle: @Composable (tab: SettingsTab) -> Unit,
    detailPaneContent: @Composable (currentTab: SettingsTab?) -> Unit,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    BackHandler(navigator.canNavigateBack(BackNavigationBehavior.PopUntilScaffoldValueChange)) {
        navigator.navigateBack(BackNavigationBehavior.PopUntilScaffoldValueChange)
    }

    ListDetailPaneScaffold(
        navigator.scaffoldDirective,
        navigator.scaffoldValue,
        listPane = {
            AnimatedPane1 {
                Column {
                    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                    TopAppBar(
                        title = { Text("设置") },
                        colors = AniThemeDefaults.transparentAppBarColors(),
                        windowInsets = WindowInsets.Zero,
                        scrollBehavior = if (LocalPlatform.current.hasScrollingBug()) null else topAppBarScrollBehavior,
                    )
//                    Surface {
//                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
//                            Text("设置", Modifier.padding(horizontal = 16.dp))
//                        }
//                    }

                    listPane(topAppBarScrollBehavior)
                }
            }
        },
        detailPane = {
            AnimatedPane1 {
                val content = @Composable {
                    Column {
                        AnimatedContent(
                            navigator.currentDestination?.content,
                            Modifier.fillMaxSize(),
                            transitionSpec = AniThemeDefaults.standardAnimatedContentTransition,
                        ) { tab ->
                            Column {
                                tab?.let {
                                    TopAppBar(
                                        title = {
                                            detailPaneTitle(it)
                                        },
                                        navigationIcon = {
                                            TopAppBarGoBackButton {
                                                navigator.navigateBack(BackNavigationBehavior.PopUntilScaffoldValueChange)
                                            }
                                        },
                                        colors = AniThemeDefaults.transparentAppBarColors(),
                                    )
                                }

                                detailPaneContent(tab)
                            }
                        }
                    }
                }

                val decorated = @Composable {
                    // PC 上套一个 card
                    if (navigator.scaffoldDirective.maxHorizontalPartitions > 1) {
                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            content()
                        }
                    } else {
                        content()
                    }
                }

                decorated()
            }
        },
        modifier
//            .padding(currentWindowAdaptiveInfo().windowSizeClass.panePadding)
            .windowInsetsPadding(contentWindowInsets)
            .consumeWindowInsets(contentWindowInsets),
    )
}

@Stable
private fun getIcon(tab: SettingsTab): ImageVector {
    return when (tab) {
        SettingsTab.APPEARANCE -> Icons.Rounded.Palette
        SettingsTab.UPDATE -> Icons.Rounded.Update
        SettingsTab.PLAYER -> Icons.Rounded.SmartDisplay
        SettingsTab.MEDIA_SUBSCRIPTION -> Icons.Rounded.Subscriptions
        SettingsTab.MEDIA_SOURCE -> Icons.Rounded.Subscriptions
        SettingsTab.MEDIA_SELECTOR -> Icons.Rounded.FilterList
        SettingsTab.DANMAKU -> Icons.Rounded.Subtitles
        SettingsTab.PROXY -> Icons.Rounded.VpnKey
        SettingsTab.BT -> Icons.Filled.P2p
        SettingsTab.CACHE -> Icons.Rounded.Download
        SettingsTab.STORAGE -> Icons.Rounded.Storage
        SettingsTab.ABOUT -> Icons.Rounded.Info
        SettingsTab.DEBUG -> Icons.Rounded.Science
    }
}

@Stable
private fun getName(tab: SettingsTab): String {
    return when (tab) {
        SettingsTab.APPEARANCE -> "界面"
        SettingsTab.UPDATE -> "软件更新"
        SettingsTab.PLAYER -> "播放器"
        SettingsTab.MEDIA_SUBSCRIPTION -> "数据源订阅"
        SettingsTab.MEDIA_SOURCE -> "数据源"
        SettingsTab.MEDIA_SELECTOR -> "观看偏好"
        SettingsTab.DANMAKU -> "弹幕"
        SettingsTab.PROXY -> "代理"
        SettingsTab.BT -> "BitTorrent"
        SettingsTab.CACHE -> "缓存"
        SettingsTab.STORAGE -> "存储空间"
        SettingsTab.ABOUT -> "关于"
        SettingsTab.DEBUG -> "调试"
    }
}

@Composable
fun SettingsTab(
    modifier: Modifier = Modifier,
    content: @Composable SettingsScope.() -> Unit,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(
            currentWindowAdaptiveInfo().windowSizeClass.cardVerticalPadding,
        ),
    ) {
        val scope = remember(this) {
            object : SettingsScope(), ColumnScope by this {}
        }
        scope.content()
    }
}
