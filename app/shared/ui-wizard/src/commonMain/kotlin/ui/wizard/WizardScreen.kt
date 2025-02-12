/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.session.AuthStateNew
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.wizard.navigation.WizardController
import me.him188.ani.app.ui.wizard.navigation.WizardDefaults
import me.him188.ani.app.ui.wizard.navigation.WizardNavHost
import me.him188.ani.app.ui.wizard.step.BangumiAuthorizeStep
import me.him188.ani.app.ui.wizard.step.BitTorrentFeatureStep
import me.him188.ani.app.ui.wizard.step.ConfigureProxyStep
import me.him188.ani.app.ui.wizard.step.ConfigureProxyUIState
import me.him188.ani.app.ui.wizard.step.GrantNotificationPermissionState
import me.him188.ani.app.ui.wizard.step.ProxyOverallTestState
import me.him188.ani.app.ui.wizard.step.RequestNotificationPermission
import me.him188.ani.app.ui.wizard.step.ThemeSelectStep
import me.him188.ani.app.ui.wizard.step.ThemeSelectUIState

@Composable
fun WizardScreen(
    vm: WizardViewModel,
    onFinishWizard: () -> Unit,
    contactActions: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    wizardLayoutParams: WizardLayoutParams =
        WizardLayoutParams.calculate(currentWindowAdaptiveInfo1().windowSizeClass),
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        WizardPage(
            modifier = modifier,
            wizardController = vm.wizardController,
            wizardState = vm.wizardState,
            onFinishWizard = {
                vm.finishWizard()
                onFinishWizard()
            },
            contactActions = contactActions,
            navigationIcon = navigationIcon,
            windowInsets = windowInsets,
            wizardLayoutParams = wizardLayoutParams,
        )
    }
}

@Composable
fun WizardPage(
    wizardController: WizardController,
    wizardState: WizardPresentationState,
    onFinishWizard: () -> Unit,
    contactActions: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    wizardLayoutParams: WizardLayoutParams,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent()
) {
    Box(Modifier.windowInsetsPadding(windowInsets)) {
        WizardScene(
            controller = wizardController,
            state = wizardState,
            navigationIcon = navigationIcon,
            modifier = modifier,
            contactActions = contactActions,
            wizardLayoutParams = wizardLayoutParams,
            onFinishWizard = onFinishWizard,
        )
    }
}

