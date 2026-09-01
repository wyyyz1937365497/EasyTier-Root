#!/bin/bash
#
# 打包 EasyTier Pro Root 模块 ZIP
#
set -euo pipefail

TARGET="${1:-sukisu}"
MODULE_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$MODULE_DIR")"

case "$TARGET" in
  sukisu)
    OUTPUT="$ROOT_DIR/easytier-pro-sukisu-v2.6.4.zip"
    ;;
  magisk)
    OUTPUT="$ROOT_DIR/easytier-pro-magisk-v2.6.4.zip"
    ;;
  *)
    echo "用法: $0 [sukisu|magisk]" >&2
    exit 2
    ;;
esac

cd "$MODULE_DIR"

# 确保权限正确
chmod 755 bin/* scripts/*.sh service.sh action.sh customize.sh uninstall.sh 2>/dev/null
chmod 644 module.prop config/config.toml META-INF/com/google/android/updater-script 2>/dev/null
chmod 755 META-INF/com/google/android/update-binary 2>/dev/null

# 仅打包模块运行文件；SukiSU/KernelSU 不需要 recovery META-INF。
rm -f "$OUTPUT"
PACKAGE_FILES=(action.sh bin config customize.sh module.prop scripts service.sh uninstall.sh)
[ "$TARGET" = "magisk" ] && PACKAGE_FILES+=(META-INF)

if command -v zip >/dev/null 2>&1; then
  zip -r "$OUTPUT" "${PACKAGE_FILES[@]}"
elif command -v python3 >/dev/null 2>&1; then
  python3 -m zipfile -c "$OUTPUT" "${PACKAGE_FILES[@]}"
elif command -v bsdtar >/dev/null 2>&1; then
  bsdtar -a -cf "$OUTPUT" "${PACKAGE_FILES[@]}"
else
  echo "错误: 需要 zip、python3 或 bsdtar" >&2
  exit 1
fi

echo ""
echo "==================================="
echo "  ${TARGET} 模块已打包:"
echo "  $OUTPUT"
echo "  大小: $(du -h "$OUTPUT" | cut -f1)"
echo "==================================="