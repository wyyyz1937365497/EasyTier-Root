#!/data/adb/magisk/busybox sh
#
# EasyTier Pro - Action 按钮
# 在 Magisk Manager 中点击模块的"操作"按钮时触发
# 用于切换暂停/恢复
#
MODDIR=${0%/*}
CONFIG_DIR="/data/adb/easytier_pro"
MODULE_PROP="${MODDIR}/module.prop"
STATUS_FILE="${CONFIG_DIR}/status"

# 读取当前状态
CURRENT=$(cat "${STATUS_FILE}" 2>/dev/null)

if [ -f "${CONFIG_DIR}/paused" ]; then
    # 当前暂停 -> 恢复
    rm -f "${CONFIG_DIR}/paused"
    echo "已恢复 EasyTier Pro 服务"
    echo "[Action] $(date): 服务已恢复" >> "${CONFIG_DIR}/logs/action.log"
else
    # 当前运行 -> 暂停
    touch "${CONFIG_DIR}/paused"
    echo "已暂停 EasyTier Pro 服务"
    echo "[Action] $(date): 服务已暂停" >> "${CONFIG_DIR}/logs/action.log"
fi