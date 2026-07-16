#!/system/bin/sh
#
# EasyTier Pro - 路由与防火墙设置
#
# 核心问题：Android 策略路由中 wlan0 表（prio ~10000+）优先级高于
# main 表，导致发往 EasyTier 虚拟网段的流量被错误路由到 wlan0。
#
# 修复方案：添加高优先级 ip rule，将目标为 EasyTier 网段的流量
# 强制走 main 表（TUN 设备路由在 main 表中）。
#
SCRIPT_DIR=${0%/*}
MODDIR="${SCRIPT_DIR%/*}"
CONFIG_DIR="/data/adb/easytier_pro"
LOG_FILE="${CONFIG_DIR}/logs/iptables.log"
CONFIG_FILE="${CONFIG_DIR}/config.toml"

IPT="/system/bin/iptables"
IP="/system/bin/ip"

# 策略路由规则优先级（必须小于 Android wlan0 表的优先级 10000）
RULE_PRIO="9000"

# 从 config.toml 读取 TUN 设备名
get_tun_dev() {
    if [ -f "$CONFIG_FILE" ]; then
        DEV=$(awk '/^[[:space:]]*dev_name[[:space:]]*=/{gsub(/[" \t]/,"");split($0,a,"=");if(a[2]!="")print a[2]}' "$CONFIG_FILE")
    fi
    if [ -z "$DEV" ] || [ "$DEV" = '""' ]; then
        # 自动检测：EasyTier 的 TUN 设备通常以 et_ 开头或 tun 开头
        DEV=$($IP link | awk -F': ' '/ (et_[[:alnum:]]+|tun[[:alnum:]]+)/{print $2; exit}' | cut -d'@' -f1)
    fi
    echo "$DEV"
}

# 获取 EasyTier 虚拟网段（从 TUN 设备地址推算 CIDR）
get_et_cidr() {
    DEV=$(get_tun_dev)
    [ -n "$DEV" ] && $IP -4 addr show dev "$DEV" 2>/dev/null | awk '/inet /{print $2; exit}'
}

# 获取 EasyTier 虚拟 IP（不含掩码）
get_et_ip() {
    DEV=$(get_tun_dev)
    [ -n "$DEV" ] && $IP -4 addr show dev "$DEV" 2>/dev/null | awk '/inet /{print $2; exit}' | cut -d'/' -f1
}

enable_routing() {
    DEV=$(get_tun_dev)
    ET_CIDR=$(get_et_cidr)

    if [ -z "$DEV" ]; then
        echo "$(date): TUN 设备未找到，跳过路由设置" >> "$LOG_FILE"
        return 1
    fi

    echo "$(date): 启用路由，TUN 设备=$DEV, 网段=$ET_CIDR" >> "$LOG_FILE"

    # 1. 确保 IP 转发开启
    echo 1 > /proc/sys/net/ipv4/ip_forward

    # 2. 【关键修复】添加高优先级策略路由规则
    #    Android 的 wlan0 表优先级（~10000）高于 main 表，
    #    导致 EasyTier 网段流量被错误路由到 wlan0。
    #    这里添加 prio 9000 的规则，强制 EasyTier 网段走 main 表。
    if [ -n "$ET_CIDR" ]; then
        # 删除旧规则（如果存在）
        $IP rule del to "$ET_CIDR" lookup main prio "$RULE_PRIO" 2>/dev/null
        # 添加新规则
        $IP rule add to "$ET_CIDR" lookup main prio "$RULE_PRIO"
        echo "$(date): 策略路由规则已添加: to $ET_CIDR lookup main prio $RULE_PRIO" >> "$LOG_FILE"
    fi

    # 如果存在对端路由（通过 easytier-cli route list 获取），
    # 也需要为这些网段添加路由规则
    # 这里用通配方式：让所有从 TUN 设备源地址出来的流量也走 main 表
    ET_IP=$(get_et_ip)
    if [ -n "$ET_IP" ]; then
        $IP rule del from "$ET_IP" lookup main prio "$RULE_PRIO" 2>/dev/null
        $IP rule add from "$ET_IP" lookup main prio "$RULE_PRIO"
        echo "$(date): 策略路由规则已添加: from $ET_IP lookup main prio $RULE_PRIO" >> "$LOG_FILE"
    fi

    # 3. 设置 NAT MASQUERADE
    $IPT -t nat -N ET_PRO 2>/dev/null
    $IPT -t nat -F ET_PRO

    # MASQUERADE: 从 TUN 设备出来的流量做源地址转换
    $IPT -t nat -A ET_PRO -o "$DEV" -j MASQUERADE

    $IPT -t nat -C POSTROUTING -j ET_PRO 2>/dev/null || \
        $IPT -t nat -I POSTROUTING 1 -j ET_PRO

    # 4. FORWARD 链：允许 TUN 设备转发
    $IPT -N ET_FWD 2>/dev/null
    $IPT -F ET_FWD

    $IPT -A ET_FWD -m state --state ESTABLISHED,RELATED -j ACCEPT
    $IPT -A ET_FWD -o "$DEV" -j ACCEPT
    $IPT -A ET_FWD -i "$DEV" -j ACCEPT

    $IPT -C FORWARD -j ET_FWD 2>/dev/null || \
        $IPT -I FORWARD 1 -j ET_FWD

    # 5. 热点/USB 共享转发
    HOT_IFACE=$($IP link | awk -F': ' '/(^| )(swlan[[:alnum:]_]*|softap[[:alnum:]_]*|p2p-wlan[[:alnum:]_]*|ap[[:alnum:]_]*)/{print $2; exit}' | cut -d'@' -f1)
    USB_IFACE=$($IP link | awk -F': ' '/(^| )(usb[[:alnum:]_]*|rndis[[:alnum:]_]*|eth[[:alnum:]_]*)/{print $2; exit}' | cut -d'@' -f1)

    if [ -n "$HOT_IFACE" ]; then
        HOT_CIDR=$($IP -4 addr show dev "$HOT_IFACE" 2>/dev/null | awk '/inet /{print $2; exit}')
        if [ -n "$HOT_CIDR" ]; then
            $IPT -t nat -A ET_PRO -s "$HOT_CIDR" -o "$DEV" -j MASQUERADE
            echo "$(date): 热点转发: $HOT_IFACE $HOT_CIDR -> $DEV" >> "$LOG_FILE"
        fi
    fi
    if [ -n "$USB_IFACE" ]; then
        USB_CIDR=$($IP -4 addr show dev "$USB_IFACE" 2>/dev/null | awk '/inet /{print $2; exit}')
        if [ -n "$USB_CIDR" ]; then
            $IPT -t nat -A ET_PRO -s "$USB_CIDR" -o "$DEV" -j MASQUERADE
            echo "$(date): USB转发: $USB_IFACE $USB_CIDR -> $DEV" >> "$LOG_FILE"
        fi
    fi

    echo "$(date): 路由规则已启用" >> "$LOG_FILE"
}

disable_routing() {
    echo "$(date): 禁用路由规则" >> "$LOG_FILE"

    # 清理策略路由规则
    ET_CIDR=$(get_et_cidr)
    ET_IP=$(get_et_ip)
    [ -n "$ET_CIDR" ] && $IP rule del to "$ET_CIDR" lookup main prio "$RULE_PRIO" 2>/dev/null
    [ -n "$ET_IP" ] && $IP rule del from "$ET_IP" lookup main prio "$RULE_PRIO" 2>/dev/null

    # 清理 NAT 链
    $IPT -t nat -D POSTROUTING -j ET_PRO 2>/dev/null
    $IPT -t nat -F ET_PRO 2>/dev/null
    $IPT -t nat -X ET_PRO 2>/dev/null

    # 清理 FORWARD 链
    $IPT -D FORWARD -j ET_FWD 2>/dev/null
    $IPT -F ET_FWD 2>/dev/null
    $IPT -X ET_FWD 2>/dev/null

    echo "$(date): 路由规则已清理" >> "$LOG_FILE"
}

case "$1" in
    enable)
        enable_routing
        ;;
    disable)
        disable_routing
        ;;
    *)
        echo "Usage: $0 {enable|disable}"
        exit 1
        ;;
esac