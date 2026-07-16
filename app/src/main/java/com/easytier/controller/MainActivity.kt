package com.easytier.controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.controller.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasyTierProApp() {
    val manager = remember { EasyTierManager() }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf(EasyTierManager.Status()) }
    var loading by remember { mutableStateOf(true) }
    var hasRoot by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    var configText by remember { mutableStateOf("") }
    var logText by remember { mutableStateOf("") }
    var snackMsg by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    fun refreshStatus() {
        scope.launch(Dispatchers.IO) {
            val s = manager.getStatus()
            withContext(Dispatchers.Main) {
                status = s
                loading = false
            }
        }
    }

    // 初始化检查
    LaunchedEffect(Unit) {
        hasRoot = withContext(Dispatchers.IO) { RootShell.hasRoot() }
        if (hasRoot) refreshStatus()
        else loading = false
    }

    // 自动刷新
    LaunchedEffect(status.running) {
        if (status.running) {
            while (true) {
                kotlinx.coroutines.delay(5000)
                refreshStatus()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Hub,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("EasyTier Pro", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { refreshStatus() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Root 状态检查
            if (!hasRoot) {
                ErrorCard("未获取 Root 权限", "请确保设备已 Root 并授予超级用户权限")
                return@Column
            }

            // 模块检查
            if (!status.moduleInstalled && !loading) {
                ErrorCard("Magisk 模块未安装", "请先在 Magisk 中安装 EasyTier Pro 模块")
                return@Column
            }

            if (loading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            // 状态卡片
            StatusCard(status)

            Spacer(Modifier.height(16.dp))

            // 控制按钮
            ControlButtons(
                status = status,
                onPause = {
                    scope.launch(Dispatchers.IO) {
                        if (manager.pause()) {
                            withContext(Dispatchers.Main) {
                                snackMsg = "已暂停"
                                snackbar.showSnackbar("已暂停")
                            }
                            refreshStatus()
                        }
                    }
                },
                onResume = {
                    scope.launch(Dispatchers.IO) {
                        if (manager.resume()) {
                            withContext(Dispatchers.Main) {
                                snackbar.showSnackbar("已恢复")
                            }
                            refreshStatus()
                        }
                    }
                },
                onRestart = {
                    scope.launch(Dispatchers.IO) {
                        if (manager.restart()) {
                            withContext(Dispatchers.Main) {
                                snackbar.showSnackbar("正在重启...")
                            }
                            kotlinx.coroutines.delay(3000)
                            refreshStatus()
                        }
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            // 节点信息
            if (status.running && status.nodeInfo.hostname.isNotEmpty()) {
                NodeInfoCard(status.nodeInfo, manager)
                Spacer(Modifier.height(12.dp))
            }

            // Peer 列表
            if (status.peers.isNotEmpty()) {
                PeersCard(status.peers, manager)
                Spacer(Modifier.height(12.dp))
            }

            // 路由列表
            if (status.routes.isNotEmpty()) {
                RoutesCard(status.routes)
                Spacer(Modifier.height(12.dp))
            }

            // 工具按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            configText = manager.getConfig()
                            withContext(Dispatchers.Main) { showConfigDialog = true }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("配置")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            logText = manager.getLogs()
                            withContext(Dispatchers.Main) { showLogDialog = true }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("日志")
                }
            }
        }
    }

    // 配置编辑对话框
    if (showConfigDialog) {
        ConfigEditDialog(
            text = configText,
            onDismiss = { showConfigDialog = false },
            onSave = { newText ->
                scope.launch(Dispatchers.IO) {
                    val ok = manager.saveConfig(newText)
                    withContext(Dispatchers.Main) {
                        showConfigDialog = false
                        snackbar.showSnackbar(if (ok) "配置已保存" else "保存失败")
                    }
                    if (ok) {
                        kotlinx.coroutines.delay(500)
                        refreshStatus()
                    }
                }
            }
        )
    }

    // 日志查看对话框
    if (showLogDialog) {
        LogDialog(
            text = logText,
            onDismiss = { showLogDialog = false },
            onRefresh = {
                scope.launch(Dispatchers.IO) {
                    logText = manager.getLogs()
                }
            }
        )
    }
}

@Composable
fun ErrorCard(title: String, desc: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Spacer(Modifier.height(8.dp))
            Text(desc, fontSize = 14.sp, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
fun StatusCard(status: EasyTierManager.Status) {
    val (color, text, icon) = when {
        status.running && !status.paused -> Triple<Color, String, androidx.compose.ui.graphics.vector.ImageVector>(GreenRun, "运行中", Icons.Default.CheckCircle)
        status.paused -> Triple<Color, String, androidx.compose.ui.graphics.vector.ImageVector>(OrangeWarn, "已暂停", Icons.Default.PauseCircle)
        else -> Triple<Color, String, androidx.compose.ui.graphics.vector.ImageVector>(RedStop, "已停止", Icons.Default.Cancel)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
            if (status.nodeInfo.virtualIp.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "IP: ${status.nodeInfo.virtualIp}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (status.error.isNotEmpty() && !status.running) {
                Spacer(Modifier.height(8.dp))
                Text(
                    status.error,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ControlButtons(
    status: EasyTierManager.Status,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (status.running && !status.paused) {
            Button(
                onClick = onPause,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeWarn)
            ) {
                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("暂停")
            }
        } else if (status.paused || !status.running) {
            Button(
                onClick = onResume,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = GreenRun)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("启动")
            }
        }
        OutlinedButton(
            onClick = onRestart,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("重启")
        }
    }
}

@Composable
fun NodeInfoCard(node: EasyTierManager.NodeInfo, manager: EasyTierManager) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("本机节点", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            InfoRow("主机名", node.hostname)
            InfoRow("虚拟 IP", node.virtualIp)
            if (node.version.isNotEmpty()) InfoRow("版本", node.version)
        }
    }
}

@Composable
fun PeersCard(peers: List<EasyTierManager.PeerInfo>, manager: EasyTierManager) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("对等节点 (${peers.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            peers.forEach { peer ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                InfoRow("主机名", peer.hostname)
                InfoRow("虚拟 IP", peer.virtualIp)
                if (peer.publicIp.isNotEmpty()) InfoRow("公网 IP", peer.publicIp)
                peer.conns.forEach { conn ->
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 2.dp)) {
                        Text("  ${conn.connType}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(
                            if (conn.latency > 0) "${conn.latency}ms" else "--",
                            fontSize = 12.sp,
                            color = if (conn.latency < 100) GreenRun else if (conn.latency < 300) OrangeWarn else RedStop,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp)) {
                        Text(
                            "  ↓${manager.formatBytes(conn.rxBytes)} ↑${manager.formatBytes(conn.txBytes)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoutesCard(routes: List<EasyTierManager.RouteInfo>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("路由表 (${routes.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            routes.forEach { route ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${route.peerName} (${route.peerIpv4})",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text("cost=${route.cost}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (route.nextHopName.isNotEmpty()) {
                    Text(
                        "  → ${route.nextHopName} (${route.nextHopIpv4})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(value, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun ConfigEditDialog(text: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var edited by remember { mutableStateOf(text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑配置文件") },
        text = {
            OutlinedTextField(
                value = edited,
                onValueChange = { edited = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(edited) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun LogDialog(text: String, onDismiss: () -> Unit, onRefresh: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("日志", modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
                    android.widget.Toast.makeText(context, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制全部")
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新日志")
                }
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                val scrollState = rememberScrollState()
                // SelectionContainer 包裹后文本可长按选中复制
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text = text,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .verticalScroll(scrollState)
                            .fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}