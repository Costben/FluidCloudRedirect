#!/usr/bin/env bash
#
# 非增量覆盖安装：永远推完整包并核对实际落点，避免落在 incremental-fs 上导致 LSPosed 解析异常。
#
# 用法
#   scripts/install-module.sh <apk> [package]
#   scripts/install-module.sh --check <package>

set -euo pipefail

ADB_BIN="${ADB:-adb}"
AAPT2_BIN="${AAPT2:-aapt2}"

die() {
  printf '错误：%s\n' "$*" >&2
  exit 1
}

resolve_serial() {
  if [ -n "${SERIAL:-}" ]; then
    printf '%s\n' "$SERIAL"
    return
  fi
  local count
  count=$("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device"' | wc -l | tr -d ' ')
  [ "$count" = "1" ] || die "需要 SERIAL：adb devices 当前识别到 $count 台设备"
  "$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }'
}

package_of_apk() {
  "$AAPT2_BIN" dump packagename "$1" 2>/dev/null | tr -d '\r' | head -1
}

code_path_of() {
  "$ADB_BIN" -s "$SERIAL" shell "pm path $1" 2>/dev/null \
    | tr -d '\r' | sed -n 's/^package://p' | head -1
}

on_incremental_fs() {
  local path="$1" target
  for target in $(
    "$ADB_BIN" -s "$SERIAL" shell "su -c 'cat /proc/mounts'" 2>/dev/null \
      | awk '$3 == "incremental-fs" { print $2 }'
  ); do
    case "$path" in
      "$target" | "$target"/*) return 0 ;;
    esac
  done
  return 1
}

MODE=install
if [ "${1:-}" = "--check" ]; then
  MODE=check
  shift
fi

[ $# -ge 1 ] || die "用法: $0 <apk> [package] | --check <package>"

if [ "$MODE" = check ]; then
  APK=""
  PKG="$1"
else
  APK="$1"
  PKG="${2:-}"
  [ -f "$APK" ] || die "找不到 APK：$APK"
fi

SERIAL="$(resolve_serial)"
printf '设备：%s\n' "$SERIAL"

if [ "$MODE" = install ]; then
  [ -n "$PKG" ] || PKG="$(package_of_apk "$APK")"
  [ -n "$PKG" ] || die "无法从 APK 解析包名"
  printf '包名：%s\n' "$PKG"
  printf '安装：%s（--no-incremental）\n' "$APK"
  "$ADB_BIN" -s "$SERIAL" install -r --no-incremental "$APK"
fi

report_path() {
  local path
  path="$(code_path_of "$PKG")"
  [ -n "$path" ] || die "设备上找不到包 $PKG"
  printf '落点：%s\n' "$path"
  if on_incremental_fs "$path"; then
    return 1
  fi
  return 0
}

if ! report_path; then
  if [ "$MODE" = install ]; then
    printf '✗ 落在增量文件系统上，重试完整安装\n'
    "$ADB_BIN" -s "$SERIAL" install -r --no-incremental "$APK"
    report_path || die "$PKG 仍挂在 incremental-fs 上"
  else
    die "$PKG 挂在 incremental-fs 上，请用本脚本重新完整安装"
  fi
fi

printf '✓ %s 落在普通存储上\n' "$PKG"
