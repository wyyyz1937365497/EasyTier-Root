SKIPUNZIP=1

ui_print "=================================="
ui_print "  EasyTier Pro Root Module"
ui_print "  v2.6.4 - 系统级透明代理"
ui_print "=================================="

# 检测架构
ARCH=$(getprop ro.product.cpu.abi)
ui_print "  架构: $ARCH"
ui_print "  SDK:  $API"

# 解压模块文件
unzip -qo "${ZIPFILE}" -x 'META-INF/*' -d "$MODPATH"

# 设置权限
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm_recursive "$MODPATH/bin" 0 0 0755 0755
set_perm "$MODPATH/scripts/easytier_core.sh" 0 0 0700
set_perm "$MODPATH/scripts/iptables_setup.sh" 0 0 0700
set_perm "$MODPATH/service.sh" 0 0 0700
set_perm "$MODPATH/action.sh" 0 0 0700
set_perm "$MODPATH/uninstall.sh" 0 0 0700

# 创建配置目录（如不存在）
CONFIG_DIR="/data/adb/easytier_pro"
if [ ! -d "$CONFIG_DIR" ]; then
  mkdir -p "$CONFIG_DIR"
  cp -f "$MODPATH/config/config.toml" "$CONFIG_DIR/config.toml"
  ui_print "  配置文件已初始化到 $CONFIG_DIR/config.toml"
else
  ui_print "  配置目录已存在，保留现有配置"
fi

# 创建日志目录
mkdir -p "$CONFIG_DIR/logs"

ui_print "  配置文件: /data/adb/easytier_pro/config.toml"
ui_print "  日志目录: /data/adb/easytier_pro/logs/"
ui_print "  "
ui_print "  安装完成后请重启设备生效"
ui_print "  使用 EasyTier Pro App 管理服务"
ui_print "=================================="

rm -f "$MODPATH/customize.sh"