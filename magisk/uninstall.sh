#!/system/bin/sh
MODDIR=${0%/*}
CONFIG_DIR="/data/adb/easytier_pro"

# 停止 easytier-core
pkill -f "${MODDIR}/bin/easytier-core" 2>/dev/null

# 清理 iptables 规则
iptables -t nat -D POSTROUTING -j ET_PRO 2>/dev/null
iptables -t nat -F ET_PRO 2>/dev/null
iptables -t nat -X ET_PRO 2>/dev/null
iptables -D FORWARD -j ET_FWD 2>/dev/null
iptables -F ET_FWD 2>/dev/null
iptables -X ET_FWD 2>/dev/null

# 清理状态文件
rm -f "${CONFIG_DIR}/easytier.pid" "${CONFIG_DIR}/status" "${CONFIG_DIR}/paused"

# 保留配置文件，只清理日志
# rm -rf "${CONFIG_DIR}"