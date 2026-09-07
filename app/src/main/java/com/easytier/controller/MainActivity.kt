package com.easytier.controller

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easytier.controller.ui.theme.EasyTierProTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GITHUB_PROFILE_URL = "https://github.com/wyyyz1937365497"
private const val GITHUB_REPOSITORY_URL = "$GITHUB_PROFILE_URL/EasyTier-Root"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EasyTierProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EasyTierProApp()
                }
            }
        }
    }
}

private enum class UiAction {
    Refresh,
    Service,
    Restart,
    Config,
    Logs
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EasyTierProApp() {
    val manager = remember { EasyTierManager() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var status by remember { mutableStateOf(EasyTierManager.Status()) }
    var loading by remember { mutableStateOf(true) }
    var hasRoot by remember { mutableStateOf(false) }
    var activeAction by remember { mutableStateOf<UiAction?>(null) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var configText by remember { mutableStateOf("") }
    var logText by remember { mutableStateOf("") }

    suspend fun readEverything(): Pair<Boolean, EasyTierManager.Status> {
        return withContext(Dispatchers.IO) {
            val rootGranted = RootShell.hasRoot()
            rootGranted to if (rootGranted) manager.getStatus() else EasyTierManager.Status()
        }
    }

    fun refreshStatus(showMessage: Boolean = true) {
        if (activeAction != null) return
        scope.launch {
            activeAction = UiAction.Refresh
            val result = runCatching { readEverything() }
            result.onSuccess { (rootGranted, nextStatus) ->
                hasRoot = rootGranted
                status = nextStatus
                loading = false
            }
            activeAction = null
            if (showMessage) {
                snackbar.showSnackbar(if (result.isSuccess) "状态已刷新" else "刷新失败")
            }
        }
    }

    fun runServiceAction(
        type: UiAction,
        successMessage: String,
        action: () -> Boolean,
        settleDelay: Long = 0L
    ) {
        if (activeAction != null) return
        scope.launch {
            activeAction = type
            val result = runCatching { withContext(Dispatchers.IO) { action() } }
            if (result.getOrDefault(false) && settleDelay > 0L) delay(settleDelay)
            if (hasRoot) {
                runCatching { withContext(Dispatchers.IO) { manager.getStatus() } }
                    .onSuccess { status = it }
            }
            activeAction = null
            snackbar.showSnackbar(
                if (result.getOrDefault(false)) successMessage else "操作失败，请查看日志"
            )
        }
    }

    fun openConfig() {
        if (activeAction != null) return
        scope.launch {
            activeAction = UiAction.Config
            val result = runCatching { withContext(Dispatchers.IO) { manager.getConfig() } }
            activeAction = null
            result.onSuccess {
                configText = it
                showConfigDialog = true
            }.onFailure {
                snackbar.showSnackbar("无法读取配置文件")
            }
        }
    }

    fun openLogs() {
        if (activeAction != null) return
        scope.launch {
            activeAction = UiAction.Logs
            val result = runCatching { withContext(Dispatchers.IO) { manager.getLogs() } }
            activeAction = null
            result.onSuccess {
                logText = it
                showLogDialog = true
            }.onFailure {
                snackbar.showSnackbar("无法读取日志")
            }
        }
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            scope.launch { snackbar.showSnackbar("没有可打开此链接的应用") }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { readEverything() }
            .onSuccess { (rootGranted, initialStatus) ->
                hasRoot = rootGranted
                status = initialStatus
            }
        loading = false
    }

    LaunchedEffect(hasRoot, status.running) {
        while (hasRoot && status.running) {
            delay(5_000)
            if (activeAction == null) {
                runCatching { withContext(Dispatchers.IO) { manager.getStatus() } }
                    .onSuccess { status = it }
            }
        }
    }

    val controlsEnabled = hasRoot && status.moduleInstalled && !loading && activeAction == null

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { contentPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            ControlRail(
                status = status,
                controlsEnabled = controlsEnabled,
                activeAction = activeAction,
                onToggleService = {
                    if (status.running && !status.paused) {
                        runServiceAction(UiAction.Service, "EasyTier 已暂停", manager::pause)
                    } else {
                        runServiceAction(
                            UiAction.Service,
                            "EasyTier 已启动",
                            manager::resume,
                            settleDelay = 1_500
                        )
                    }
                },
                onRestart = {
                    runServiceAction(
                        UiAction.Restart,
                        "EasyTier 已重启",
                        manager::restart,
                        settleDelay = 3_000
                    )
                },
                onRefresh = { refreshStatus() },
                onConfig = ::openConfig,
                onLogs = ::openLogs,
                onAbout = { showAboutDialog = true }
            )

            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(modifier = Modifier.weight(1f)) {
                DashboardHeader(status = status, hasRoot = hasRoot, loading = loading)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InformationFeed(
                    modifier = Modifier.weight(1f),
                    status = status,
                    hasRoot = hasRoot,
                    loading = loading
                )
            }
        }
    }

    if (showConfigDialog) {
        ConfigEditDialog(
            text = configText,
            onDismiss = { showConfigDialog = false },
            onSave = { newText ->
                scope.launch {
                    activeAction = UiAction.Config
                    val ok = runCatching {
                        withContext(Dispatchers.IO) { manager.saveConfig(newText) }
                    }.getOrDefault(false)
                    activeAction = null
                    showConfigDialog = false
                    snackbar.showSnackbar(if (ok) "配置已保存" else "保存失败")
                    if (ok && hasRoot) {
                        runCatching { withContext(Dispatchers.IO) { manager.getStatus() } }
                            .onSuccess { status = it }
                    }
                }
            }
        )
    }

    if (showLogDialog) {
        LogDialog(
            text = logText,
            onDismiss = { showLogDialog = false },
            onRefresh = {
                scope.launch {
                    logText = runCatching {
                        withContext(Dispatchers.IO) { manager.getLogs() }
                    }.getOrDefault("无法读取日志")
                }
            }
        )
    }

    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            onOpenProfile = { openUrl(GITHUB_PROFILE_URL) },
            onOpenRepository = { openUrl(GITHUB_REPOSITORY_URL) }
        )
    }
}

