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
        val cost: String = "",
        val latencyMs: String = "",
        val lossRate: String = "",
        val rxBytes: String = "",
        val txBytes: String = "",
        val tunnelProto: String = "",
        val natType: String = "",
        val isP2p: Boolean = false,
        val isRelay: Boolean = false,
        val isLocal: Boolean = false,
        val version: String = ""
    )

    data class RouteInfo(
        val hostname: String = "",
        val ipv4: String = "",
        val nextHopHostname: String = "",
        val nextHopIpv4: String = "",
        val pathLatency: Double = 0.0,
        val pathLen: Int = 0
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
            return Status(moduleInstalled = false, error = "Magisk 模块未安装")
        }

        val pausedResult = RootShell.exec("test -f ${RootShell.getConfigDir()}/paused && echo yes", 3000)
        val paused = pausedResult.output.contains("yes")

        val pidResult = RootShell.exec("pgrep -f easytier-core | head -1", 3000)
        val running = pidResult.output.isNotBlank() && pidResult.output.any { it.isDigit() }

        if (!running) {
            return Status(
                running = false, paused = paused, moduleInstalled = true,
                error = if (paused) "服务已暂停" else "服务未运行"
            )
        }

        val nodeInfo = try {
            val raw = runCli("node", "info")
            parseNodeInfo(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get node info", e)
            NodeInfo(running = true)
        }

        val peers = try {
            val raw = runCli("peer", "list")
            parsePeers(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get peers", e)
            emptyList()
        }

        val routes = try {
            val raw = runCli("route", "list")
            parseRoutes(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get routes", e)
            emptyList()
        }

        return Status(
            running = true, paused = paused, moduleInstalled = true,
            nodeInfo = nodeInfo, peers = peers, routes = routes
        )
    }

    private fun parseNodeInfo(json: String): NodeInfo {
        if (json.isBlank()) return NodeInfo(running = true)
        val obj = JSONObject(json)
        return NodeInfo(
            hostname = obj.optString("hostname", ""),
            virtualIp = obj.optString("ipv4_addr", ""),
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
            val cost = obj.optString("cost", "")
            peers.add(PeerInfo(
                hostname = obj.optString("hostname", ""),
                virtualIp = obj.optString("ipv4", obj.optString("cidr", "")),
                cost = cost,
                latencyMs = obj.optString("lat_ms", "-"),
                lossRate = obj.optString("loss_rate", "-"),
                rxBytes = obj.optString("rx_bytes", "-"),
                txBytes = obj.optString("tx_bytes", "-"),
                tunnelProto = obj.optString("tunnel_proto", ""),
                natType = obj.optString("nat_type", ""),
                isP2p = cost == "p2p",
                isRelay = cost.startsWith("relay"),
                isLocal = cost == "Local",
                version = obj.optString("version", "")
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
                hostname = obj.optString("hostname", ""),
                ipv4 = obj.optString("ipv4", ""),
                nextHopHostname = obj.optString("next_hop_hostname", ""),
                nextHopIpv4 = obj.optString("next_hop_ipv4", ""),
                pathLatency = obj.optDouble("path_latency", 0.0),
                pathLen = obj.optInt("path_len", 0)
            ))
        }
        return routes
    }

    fun pause(): Boolean {
        return RootShell.exec("touch ${RootShell.getConfigDir()}/paused", 3000).success
    }

    fun resume(): Boolean {
        return RootShell.exec("rm -f ${RootShell.getConfigDir()}/paused", 3000).success
    }

    fun restart(): Boolean {
        return RootShell.exec(
            "rm -f ${RootShell.getConfigDir()}/paused && pkill -f easytier-core; sleep 2", 8000
        ).success
    }

    fun getConfig(): String {
        val r = RootShell.exec("cat ${RootShell.getConfigDir()}/config.toml", 3000)
        return if (r.success) r.output else ""
    }

    fun saveConfig(content: String): Boolean {
        val tmpFile = "/data/local/tmp/et_config.toml"
        val cmd = "cat > $tmpFile << 'ETPROEOF'\n$content\nETPROEOF\n" +
                  "cp $tmpFile ${RootShell.getConfigDir()}/config.toml && rm $tmpFile"
        return RootShell.exec(cmd, 5000).success
    }

    fun getLogs(tailLines: Int = 100): String {
        val r = RootShell.exec("tail -$tailLines ${RootShell.getConfigDir()}/logs/core.log 2>/dev/null", 3000)
        return if (r.success) r.output else "无日志"
    }
}