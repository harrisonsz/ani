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
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.wizard.navigation.WizardController
import me.him188.ani.app.ui.wizard.navigation.WizardDefaults
import me.him188.ani.app.ui.wizard.navigation.WizardNavHost
import me.him188.ani.app.ui.wizard.step.AuthorizeUIState
import me.him188.ani.app.ui.wizard.step.BangumiAuthorize
import me.him188.ani.app.ui.wizard.step.BitTorrentFeature
import me.him188.ani.app.ui.wizard.step.ConfigureProxy
import me.him188.ani.app.ui.wizard.step.ConfigureProxyUIState
import me.him188.ani.app.ui.wizard.step.NotificationPermissionState
import me.him188.ani.app.ui.wizard.step.ProxyOverallTestState
import me.him188.ani.app.ui.wizard.step.SelectTheme

@Composable
fun WizardScreen(
    vm: WizardViewModel,
    onFinishWizard: () -> Unit,
    contactActions: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    wizardLayoutParams: WizardLayoutParams =
        WizardLayoutParams.fromWindowSizeClass(currentWindowAdaptiveInfo1().windowSizeClass),
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
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    wizardLayoutParams: WizardLayoutParams = WizardLayoutParams.Default
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
    modifier: Modifier = Modifier,
    wizardLayoutParams: WizardLayoutParams = WizardLayoutParams.Default
) {
    val context = LocalContext.current

    var notificationErrorScrolledOnce by rememberSaveable { mutableStateOf(false) }
    var bangumiAuthorizeSkipClicked by rememberSaveable { mutableStateOf(false) }
    var bangumiShowTokenAuthorizePage by remember { mutableStateOf(false) }

    val authorizeState by state.bangumiAuthorizeState.state.collectAsStateWithLifecycle(AuthorizeUIState.Idle)
    val proxyState by state.configureProxyState.state.collectAsStateWithLifecycle(ConfigureProxyUIState.Default)

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
            SelectTheme(
                config = state.selectThemeState.value,
                onUpdate = { state.selectThemeState.update(it) },
                layoutParams = wizardLayoutParams,
            )
        }
        step(
            "proxy",
            title = { Text("设置代理") },
            forwardButton = {
                WizardDefaults.GoForwardButton(
                    { controller.goForward() },
                    enabled = proxyState.overallState == ProxyOverallTestState.SUCCESS,
                )
            },
            skipButton = { WizardDefaults.SkipButton({ controller.goForward() }) },
        ) {
            val configureProxyState = state.configureProxyState

            ConfigureProxy(
                state = proxyState,
                onUpdate = { configureProxyState.onUpdateConfig(it) },
                onRequestReTest = { configureProxyState.onRequestReTest() },
                layoutParams = wizardLayoutParams,
            )
        }
        step("bittorrent", { Text("BitTorrent 功能") }) {
            val configState = state.bitTorrentFeatureState.enabled
            val notificationPermissionState by state.bitTorrentFeatureState.notificationPermissionState
                .collectAsStateWithLifecycle(NotificationPermissionState.Placeholder)


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

            // 用于在请求权限失败时滚动底部的错误信息位置, 
            // 因为错误信息显示在最底部, 手机屏幕可能显示不下, 所以需要在错误发生时自动滚动到底部让用户看到信息
            // 每次只有 lastRequestResult 从别的状态变成 false 时, 才会滚动
            // notificationErrorScrolledOnce 用于标记是否已经滚动过一次, 避免每次进入这一 step 都会滚动
            // collectAsStateWithLifecycle 的默认值也会触发一次 LaunchedEffect scope, 不能处理这种情况
            // 下面的 bangumi 授权步骤也是同理
            LaunchedEffect(notificationPermissionState.lastRequestResult) {
                if (notificationPermissionState.placeholder) return@LaunchedEffect
                if (notificationPermissionState.lastRequestResult == false) {
                    if (!notificationErrorScrolledOnce) wizardScrollState.animateScrollTo(wizardScrollState.maxValue)
                    notificationErrorScrolledOnce = true
                } else {
                    notificationErrorScrolledOnce = false
                }
            }

            BitTorrentFeature(
                bitTorrentEnabled = configState.value,
                grantedNotificationPermission = notificationPermissionState.granted,
                showPermissionError = notificationPermissionState.lastRequestResult == false,
                onBitTorrentEnableChanged = { configState.update(it) },
                showGrantNotificationItem = notificationPermissionState.showGrantNotificationItem,
                onRequestNotificationPermission = {
                    state.bitTorrentFeatureState.onRequestNotificationPermission(context)
                },
                onOpenSystemNotificationSettings = {
                    state.bitTorrentFeatureState.onOpenSystemNotificationSettings(context)
                },
                layoutParams = wizardLayoutParams,
            )
        }
        step(
            "bangumi",
            { Text("Bangumi 授权") },
            forwardButton = {
                WizardDefaults.GoForwardButton(
                    onFinishWizard,
                    text = "完成",
                    enabled = authorizeState is AuthorizeUIState.Success,
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
                    { bangumiAuthorizeSkipClicked = true },
                    "游客模式",
                )
            },
        ) {
            val scope = rememberCoroutineScope()

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

            BangumiAuthorize(
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
                        scrollUpTopAppBar()
                        wizardScrollState.animateScrollTo(0)
                    }
                },
                layoutParams = wizardLayoutParams,
            )

            if (bangumiAuthorizeSkipClicked) {
                BangumiUseGuestModeDialog(
                    onCancel = { bangumiAuthorizeSkipClicked = false },
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