@Composable
private fun ControlRail(
    status: EasyTierManager.Status,
    controlsEnabled: Boolean,
    activeAction: UiAction?,
    onToggleService: () -> Unit,
    onRestart: () -> Unit,
    onRefresh: () -> Unit,
    onConfig: () -> Unit,
    onLogs: () -> Unit,
    onAbout: () -> Unit
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets(0, 0, 0, 0),
        header = {
            Surface(
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp).size(48.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = "EasyTier Pro",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) {
        val isActive = status.running && !status.paused
        RailAction(
            label = if (isActive) "暂停" else "启动",
            icon = if (isActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
            selected = isActive,
            enabled = controlsEnabled,
            loading = activeAction == UiAction.Service,
            onClick = onToggleService
        )
        RailAction(
            label = "重启",
            icon = Icons.Default.RestartAlt,
            enabled = controlsEnabled,
            loading = activeAction == UiAction.Restart,
            onClick = onRestart
        )
        RailAction(
            label = "刷新",
            icon = Icons.Default.Refresh,
            enabled = activeAction == null,
            loading = activeAction == UiAction.Refresh,
            onClick = onRefresh
        )
        RailAction(
            label = "配置",
            icon = Icons.Default.Edit,
            enabled = controlsEnabled,
            loading = activeAction == UiAction.Config,
            onClick = onConfig
        )
        RailAction(
            label = "日志",
            icon = Icons.Default.Description,
            enabled = controlsEnabled,
            loading = activeAction == UiAction.Logs,
            onClick = onLogs
        )
        Spacer(modifier = Modifier.weight(1f))
        RailAction(label = "关于", icon = Icons.Default.Info, enabled = true, onClick = onAbout)
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun RailAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(imageVector = icon, contentDescription = label)
            }
        },
        modifier = modifier,
        enabled = enabled,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        alwaysShowLabel = true
    )
}

