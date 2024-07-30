package me.him188.ani.app.ui.update

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.tools.update.InstallationFailureReason
import me.him188.ani.app.tools.update.InstallationResult
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.utils.io.SystemPath
import org.koin.mp.KoinPlatform

/**
 * UI 的"有新版本"标识的状态
 */
@Stable
sealed interface UpdateLogoState {
    /**
     * 没有开启自动检查更新, 需要点击检查
     */
    @Immutable
    data object ClickToCheck : UpdateLogoState

    /**
     * 已经是最新版本
     */
    @Immutable
    data object UpToDate : UpdateLogoState

    sealed interface HasNewVersion : UpdateLogoState {
        val version: NewVersion
    }

    /**
     * 有新版本, 而且没有开启自动下载, 所以要展示一个 "更新" 图标
     */
    @Immutable
    data class HasUpdate(override val version: NewVersion) : UpdateLogoState, HasNewVersion

    /**
     * 正在下载更新
     */
    @Stable
    data class Downloading(
        override val version: NewVersion,
        private val fileDownloaderState: FileDownloaderPresentation,
    ) : HasNewVersion {
        val progress: Float get() = fileDownloaderState.progress
    }

    /**
     * 正在下载更新
     */
    @Stable
    data class DownloadFailed(
        override val version: NewVersion,
        private val throwable: Throwable,
    ) : HasNewVersion

    /**
     * 已经下载完成, 点击安装
     */
    @Immutable
    data class Downloaded(
        override val version: NewVersion,
        val file: SystemPath,
    ) : HasNewVersion

    companion object
}

fun AutoUpdateViewModel.handleClickLogo(
    context: ContextMP,
    onInstallationError: (InstallationFailureReason) -> Unit,
    showChangelogDialog: () -> Unit,
) {
    when (val logo = logoState) {
        UpdateLogoState.ClickToCheck -> {} // should not happen
        is UpdateLogoState.DownloadFailed -> this.restartDownload(context)
        is UpdateLogoState.Downloaded -> {
            val result = KoinPlatform.getKoin().get<UpdateInstaller>().install(logo.file, context)
            if (result is InstallationResult.Failed) {
                onInstallationError(result.reason)
            }
        }

        is UpdateLogoState.Downloading -> {}
        is UpdateLogoState.HasUpdate -> showChangelogDialog()
        UpdateLogoState.UpToDate -> this.startCheckLatestVersion(context)
    }
}
