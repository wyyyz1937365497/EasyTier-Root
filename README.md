# EasyTier Pro

> 适配 SukiSU Ultra、KernelSU 与 Magisk 的 EasyTier 系统级透明代理方案，不占用 Android VPN 服务

## 架构

```
┌─────────────────────────────────┐
│     Android 应用 (Compose)       │
│  ┌───────┐  ┌──────┐  ┌──────┐ │
│  │ 状态  │  │ 配置 │  │ 日志 │ │
│  │ 面板  │  │ 编辑 │  │ 查看 │ │
│  └───┬───┘  └──┬───┘  └──┬───┘ │
└──────┼─────────┼─────────┼──────┘
       │ su -c   │ cat >   │ tail
       ▼         ▼         ▼
┌──────────────────────────────────┐
│   Magisk 模块 (系统级 Root)       │
│  ┌──────────────────────────┐    │
│  │  easytier-core (TUN)     │    │
│  │  easytier-cli  (RPC)     │    │
│  │  easytier-web  (Web UI)  │    │
│  └──────────┬───────────────┘    │
│             │                    │
│  ┌──────────▼───────────────┐    │
│  │  iptables NAT/FORWARD    │    │
│  │  (透明路由 + 热点转发)    │    │
│  └──────────────────────────┘    │
│             │                    │
│  ┌──────────▼───────────────┐    │
│  │  守护进程 (10s 轮询)      │    │
│  │  自动重启 + 状态反馈      │    │
│  └──────────────────────────┘    │
└──────────────────────────────────┘
              │
              ▼ TUN 设备 (不占用 VPN)
        ┌───────────┐
        │ EasyTier  │
        │ P2P 网络  │
        └───────────┘
```

## 组成部分

### 1. Root 模块 (`magisk/`)
- `bin/easytier-core` — EasyTier 核心二进制 (aarch64)
- `bin/easytier-cli` — CLI 管理工具
- `bin/easytier-web` — Web 控制台
- `scripts/easytier_core.sh` — 守护进程，自动启停 + 状态更新
- `scripts/iptables_setup.sh` — iptables NAT/FORWARD 路由规则
- `config/config.toml` — 默认配置文件
- `service.sh` — Magisk 开机启动入口
- `action.sh` — Magisk 操作按钮（暂停/恢复）

### 2. Android 应用 (`app/`)
- **状态面板** — 运行状态、虚拟 IP、节点信息
- **对等节点** — Peer 列表、连接延迟、流量统计
- **路由表** — 网络路由信息
- **配置编辑** — 在线编辑 config.toml
- **日志查看** — 实时查看运行日志
- **控制操作** — 启动/暂停/重启

## 构建

### 构建 APK
```bash
chmod +x setup_android_env.sh && ./setup_android_env.sh
./gradlew assembleDebug
```
APK 位于 `app/build/outputs/apk/debug/app-debug.apk`

### 打包 Root 模块
```bash
cd magisk
chmod +x build.sh
./build.sh sukisu   # SukiSU Ultra / KernelSU
./build.sh magisk   # Magisk
```
SukiSU Ultra ZIP 位于 `easytier-pro-sukisu-v2.6.4.zip`。

## 安装

1. 在 SukiSU Ultra Manager 中刷入 `easytier-pro-sukisu-v2.6.4.zip`
2. 重启设备
3. 安装 APK（已有相同版本时无需重装）
4. 打开 App，编辑配置文件（填入网络名称、密钥、对端节点）
5. 重启或点击“重启”按钮使配置生效

## 配置说明

配置文件路径：`/data/adb/easytier_pro/config.toml`

关键配置项：
- `network_name` — 网络名称（所有节点需一致）
- `network_secret` — 网络密钥（所有节点需一致）
- `[[peer]]` — 对端节点地址
- `dev_name` — TUN 设备名（留空自动生成）
- `dhcp` — 自动分配虚拟 IP

## 与官方模块的区别

| 特性 | 官方模块 | EasyTier Pro |
|------|---------|--------------|
| 路由设置 | 仅 ip rule | iptables NAT + FORWARD |
| 热点转发 | 需手动操作 | 自动检测 + 配置 |
| 状态管理 | module.prop 文字 | 原生 App 实时显示 |
| 配置编辑 | 手动改文件 | App 内编辑器 |
| 流量统计 | 无 | Peer 连接详情 |
| 控制方式 | Magisk 开关 | App + Magisk 双控 |
| VPN 占用 | 不占用 | 不占用 |

## 参考项目

- [EasyTier](https://github.com/EasyTier/EasyTier) — 原始项目
- [Surfing](https://github.com/GitMetaio/Surfing) — Magisk 透明代理架构参考