@Composable
private fun DashboardHeader(
    status: EasyTierManager.Status,
    hasRoot: Boolean,
    loading: Boolean
) {
    val (stateLabel, stateColor, stateContainer) = when {
        loading -> Triple(
            "正在检查",
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant
        )
        !hasRoot -> Triple(
            "等待 Root",
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.errorContainer
        )
        !status.moduleInstalled -> Triple(
            "模块未安装",
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.errorContainer
        )
        status.running && !status.paused -> Triple(
            "运行中",
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primaryContainer
        )
        status.paused -> Triple(
            "已暂停",
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer
        )
        else -> Triple(
            "已停止",
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.errorContainer
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "EasyTier Pro",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "网络控制中心 · 运行时信息每 5 秒更新",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(shape = CircleShape, color = stateContainer, contentColor = stateColor) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape).background(stateColor)
                )
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InformationFeed(
    modifier: Modifier,
    status: EasyTierManager.Status,
    hasRoot: Boolean,
    loading: Boolean
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp
    ) {
        if (loading) {
            item(key = "loading-status") { LoadingInfoCard("服务状态") }
            item(key = "loading-network") { LoadingInfoCard("网络概览") }
            item(key = "loading-node") { LoadingInfoCard("本机节点") }
            item(key = "loading-environment") { LoadingInfoCard("运行环境") }
        } else {
            if (!hasRoot) {
                item(key = "root-notice") {
                    RequirementNoticeCard(
                        icon = Icons.Default.Security,
                        title = "需要 Root 权限",
                        message = "请在 Root 管理器中授予 EasyTier Pro 超级用户权限，然后使用左侧的刷新按钮重试。"
                    )
                }
            } else if (!status.moduleInstalled) {
                item(key = "module-notice") {
                    RequirementNoticeCard(
                        icon = Icons.Default.Warning,
                        title = "EasyTier 模块未安装",
                        message = "请先在 SukiSU、KernelSU 或 Magisk 中安装 EasyTier Pro 模块。"
                    )
                }
            }

            item(key = "status") { ServiceStatusCard(status = status, hasRoot = hasRoot) }
            item(key = "overview") { NetworkOverviewCard(status) }
            item(key = "node") { NodeInfoCard(status.nodeInfo) }
            item(key = "environment") {
                EnvironmentCard(
                    hasRoot = hasRoot,
                    moduleInstalled = status.moduleInstalled,
                    serviceRunning = status.running
                )
            }
            item(key = "peers") { PeersCard(status.peers) }
            item(key = "routes") { RoutesCard(status.routes) }
        }
    }
}

