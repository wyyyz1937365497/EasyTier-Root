package com.easytier.controller

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * EasyTier 状态管理器
 * 通过 easytier-cli -o json 获取网络状态
 * 通过 root shell 控制服务启停
 */
class EasyTierManager {

    companion object {
        private const val TAG = "EasyTierManager"
        private const val RPC_PORTAL = "127.0.0.1:15888"
    }

    data class NodeInfo(
        val hostname: String = "",
        val virtualIp: String = "",
        val version: String = "",
        val running: Boolean = false
    )

    data class PeerInfo(
        val hostname: String = "",
        val virtualIp: String = "",
        val publicIp: String = "",
        val latencies: Map<String, Long> = emptyMap(),
        val conns: List<ConnInfo> = emptyList()
    )

    data class ConnInfo(
        val connType: String = "",
        val remoteAddr: String = "",
        val latency: Long = 0,
        val rxBytes: Long = 0,
        val txBytes: Long = 0,
        val lossRate: Double = 0.0
    )

    data class RouteInfo(
        val peerName: String = "",
        val peerIpv4: String = "",
        val nextHopName: String = "",
        val nextHopIpv4: String = "",
        val cost: Int = 0
    )

    data class Status(
        val running: Boolean = false,
        val paused: Boolean = false,
        val moduleInstalled: Boolean = false,
        val nodeInfo: NodeInfo = NodeInfo(),
        val peers: List<PeerInfo> = emptyList(),
        val routes: List<RouteInfo> = emptyList(),
        val error: String = ""
    )

    private fun runCli(vararg args: String): String {
        val cliPath = "${RootShell.getBinDir()}/easytier-cli"
        val cmd = "$cliPath -p $RPC_PORTAL -o json ${args.joinToString(" ")}"
        val result = RootShell.exec(cmd, 8000)
        return if (result.success) result.output else ""
    }

    fun getStatus(): Status {
        val moduleInstalled = RootShell.isModuleInstalled()
        if (!moduleInstalled) {
            return Status(
                moduleInstalled = false,
                error = "Magisk 模块未安装"
            )
        }

        // 检查暂停状态
        val pausedResult = RootShell.exec("test -f ${RootShell.getConfigDir()}/paused && echo yes", 3000)
        val paused = pausedResult.output.contains("yes")

        // 检查进程是否运行
        val pidResult = RootShell.exec("pgrep -f easytier-core | head -1", 3000)
        val running = pidResult.output.isNotBlank() && pidResult.output.any { it.isDigit() }

        if (!running) {
            return Status(
                running = false,
                paused = paused,
                moduleInstalled = true,
                error = if (paused) "服务已暂停" else "服务未运行"
            )
        }

        // 获取节点信息
        val nodeInfo = try {
            val raw = runCli("node", "info")
            parseNodeInfo(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get node info", e)
            NodeInfo(running = true)
        }

        // 获取 Peer 列表
        val peers = try {
            val raw = runCli("peer", "list")
            parsePeers(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get peers", e)
            emptyList()
        }

        // 获取路由列表
        val routes = try {
            val raw = runCli("route", "list")
            parseRoutes(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get routes", e)
            emptyList()
        }

        return Status(
            running = true,
            paused = paused,
            moduleInstalled = true,
            nodeInfo = nodeInfo,
            peers = peers,
            routes = routes
        )
    }

    private fun parseNodeInfo(json: String): NodeInfo {
        if (json.isBlank()) return NodeInfo(running = true)
        val obj = JSONObject(json)
        return NodeInfo(
            hostname = obj.optString("hostname", ""),
            virtualIp = obj.optString("virtual_ipv4", obj.optString("ipv4", "")),
            version = obj.optString("version", ""),
            running = true
        )
    }

    private fun parsePeers(json: String): List<PeerInfo> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        val peers = mutableListOf<PeerInfo>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val conns = mutableListOf<ConnInfo>()
            val connsArr = obj.optJSONArray("conns")
            if (connsArr != null) {
                for (j in 0 until connsArr.length()) {
                    val c = connsArr.getJSONObject(j)
                    conns.add(ConnInfo(
                        connType = c.optString("conn_id", c.optString("conn_type", "")),
                        remoteAddr = c.optString("remote_addr", ""),
                        latency = c.optLong("latency_ms", 0),
                        rxBytes = c.optLong("rx_bytes", 0),
                        txBytes = c.optLong("tx_bytes", 0),
                        lossRate = c.optDouble("loss_rate", 0.0)
                    ))
                }
            }
            peers.add(PeerInfo(
                hostname = obj.optString("hostname", ""),
                virtualIp = obj.optString("virtual_ipv4", obj.optString("ipv4", "")),
                publicIp = obj.optString("public_ipv4", ""),
                conns = conns
            ))
        }
        return peers
    }

    private fun parseRoutes(json: String): List<RouteInfo> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        val routes = mutableListOf<RouteInfo>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            routes.add(RouteInfo(
                peerName = obj.optString("peer_name", ""),
                peerIpv4 = obj.optString("peer_ipv4", ""),
                nextHopName = obj.optString("next_hop_name", ""),
                nextHopIpv4 = obj.optString("next_hop_ipv4", ""),
                cost = obj.optInt("cost", 0)
            ))
        }
        return routes
    }

    fun pause(): Boolean {
        val result = RootShell.exec("touch ${RootShell.getConfigDir()}/paused", 3000)
        return result.success
    }

    fun resume(): Boolean {
        val result = RootShell.exec("rm -f ${RootShell.getConfigDir()}/paused", 3000)
        return result.success
    }

    fun restart(): Boolean {
        val moduleDir = RootShell.getModuleDir()
        val result = RootShell.exec(
            "rm -f ${RootShell.getConfigDir()}/paused && " +
            "pkill -f easytier-core; sleep 2",
            8000
        )
        // 守护进程会自动重启
        return result.success
    }

    fun getConfig(): String {
        val result = RootShell.exec("cat ${RootShell.getConfigDir()}/config.toml", 3000)
        return if (result.success) result.output else ""
    }

    fun saveConfig(content: String): Boolean {
        // 写入临时文件再移动
        val tmpFile = "/data/local/tmp/et_config.toml"
        val cmd = "cat > $tmpFile << 'ETPROEOF'\n$content\nETPROEOF\n" +
                  "cp $tmpFile ${RootShell.getConfigDir()}/config.toml && rm $tmpFile"
        val result = RootShell.exec(cmd, 5000)
        return result.success
    }

    fun getLogs(tailLines: Int = 100): String {
        val result = RootShell.exec("tail -$tailLines ${RootShell.getConfigDir()}/logs/core.log 2>/dev/null", 3000)
        return if (result.success) result.output else "无日志"
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "%.2f MB".format(bytes / 1_000_000.0)
            bytes >= 1_000 -> "%.2f KB".format(bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}