@Composable
internal fun WizardScene(
    controller: WizardController,
    state: WizardPresentationState,
    contactActions: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    onFinishWizard: () -> Unit,
    wizardLayoutParams: WizardLayoutParams,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showGuestSessionDialog by rememberSaveable { mutableStateOf(false) }
    var bangumiShowTokenAuthorizePage by remember { mutableStateOf(false) }

    val authorizeState by state.bangumiAuthorizeState.state.collectAsStateWithLifecycle(AuthStateNew.Idle)
    val proxyState by state.configureProxyState.state.collectAsStateWithLifecycle(ConfigureProxyUIState.Placeholder)

    WizardNavHost(
        controller,
        modifier = modifier,
    ) {
        step(
            "theme",
            { Text("选择主题") },
            backwardButton = { Spacer(Modifier) },
            navigationIcon = navigationIcon,
        ) {
            val themeSelectUiState by state.themeSelectState.state
                .collectAsStateWithLifecycle(ThemeSelectUIState.Placeholder)
            
            ThemeSelectStep(
                config = themeSelectUiState,
                onUpdateUseDarkMode = { state.themeSelectState.onUpdateUseDarkMode(it) },
                onUpdateUseDynamicTheme = { state.themeSelectState.onUpdateUseDynamicTheme(it) },
                onUpdateSeedColor = { state.themeSelectState.onUpdateSeedColor(it) },
                layoutParams = wizardLayoutParams,
            )
        }
        step(
            "proxy",
            title = { Text("设置代理") },
            forwardButton = {
                WizardDefaults.GoForwardButton(
                    {
                        scope.launch {
                            controller.goForward()
                        }
                    },
                    enabled = proxyState.overallState == ProxyOverallTestState.SUCCESS,
                )
            },
            skipButton = {
                WizardDefaults.SkipButton(
                    {
                        scope.launch {
                            controller.goForward()
                        }
                    },
                )
            },
        ) {
            val configureProxyState = state.configureProxyState

            ConfigureProxyStep(
                state = proxyState,
                onUpdate = { configureProxyState.onUpdateConfig(it) },
                onRequestReTest = { configureProxyState.onRequestReTest() },
                layoutParams = wizardLayoutParams,
            )
        }
        step("bittorrent", { Text("BitTorrent 功能") }) {
            val monoTasker = rememberUiMonoTasker()
            
            val configState = state.bitTorrentFeatureState.enabled
            val grantNotificationPermissionState by state.bitTorrentFeatureState.grantNotificationPermissionState
                .collectAsStateWithLifecycle(GrantNotificationPermissionState.Placeholder)


            val lifecycle = LocalLifecycleOwner.current
            LaunchedEffect(lifecycle) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    state.bitTorrentFeatureState.onCheckPermissionState(context)
                }
            }

            DisposableEffect(Unit) {
                state.bitTorrentFeatureState.onCheckPermissionState(context)
                onDispose { }
            }

            BitTorrentFeatureStep(
                bitTorrentEnabled = configState.value,
                onBitTorrentEnableChanged = { configState.update(it) },
                layoutParams = wizardLayoutParams,
                requestNotificationPermission = if (grantNotificationPermissionState.showGrantNotificationItem) {
                    {
                        RequestNotificationPermission(
                            grantedNotificationPermission = grantNotificationPermissionState.granted,
                            showPermissionError = grantNotificationPermissionState.lastRequestResult == false,
                            onRequestNotificationPermission = {
                                monoTasker.launch {
                                    val granted = state.bitTorrentFeatureState.onRequestNotificationPermission(context)
                                    // 授权失败就滚动到底部, 底部有错误信息
                                    if (!granted) wizardScrollState.animateScrollTo(wizardScrollState.maxValue)
                                }
                            },
                            onOpenSystemNotificationSettings = {
                                state.bitTorrentFeatureState.onOpenSystemNotificationSettings(context)
                            },
                        )
                    }
                } else null,
            )
        }
        step(
            "bangumi",
            { Text("Bangumi 授权") },
            forwardButton = {
                WizardDefaults.GoForwardButton(
                    onFinishWizard,
                    text = "完成",
                    enabled = authorizeState is AuthStateNew.Success,
                )
            },
            navigationIcon = {
                if (bangumiShowTokenAuthorizePage) {
                    BackNavigationIconButton(
                        {
                            bangumiShowTokenAuthorizePage = false
                            state.bangumiAuthorizeState.onCheckCurrentToken()
                        },
                    )
                }
            },
            skipButton = {
                WizardDefaults.SkipButton(
                    { showGuestSessionDialog = true },
                    "游客模式",
                )
            },
        ) {
            // 如果 45s 没等到结果, 那可以认为用户可能遇到了麻烦, 我们自动滚动到底部, 底部有帮助列表
            LaunchedEffect(authorizeState) {
                if (authorizeState is AuthStateNew.AwaitingResult) {
                    delay(45_000)
                    coroutineScope {
                        launch { scrollTopAppBarCollapsed() }
                        launch { wizardScrollState.animateScrollTo(wizardScrollState.maxValue) }
                    }
                }
            }
            
            // 每次进入这一步都会检查 token 是否有效, 以及退出这一步时要取消正在进行的授权请求
            DisposableEffect(Unit) {
                state.bangumiAuthorizeState.onCheckCurrentToken()
                onDispose {
                    state.bangumiAuthorizeState.onCancelAuthorize()
                }
            }

            BackHandler(bangumiShowTokenAuthorizePage) {
                bangumiShowTokenAuthorizePage = false
            }

            BangumiAuthorizeStep(
                authorizeState = authorizeState,
                showTokenAuthorizePage = bangumiShowTokenAuthorizePage,
                contactActions = contactActions,
                onSetShowTokenAuthorizePage = { bangumiShowTokenAuthorizePage = it },
                onClickAuthorize = { state.bangumiAuthorizeState.onClickNavigateAuthorize(context) },
                onCancelAuthorize = { state.bangumiAuthorizeState.onCancelAuthorize() },
                onAuthorizeViaToken = { state.bangumiAuthorizeState.onAuthorizeViaToken(it) },
                onClickNavigateToBangumiDev = {
                    state.bangumiAuthorizeState.onClickNavigateToBangumiDev(context)
                },
                onScrollToTop = {
                    scope.launch {
                        scrollTopAppBarExpanded()
                        wizardScrollState.animateScrollTo(0)
                    }
                },
                layoutParams = wizardLayoutParams,
            )

            if (showGuestSessionDialog) {
                BangumiUseGuestModeDialog(
                    onCancel = { showGuestSessionDialog = false },
                    onConfirm = {
                        state.bangumiAuthorizeState.onUseGuestMode()
                        onFinishWizard()
                    },
                )
            }
        }
    }
}

@Composable
private fun BangumiUseGuestModeDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onCancel,
        icon = {
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
            )
        },
        title = { Text("使用游客模式") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("确认要使用游客模式吗？")
                Text("使用游客模式可以免登录进入 Ani，但无法使用同步收藏、管理追番进度、发送评论等功能")
            }
        },
        confirmButton = {
            TextButton(onConfirm) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onCancel) {
                Text("取消")
            }
        },
    )
}