@Composable
private fun LoadingInfoCard(title: String) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                "正在读取设备信息…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RequirementNoticeCard(icon: ImageVector, title: String, message: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ServiceStatusCard(status: EasyTierManager.Status, hasRoot: Boolean) {
    val isUnavailable = !hasRoot || !status.moduleInstalled
    val visual = when {
        isUnavailable -> StatusVisual(
            "不可用",
            if (!hasRoot) "尚未取得 Root 权限" else "尚未安装系统模块",
            Icons.Default.Lock,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        status.running && !status.paused -> StatusVisual(
            "运行中",
            "EasyTier 核心服务工作正常",
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        status.paused -> StatusVisual(
            "已暂停",
            "服务保持配置但不参与网络",
            Icons.Default.PauseCircle,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        else -> StatusVisual(
            "已停止",
            status.error.ifBlank { "EasyTier 核心服务未运行" },
            Icons.Default.Cancel,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = visual.container,
            contentColor = visual.content
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = visual.icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    visual.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(visual.supportingText, style = MaterialTheme.typography.bodySmall)
            }
            if (status.nodeInfo.virtualIp.isNotBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = visual.content.copy(alpha = 0.10f),
                    contentColor = visual.content
                ) {
                    Text(
                        text = status.nodeInfo.virtualIp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }
    }
}

private data class StatusVisual(
    val title: String,
    val supportingText: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color
)

@Composable
private fun NetworkOverviewCard(status: EasyTierManager.Status) {
    val remotePeers = status.peers.count { !it.isLocal }
    val p2pPeers = status.peers.count { it.isP2p }
    val relayPeers = status.peers.count { it.isRelay }

    InfoCard(title = "网络概览", icon = Icons.Default.Lan) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("节点", remotePeers.toString(), Modifier.weight(1f))
            MetricTile("直连", p2pPeers.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("中继", relayPeers.toString(), Modifier.weight(1f))
            MetricTile("路由", status.routes.size.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NodeInfoCard(node: EasyTierManager.NodeInfo) {
    InfoCard(title = "本机节点", icon = Icons.Default.SettingsEthernet) {
        InfoField("主机名", node.hostname)
        InfoField("虚拟 IP", node.virtualIp, monospace = true)
        InfoField("EasyTier 版本", node.version)
    }
}

@Composable
private fun EnvironmentCard(hasRoot: Boolean, moduleInstalled: Boolean, serviceRunning: Boolean) {
    InfoCard(title = "运行环境", icon = Icons.Default.Terminal) {
        StateLine("Root 权限", hasRoot)
        StateLine("系统模块", moduleInstalled)
        StateLine("核心进程", serviceRunning)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "RPC · 127.0.0.1:15888",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StateLine(label: String, ready: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (ready) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            if (ready) "就绪" else "不可用",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PeersCard(peers: List<EasyTierManager.PeerInfo>) {
    InfoCard(
        title = "对等节点",
        icon = Icons.Default.Hub
    ) {
        if (peers.isEmpty()) {
            EmptyCardContent("暂无已连接节点")
        } else {
            peers.forEachIndexed { index, peer ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PeerItem(peer)
            }
        }
    }
}

@Composable
private fun PeerItem(peer: EasyTierManager.PeerInfo) {
    val badge = when {
        peer.isLocal -> BadgeVisual(
            "本机",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        peer.isP2p -> BadgeVisual(
            "P2P",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        peer.isRelay -> BadgeVisual(
            "中继",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        else -> BadgeVisual(
            peer.cost.ifBlank { "未知" },
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    val latencyValue = peer.latencyMs.toDoubleOrNull()
    val latencyColor = when {
        latencyValue == null -> MaterialTheme.colorScheme.onSurfaceVariant
        latencyValue < 50 -> MaterialTheme.colorScheme.primary
        latencyValue < 150 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = peer.hostname.ifBlank { "未命名节点" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Badge(badge)
        }
        Text(
            text = peer.virtualIp.ifBlank { "无虚拟 IP" },
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Speed,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = latencyColor
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (peer.latencyMs == "-") "--" else "${peer.latencyMs} ms",
                style = MaterialTheme.typography.labelMedium,
                color = latencyColor
            )
            Spacer(Modifier.weight(1f))
            if (peer.tunnelProto.isNotBlank()) {
                Text(
                    peer.tunnelProto.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ArrowDownward,
                contentDescription = "接收",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "接收",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                peer.rxBytes.takeUnless { it == "-" }.orEmpty().ifBlank { "--" },
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ArrowUpward,
                contentDescription = "发送",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "发送",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                peer.txBytes.takeUnless { it == "-" }.orEmpty().ifBlank { "--" },
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (peer.lossRate != "-" && peer.lossRate != "0.0%") {
            Text(
                "丢包 ${peer.lossRate}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private data class BadgeVisual(val label: String, val container: Color, val content: Color)

@Composable
private fun Badge(visual: BadgeVisual) {
    Surface(shape = CircleShape, color = visual.container, contentColor = visual.content) {
        Text(
            text = visual.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun RoutesCard(routes: List<EasyTierManager.RouteInfo>) {
    InfoCard(title = "路由表", icon = Icons.Default.Route) {
        if (routes.isEmpty()) {
            EmptyCardContent("暂无可用路由")
        } else {
            routes.forEachIndexed { index, route ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RouteItem(route)
            }
        }
    }
}

@Composable
private fun RouteItem(route: EasyTierManager.RouteInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = route.hostname.ifBlank { "未命名路由" },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (route.pathLatency > 0) {
                Text(
                    "${route.pathLatency.toInt()} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (route.ipv4.isNotBlank()) {
            Text(
                route.ipv4,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (route.nextHopHostname.isNotBlank() &&
            route.nextHopHostname != "Local" &&
            route.nextHopHostname != "DIRECT"
        ) {
            Text(
                "经由 ${route.nextHopHostname}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            content()
        }
    }
}

@Composable
private fun InfoField(label: String, value: String, monospace: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { "--" },
            style = if (monospace) {
                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyCardContent(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.SettingsEthernet,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConfigEditDialog(text: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var edited by remember(text) { mutableStateOf(text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
        title = { Text("编辑配置") },
        text = {
            OutlinedTextField(
                value = edited,
                onValueChange = { edited = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 440.dp),
                label = { Text("config.toml") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        },
        confirmButton = { TextButton(onClick = { onSave(edited) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun LogDialog(text: String, onDismiss: () -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Description, contentDescription = null) },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("运行日志", modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    clipboardManager?.setPrimaryClip(ClipData.newPlainText("EasyTier Pro logs", text))
                    android.widget.Toast.makeText(
                        context,
                        "已复制全部日志",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制全部")
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新日志")
                }
            }
        },
        text = {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 420.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                SelectionContainer {
                    Text(
                        text = text,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                            .padding(12.dp).fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenRepository: () -> Unit
) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(32.dp))
                }
            }
        },
        title = { Text("EasyTier Pro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("系统级 EasyTier 网络控制台", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "版本 $version · 为 Root Android 设备设计",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AboutLink(
                    icon = Icons.Default.AccountCircle,
                    title = "@wyyyz1937365497",
                    description = "GitHub 个人主页",
                    onClick = onOpenProfile
                )
                AboutLink(
                    icon = Icons.Default.Code,
                    title = "EasyTierPro",
                    description = "查看项目仓库",
                    onClick = onOpenRepository
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun AboutLink(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "打开链接")
        },
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    )
}
