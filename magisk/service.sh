#!/data/adb/magisk/busybox sh
MODDIR=${0%/*}
CONFIG_DIR="/data/adb/easytier_pro"
LOG_FILE="${CONFIG_DIR}/logs/service.log"
MODULE_PROP="${MODDIR}/module.prop"

chmod 755 "${MODDIR}/bin/"* 2>/dev/null
chmod 755 "${MODDIR}/scripts/"* 2>/dev/null

# 等待系统启动完成
while [ "$(getprop sys.boot_completed)" != "1" ]; do
  sleep 5s
done

# 防止系统挂起
echo "PowerManagerService.noSuspend" > /sys/power/wake_lock

# 启动核心守护
"${MODDIR}/scripts/easytier_core.sh" &