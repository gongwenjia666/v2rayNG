package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.ui.compose.dpadClickable
import com.v2ray.ang.ui.compose.dpadFocusOutline
import kotlinx.coroutines.delay

/**
 * 电视版主界面：整屏自绘，纯遥控器操作。
 *
 * 交互模型：方向键在左侧大按钮与右侧配置卡片之间移动焦点，中间 OK 键「先选中、再弹确认框」，
 * 确认框内再按一次 OK 才真正执行。全部焦点初始落在左侧主按钮上，保证电视一进界面就有焦点。
 *
 * 本文件是新增文件，上游从源仓库拉取更新时不会被覆盖，除非上游改动了调用点。
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

    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(200)
        runCatching { primaryFocus.requestFocus() }
    }

    val statusText = when (val status = uiState.status) {
        MainStatus.Disconnected -> "未连接"
        MainStatus.Connected -> "已连接"
        MainStatus.Testing -> "测速中…"
        is MainStatus.TestProgress -> "测速中 ${status.progress}"
        is MainStatus.ConnectionTest -> "测速结果 ${status.result.delayMillis} ms"
    }

    pending?.let { target ->
        GtvConfirmDialog(
            title = "确认连接",
            message = "使用「${target.remarks.ifBlank { target.profile.server.orEmpty() }}」建立连接？\n" +
                "${target.profile.server.orEmpty()}:${target.profile.serverPort.orEmpty()}  ${target.typeDescription}",
            confirmLabel = "确认连接",
            onConfirm = {
                pending = null
                onAction(MainAction.SelectServer(target.guid))
                if (!isRunning) onAction(MainAction.ToggleService)
            },
            onDismiss = { pending = null }
        )
    }

    if (pendingStop) {
        GtvConfirmDialog(
            title = "确认断开",
            message = "当前连接将被断开，确定继续？",
            confirmLabel = "确认断开",
            onConfirm = {
                pendingStop = false
                onAction(MainAction.ToggleService)
            },
            onDismiss = { pendingStop = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 左侧：状态 + 一级菜单，可上下滚动，焦点默认落在这里
        LazyColumn(
            modifier = Modifier.width(300.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "v2ray.tv",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = statusText,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                GtvBigButton(
                    label = if (isRunning) "断开连接" else "连接",
                    primary = true,
                    focusRequester = primaryFocus,
                    onClick = {
                        if (isRunning) {
                            pendingStop = true
                        } else {
                            val guid = selectedGuid ?: rows.firstOrNull()?.guid
                            if (guid == null) {
                                onAction(MainAction.ImportClipboard)
                            } else {
                                val row = rows.firstOrNull { it.guid == guid }
                                if (row != null) pending = row else onAction(MainAction.ToggleService)
                            }
                        }
                    }
                )
            }
            item { GtvBigButton("更新全部订阅", false) { onAction(MainAction.UpdateSubscriptions) } }
            item { GtvBigButton("全部测速", false) { onAction(MainAction.TestAllServers) } }
            item { GtvBigButton("导入剪贴板", false) { onAction(MainAction.ImportClipboard) } }
            item { GtvBigButton("订阅设置", false) { onNavigate(MainDestination.Subscriptions) } }
            item { GtvBigButton("设置", false) { onNavigate(MainDestination.Settings) } }
            item { GtvBigButton("日志", false) { onNavigate(MainDestination.Logcat) } }
        }

        // 右侧：配置大卡片，两列，方向键左右上下移动
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "配置（${rows.size}）",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "方向键移动 · 中间 OK 键选中后确认",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (rows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "还没有配置，先用「导入剪贴板」或「更新全部订阅」拉一份",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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
}

/** 大按钮：整条可聚焦，焦点时换底色+描边，OK 键或回车触发。 */
@Composable
private fun GtvBigButton(
    label: String,
    primary: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val baseColor = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val focusColor = if (primary) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.primaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(baseColor, shape)
            .dpadFocusOutline(
                focusRequester = focusRequester,
                cornerRadius = 12.dp,
                focusContainerColor = focusColor
            )
            .dpadClickable(onClick)
            .padding(start = 20.dp, end = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            fontSize = if (primary) 23.sp else 18.sp,
            fontWeight = if (primary) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (primary) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 配置卡片：焦点时粗边框，OK 键弹出确认框。 */
@Composable
private fun GtvProfileCard(
    model: ServerRowUiModel,
    selected: Boolean,
    running: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val baseColor = if (running) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(shape)
            .background(baseColor, shape)
            .dpadFocusOutline(cornerRadius = 14.dp)
            .dpadClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = model.remarks.ifBlank { model.profile.server.orEmpty() },
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (running) {
                Spacer(Modifier.width(8.dp))
                Text("● 使用中", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            } else if (selected) {
                Spacer(Modifier.width(8.dp))
                Text("已选中", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** 两步确认框：自绘，两个大按钮都可用方向键选择，默认焦点在「确认」。 */
@Composable
private fun GtvConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { confirmFocus.requestFocus() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(600.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .padding(horizontal = 30.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    GtvDialogButton(
                        label = confirmLabel,
                        primary = true,
                        focusRequester = confirmFocus,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    )
                    GtvDialogButton(
                        label = "取消",
                        primary = false,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GtvDialogButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    height: Dp = 62.dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(
                if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape
            )
            .dpadFocusOutline(
                focusRequester = focusRequester,
                cornerRadius = 12.dp,
                focusContainerColor = if (primary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer
            )
            .dpadClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = if (primary) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}
