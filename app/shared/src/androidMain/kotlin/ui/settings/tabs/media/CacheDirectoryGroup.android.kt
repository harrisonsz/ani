package me.him188.ani.app.ui.settings.tabs.media

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.rememberViewModel
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SingleSelectionItem

@Composable
private fun renderTorrentCacheLocationName(cacheLocation: AndroidTorrentCacheLocation): String {
    return when (cacheLocation) {
        is AndroidTorrentCacheLocation.InternalPrivate -> "内部私有目录"
        is AndroidTorrentCacheLocation.ExternalPrivate -> "外部私有目录"
    }
}

@Composable
private fun renderTorrentCacheLocation(cacheLocation: AndroidTorrentCacheLocation): String {
    return when (cacheLocation) {
        is AndroidTorrentCacheLocation.InternalPrivate -> cacheLocation.path
        is AndroidTorrentCacheLocation.ExternalPrivate -> cacheLocation.path ?: "不可用"
    }
}

@Composable
private fun renderTorrentCacheLocationDescription(cacheLocation: AndroidTorrentCacheLocation) = buildString {
    appendLine(
        when (cacheLocation) {
            is AndroidTorrentCacheLocation.ExternalPrivate -> "此目录允许其他应用访问"
            is AndroidTorrentCacheLocation.InternalPrivate -> "此目录仅 Ani 可访问"
        },
    )
    append(renderTorrentCacheLocation(cacheLocation))
}

@Composable
private fun renderMigrationStatus(status: AndroidTorrentCacheViewModel.MigrationStatus) = when (status) {
    is AndroidTorrentCacheViewModel.MigrationStatus.Init -> "正在准备..."
    is AndroidTorrentCacheViewModel.MigrationStatus.Cache ->
        if (status.currentFile != null) "迁移缓存: \n${status.currentFile}" else "迁移缓存..."

    is AndroidTorrentCacheViewModel.MigrationStatus.Metadata ->
        if (status.currentMetadata != null) "合并元数据: \n${status.currentMetadata}" else "合并元数据..."
}

@Composable
actual fun SettingsScope.CacheDirectoryGroup(vm: MediaSettingsViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val cacheVm = rememberViewModel {
        AndroidTorrentCacheViewModel(context, vm.mediaCacheSettings, vm.permissionManager)
    }

    Group({ Text("存储设置") }) {
        val loading = vm.mediaSelectorSettings.loading

        LaunchedEffect(key1 = loading) {
            if (!loading) {
                cacheVm.refreshStorageState()
            }
        }

        SingleSelectionItem(
            title = { Text("BT 视频缓存位置") },
            description = { Text(if (it != null) renderTorrentCacheLocation(it) else "未知目录") },
            modifier = Modifier.placeholder(loading),
            items = cacheVm.torrentLocationPresentation,
            dialogIcon = { Icon(imageVector = Icons.Default.Storage, contentDescription = null) },
            dialogDescription = {
                Text("更改缓存目录后 Ani 会将所有缓存移动至新的位置。")
            },
            selected = cacheVm.currentSelectionIndex,
            key = { it.javaClass.name },
            listItem = {
                ItemHeader(
                    title = { Text(text = renderTorrentCacheLocationName(it)) },
                    description = { Text(text = renderTorrentCacheLocationDescription(it)) },
                )
            },
            onOpenDialog = {
                scope.launch { cacheVm.refreshStorageState() }
            },
            /*onSelectItem = { location ->
                if (location !is AndroidTorrentCacheLocation.ExternalShared || location.accessible) {
                    return@SingleSelectionItem true
                }

                val grantDir = cacheVm.requestExternalSharedStorage()
                when (grantDir) {
                    null -> false // 关闭了请求对话框
                    "" -> {
                        toaster.toast("未选择任何目录并授予权限。")
                        false
                    }

                    else -> {
                        cacheVm.refreshStorageState()
                        true
                    }
                }
            },*/
            onConfirm = { location ->
                scope.launch { location?.let { cacheVm.setStorage(it) } }
            },
        )
    }

    if (cacheVm.showMigrationDialog) {
        AlertDialog(
            title = { Text("正在迁移缓存") },
            text = {
                Column {
                    Text(renderMigrationStatus(status = cacheVm.migrationStatus))
                    Spacer(modifier = Modifier.height(24.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("迁移数据需要一些时间，提前取消可能导致缓存数据损坏。")
                    Text("迁移完成或取消迁移后请重启 Ani。")
                }
            },
            onDismissRequest = { /* not dismiss-able */ },
            confirmButton = {
                TextButton({ cacheVm.cancelCacheMigration() }) { Text("取消") }
            },
        )
    }

    if (cacheVm.showExternalSharedStorageRequestDialog) {
        AlertDialog(
            onDismissRequest = { cacheVm.respondSelectExternalSharedStorage(false) },
            confirmButton = {
                Button({ cacheVm.respondSelectExternalSharedStorage(true) }) { Text("确认") }
            },
            dismissButton = {
                TextButton({ cacheVm.respondSelectExternalSharedStorage(false) }) { Text("取消") }
            },
            title = { Text("授予 Ani 外部共享存储权限") },
            text = {
                Column {
                    Text("将 BT 视频缓存位置设置为外部共享存储之前需要授予 Ani 对应的存储访问权限。")
                    Text("为了你的数据安全，Ani 将仅仅申请外部存储的其中一个目录的访问权限，你的其他文件将不会被 Ani 读取。")
                    Text("点击确定后将打开文件选择器，请选择一个目录并授予 Ani 权限。")
                }
            },
        )
    }
}