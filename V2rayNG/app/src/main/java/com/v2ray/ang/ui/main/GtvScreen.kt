package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 电视版主界面（横版大按钮）。整屏自绘，不依赖上游手机布局，
 * 上游从源仓库拉取更新时只需保留一行调用即可，不会被覆盖。
 */
@Composable
internal fun GtvScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groupId = uiState.selectedGroupId
    val groupState by mainViewModel.serverGroupState(groupId).collectAsStateWithLifecycle()
    val rows = groupState.rows
    val selectedGuid = uiState.selectedGuid
    val isRunning = uiState.isRunning
    var pending by remember { mutableStateOf<ServerRowUiModel?>(null) }
    var pendingStop by remember { mutableStateOf(false) }

    val statusText = when (val status = uiState.status) {
        MainStatus.Disconnected -> "未连接"
        MainStatus.Connected -> "已连接"
        MainStatus.Testing -> "测速中…"
        is MainStatus.TestProgress -> "测速中 ${status.progress}"
        is MainStatus.ConnectionTest -> "测速结果 ${status.result.delayMillis} ms"
    }

    if (pending != null) {
        val target = pending
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("确认连接", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "使用「${target?.remarks.orEmpty()}」建立连接？\n${target?.profile?.server.orEmpty()}  ${target?.typeDescription.orEmpty()}",
                    fontSize = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val guid = target?.guid
                    pending = null
                    if (guid != null) {
                        onAction(MainAction.SelectServer(guid))
                        if (!isRunning) onAction(MainAction.ToggleService)
                    }
                }) { Text("确认连接", fontSize = 20.sp) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("取消", fontSize = 20.sp) }
            }
        )
    }

    if (pendingStop) {
        AlertDialog(
            onDismissRequest = { pendingStop = false },
            title = { Text("确认断开", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
            text = { Text("当前连接将被断开，确认继续？", fontSize = 18.sp) },
            confirmButton = {
                TextButton(onClick = {
                    pendingStop = false
                    onAction(MainAction.ToggleService)
                }) { Text("确认断开", fontSize = 20.sp) }
            },
            dismissButton = {
                TextButton(onClick = { pendingStop = false }) { Text("取消", fontSize = 20.sp) }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 左侧：状态 + 一级菜单，全部为大按钮
        Column(
            modifier = Modifier.width(300.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "v2ray.tv",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = statusText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            GtvBigButton(
                label = if (isRunning) "断开连接" else "连接",
                primary = true,
                onClick = {
                    if (isRunning) pendingStop = true
                    else {
                        val guid = selectedGuid ?: rows.firstOrNull()?.guid
                        if (guid == null) onAction(MainAction.ImportClipboard)
                        else {
                            val row = rows.firstOrNull { it.guid == guid }
                            if (row != null) pending = row else onAction(MainAction.ToggleService)
                        }
                    }
                }
            )
            GtvBigButton(label = "配置列表", primary = false, onClick = { onAction(MainAction.RefreshGroups) })
            GtvBigButton(label = "更新全部订阅", primary = false, onClick = { onAction(MainAction.UpdateSubscriptions) })
            GtvBigButton(label = "全部测速", primary = false, onClick = { onAction(MainAction.TestAllServers) })
            GtvBigButton(label = "导入剪贴板", primary = false, onClick = { onAction(MainAction.ImportClipboard) })
            GtvBigButton(label = "订阅设置", primary = false, onClick = { onNavigate(MainDestination.Subscriptions) })
            GtvBigButton(label = "设置", primary = false, onClick = { onNavigate(MainDestination.Settings) })
            GtvBigButton(label = "日志", primary = false, onClick = { onNavigate(MainDestination.Logcat) })
        }

        // 右侧：配置大卡片，两列横向排布
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "配置（${rows.size}）",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "方向键选择 · OK 键确认",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(rows, key = { it.guid }) { row ->
                    GtvProfileCard(
                        model = row,
                        selected = row.guid == selectedGuid,
                        running = isRunning && row.guid == selectedGuid,
                        onClick = { pending = row }
                    )
                }
            }
        }
    }
}

@Composable
private fun GtvBigButton(label: String, primary: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        primary && focused -> MaterialTheme.colorScheme.primary
        primary -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        focused -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = if (primary) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(bg, RoundedCornerShape(12.dp))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            fontSize = if (primary) 24.sp else 19.sp,
            fontWeight = if (primary) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(start = 22.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GtvProfileCard(model: ServerRowUiModel, selected: Boolean, running: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        focused -> MaterialTheme.colorScheme.primary
        running -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        selected -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(if (focused) 3.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = model.remarks.ifBlank { model.profile.server.orEmpty() },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (running) {
                Spacer(Modifier.width(10.dp))
                Text("● 使用中", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            text = "${model.profile.server.orEmpty()}:${model.profile.serverPort.orEmpty()}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row {
            Text(model.typeDescription, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (model.statistics.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(model.statistics, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
