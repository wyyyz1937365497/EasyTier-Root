#!/bin/bash
#
# 打包 Magisk 模块 ZIP
#
set -e

WORKSPACE="/data/user/0/com.ai.assistance.operit/files/workspace/186b7012-3670-4ed7-b2b5-7c02aff8a8a6"
MAGISK_DIR="$WORKSPACE/magisk"
OUTPUT="$WORKSPACE/easytier-pro-magisk-v2.6.4.zip"

cd "$MAGISK_DIR"

# 确保权限正确
chmod 755 bin/* scripts/*.sh service.sh action.sh customize.sh uninstall.sh 2>/dev/null
chmod 644 module.prop config/config.toml META-INF/com/google/android/updater-script 2>/dev/null
chmod 755 META-INF/com/google/android/update-binary 2>/dev/null

# 打包 ZIP（排除不需要的文件）
rm -f "$OUTPUT"
zip -r "$OUTPUT" . \
    -x "*.git*" \
    -x "*.DS_Store" \
    -x "build.sh" \
    -x "log.log"

echo ""
echo "==================================="
echo "  Magisk 模块已打包:"
echo "  $OUTPUT"
echo "  大小: $(du -h "$OUTPUT" | cut -f1)"
echo "==================================="