#!/system/bin/sh
#
# EasyTier Pro - 核心守护进程
# 负责：启动/停止 easytier-core，管理 TUN 设备，设置路由
#
SCRIPT_DIR=${0%/*}
MODDIR="${SCRIPT_DIR%/*}"
BIN_DIR="${MODDIR}/bin"
CONFIG_DIR="/data/adb/easytier_pro"
CONFIG_FILE="${CONFIG_DIR}/config.toml"
COMMAND_ARGS="${CONFIG_DIR}/command_args"
LOG_FILE="${CONFIG_DIR}/logs/core.log"
MODULE_PROP="${MODDIR}/module.prop"
PID_FILE="${CONFIG_DIR}/easytier.pid"
STATUS_FILE="${CONFIG_DIR}/status"
ET_CORE="${BIN_DIR}/easytier-core"
ET_CLI="${BIN_DIR}/easytier-cli"

# 获取设备名
BRAND=$(getprop ro.product.brand | tr ' ' '-')
MODEL=$(getprop ro.product.model | tr ' ' '-')
DEVICE_HOSTNAME="${BRAND}-${MODEL}"

update_status() {
    echo "$1" > "${STATUS_FILE}"
    if [ -f "${MODULE_PROP}" ]; then
        sed -i "s#^description=.*#description=EasyTier Pro | $1#" "${MODULE_PROP}"
    fi
}

init_tun() {
    if [ ! -e /dev/net/tun ]; then
        mkdir -p /dev/net 2>/dev/null
        ln -s /dev/tun /dev/net/tun 2>/dev/null
    fi
}

is_running() {
    if [ -f "${PID_FILE}" ]; then
        PID=$(cat "${PID_FILE}" 2>/dev/null)
        [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null && return 0
    fi
    pgrep -f "${ET_CORE}" >/dev/null 2>&1 && return 0
    return 1
}

start_core() {
    if is_running; then
        update_status "运行中"
        return 0
    fi

    init_tun

    # 选择启动方式
    if [ -f "${COMMAND_ARGS}" ]; then
        # 命令行参数模式
        CMD_CONTENT=$(tr '\r\n' ' ' < "${COMMAND_ARGS}")
        if echo "${CMD_CONTENT}" | grep -q "\-\-hostname"; then
            FINAL_ARGS="${CMD_CONTENT}"
        else
            FINAL_ARGS="${CMD_CONTENT} --hostname ${DEVICE_HOSTNAME}"
        fi
        update_status "启动中(参数模式)..."
        TZ=Asia/Shanghai "${ET_CORE}" ${FINAL_ARGS} > "${LOG_FILE}" 2>&1 &
    elif [ -f "${CONFIG_FILE}" ]; then
        # 配置文件模式
        if grep -q "^[[:space:]]*hostname[[:space:]]*=" "${CONFIG_FILE}"; then
            update_status "启动中(配置模式)..."
            TZ=Asia/Shanghai "${ET_CORE}" -c "${CONFIG_FILE}" > "${LOG_FILE}" 2>&1 &
        else
            update_status "启动中(配置模式)..."
            TZ=Asia/Shanghai "${ET_CORE}" -c "${CONFIG_FILE}" --hostname "${DEVICE_HOSTNAME}" > "${LOG_FILE}" 2>&1 &
        fi
    else
        update_status "缺少配置文件"
        return 1
    fi

    echo $! > "${PID_FILE}"
    sleep 3

    if is_running; then
        update_status "运行中"
        # 等待 RPC 就绪后设置路由
        sleep 2
        "${MODDIR}/scripts/iptables_setup.sh" enable 2>/dev/null
        return 0
    else
        update_status "启动失败"
        rm -f "${PID_FILE}"
        return 1
    fi
}

stop_core() {
    update_status "停止中..."
    "${MODDIR}/scripts/iptables_setup.sh" disable 2>/dev/null
    if [ -f "${PID_FILE}" ]; then
        PID=$(cat "${PID_FILE}" 2>/dev/null)
        [ -n "$PID" ] && kill "$PID" 2>/dev/null
    fi
    pkill -f "${ET_CORE}" 2>/dev/null
    sleep 1
    rm -f "${PID_FILE}"
    update_status "已停止"
}

# 主守护循环
while true; do
    # 检查是否被禁用
    if [ -f "${MODDIR}/disable" ]; then
        if is_running; then
            stop_core
        fi
        update_status "模块已禁用"
        sleep 10
        continue
    fi

    # 检查暂停标志（由 App 设置）
    if [ -f "${CONFIG_DIR}/paused" ]; then
        if is_running; then
            stop_core
        fi
        update_status "已暂停"
        sleep 5
        continue
    fi

    # 如果没在运行就启动
    if ! is_running; then
        start_core
    else
        update_status "运行中"
    fi

    sleep 10
done