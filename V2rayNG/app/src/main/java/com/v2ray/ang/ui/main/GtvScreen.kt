package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private enum class GtvZone { MENU, GRID }

private data class GtvCursor(val zone: GtvZone, val index: Int)

/**
 * 电视版主界面。
 *
 * 焦点不交给 Compose 的焦点查找（在部分电视/盒子上方向键不动），改为整屏单点聚焦 +
 * 自己消费方向键，按索引移动光标并绘制高亮，行为与具体机型无关。
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
    var cursor by remember { mutableStateOf(GtvCursor(GtvZone.MENU, 0)) }

    val rootFocus = remember { FocusRequester() }
    val menuState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    val statusText = when (val status = uiState.status) {
        MainStatus.Disconnected -> "未连接"
        MainStatus.Connected -> "已连接"
        MainStatus.Testing -> "测速中…"
        is MainStatus.TestProgress -> "测速中 ${status.progress}"
        is MainStatus.ConnectionTest -> "测速结果 ${status.result.delayMillis} ms"
    }

    val menu: List<Pair<String, () -> Unit>> = listOf(
        (if (isRunning) "断开连接" else "连接") to {
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
        },
        "更新全部订阅" to { onAction(MainAction.UpdateSubscriptions) },
        "全部测速" to { onAction(MainAction.TestAllServers) },
        "导入剪贴板" to { onAction(MainAction.ImportClipboard) },
        "订阅设置" to { onNavigate(MainDestination.Subscriptions) },
        "设置" to { onNavigate(MainDestination.Settings) },
        "日志" to { onNavigate(MainDestination.Logcat) }
    )

    if (pending != null) {
        val target = pending
        GtvConfirmDialog(
            title = "确认连接",
            message = "使用「${target?.remarks?.ifBlank { target.profile.server.orEmpty() }.orEmpty()}」建立连接？\n" +
                "${target?.profile?.server.orEmpty()}:${target?.profile?.serverPort.orEmpty()}  ${target?.typeDescription.orEmpty()}",
            confirmLabel = "确认连接",
            onConfirm = {
                val guid = target?.guid
                pending = null
                if (guid != null) {
                    onAction(MainAction.SelectServer(guid))
                    if (!isRunning) onAction(MainAction.ToggleService)
                }
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

    LaunchedEffect(cursor) {
        if (cursor.zone == GtvZone.MENU) {
            runCatching { menuState.animateScrollToItem(cursor.index + 1) }
        } else {
            runCatching { gridState.animateScrollToItem(cursor.index) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionDown -> {
                        cursor = when (cursor.zone) {
                            GtvZone.MENU ->
                                if (cursor.index < menu.lastIndex) GtvCursor(GtvZone.MENU, cursor.index + 1)
                                else if (rows.isNotEmpty()) GtvCursor(GtvZone.GRID, 0)
                                else cursor
                            GtvZone.GRID ->
                                if (cursor.index + 2 < rows.size) GtvCursor(GtvZone.GRID, cursor.index + 2)
                                else cursor
                        }
                        true
                    }

                    Key.DirectionUp -> {
                        cursor = when (cursor.zone) {
                            GtvZone.MENU -> GtvCursor(GtvZone.MENU, (cursor.index - 1).coerceAtLeast(0))
                            GtvZone.GRID ->
                                if (cursor.index - 2 >= 0) GtvCursor(GtvZone.GRID, cursor.index - 2)
                                else GtvCursor(GtvZone.MENU, menu.lastIndex)
                        }
                        true
                    }

                    Key.DirectionRight -> {
                        cursor = when (cursor.zone) {
                            GtvZone.MENU ->
                                if (rows.isNotEmpty()) GtvCursor(GtvZone.GRID, 0) else cursor
                            GtvZone.GRID ->
                                if (cursor.index % 2 == 0 && cursor.index + 1 < rows.size)
                                    GtvCursor(GtvZone.GRID, cursor.index + 1) else cursor
                        }
                        true
                    }

                    Key.DirectionLeft -> {
                        cursor = when (cursor.zone) {
                            GtvZone.MENU -> cursor
                            GtvZone.GRID ->
                                if (cursor.index % 2 == 1) GtvCursor(GtvZone.GRID, cursor.index - 1)
                                else GtvCursor(GtvZone.MENU, 0)
                        }
                        true
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (cursor.zone == GtvZone.MENU) {
                            menu.getOrNull(cursor.index)?.second?.invoke()
                        } else {
                            rows.getOrNull(cursor.index)?.let { pending = it }
                        }
                        true
                    }

                    else -> false
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            LazyColumn(
                state = menuState,
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
                items(menu.size) { i ->
                    GtvBigButton(
                        label = menu[i].first,
                        primary = i == 0,
                        highlighted = cursor.zone == GtvZone.MENU && cursor.index == i
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
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
                        text = "遥控器：方向键移动 · 中间 OK 键选中后确认",
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
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(rows, key = { _, row -> row.guid }) { i, row ->
                            GtvProfileCard(
                                model = row,
                                selected = row.guid == selectedGuid,
                                running = isRunning && row.guid == selectedGuid,
                                highlighted = cursor.zone == GtvZone.GRID && cursor.index == i
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { rootFocus.requestFocus() }
    }
}

/** 大按钮：只负责画，不做焦点；高亮由索引驱动。 */
@Composable
private fun GtvBigButton(label: String, primary: Boolean, highlighted: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    val baseColor = when {
        primary && highlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        primary -> MaterialTheme.colorScheme.primary
        highlighted -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = if (highlighted) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(baseColor, shape)
            .border(if (highlighted) 3.dp else 1.dp, borderColor, shape)
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

/** 配置卡片：只负责画，高亮由索引驱动。 */
@Composable
private fun GtvProfileCard(
    model: ServerRowUiModel,
    selected: Boolean,
    running: Boolean,
    highlighted: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    val baseColor = when {
        highlighted -> MaterialTheme.colorScheme.surfaceVariant
        running -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        highlighted -> MaterialTheme.colorScheme.primary
        running -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        selected -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(shape)
            .background(baseColor, shape)
            .border(if (highlighted) 3.dp else 1.dp, borderColor, shape)
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

/** 两步确认框：同样自己消费方向键，左右切换、OK 执行。 */
@Composable
private fun GtvConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var onConfirmSide by remember { mutableStateOf(true) }
    val dialogFocus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(dialogFocus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
                            onConfirmSide = !onConfirmSide
                            true
                        }

                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (onConfirmSide) onConfirm() else onDismiss()
                            true
                        }

                        else -> false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    GtvDialogButton(
                        label = confirmLabel,
                        primary = true,
                        highlighted = onConfirmSide,
                        modifier = Modifier.weight(1f)
                    )
                    GtvDialogButton(
                        label = "取消",
                        primary = false,
                        highlighted = !onConfirmSide,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { dialogFocus.requestFocus() }
    }
}

@Composable
private fun GtvDialogButton(
    label: String,
    primary: Boolean,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 62.dp,
) {
    val shape = RoundedCornerShape(12.dp)
    val baseColor = when {
        primary -> MaterialTheme.colorScheme.primary
        highlighted -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(baseColor, shape)
            .border(
                if (highlighted) 3.dp else 1.dp,
                if (highlighted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape
            ),
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
