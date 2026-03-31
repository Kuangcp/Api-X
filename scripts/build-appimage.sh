#!/usr/bin/env bash
# 在 Linux 上从 Compose Desktop 的 createDistributable 产物打包为 AppImage。
# 依赖：bash、coreutils、findutils、grep、sed、curl 或 wget（可选，用于下载 appimagetool）、
#       squashfs-tools（appimagetool 会调用 mksquashfs）、openssl（校验可选）。
#
# 用法：
#   ./scripts/build-appimage.sh
#   APPIMAGE_TOOL=/path/to/appimagetool ./scripts/build-appimage.sh
#
# 首次需安装 appimagetool（Arch: yay -S appimagetool 或从 GitHub 下载 AppImageKit 发布包）。

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

die() { echo "错误: $*" >&2; exit 1; }

# 从 build.gradle.kts 读取 packageName / packageVersion（缺省与 Gradle 一致）
PACKAGE_NAME="$(grep -oP 'packageName\s*=\s*"\K[^"]+' build.gradle.kts 2>/dev/null || true)"
[[ -n "${PACKAGE_NAME:-}" ]] || PACKAGE_NAME="api-x"
PACKAGE_VERSION="$(grep -oP 'packageVersion\s*=\s*"\K[^"]+' build.gradle.kts 2>/dev/null || true)"
[[ -n "${PACKAGE_VERSION:-}" ]] || PACKAGE_VERSION="1.0.0"

GRADLE="${GRADLE:-./gradlew}"
[[ -x "$GRADLE" ]] || die "未找到可执行的 $GRADLE"

echo "==> Gradle: createDistributable"
"$GRADLE" createDistributable

resolve_app_bundle() {
  local pkg="$1"
  local candidates=(
    "$ROOT/build/compose/binaries/main/app/$pkg"
    "$ROOT/build/compose/binaries/main-release/app/$pkg"
  )
  local d
  for d in "${candidates[@]}"; do
    if [[ -d "$d/bin" ]]; then
      if [[ -x "$d/bin/$pkg" ]]; then
        echo "$d"
        return 0
      fi
      # 若 bin 下名称与 packageName 不一致，取 bin 内第一个可执行文件所在目录
      local first
      first=$(find "$d/bin" -maxdepth 1 -type f -executable 2>/dev/null | head -1 || true)
      if [[ -n "$first" ]]; then
        echo "$d"
        return 0
      fi
    fi
  done
  local found
  found=$(find "$ROOT/build/compose/binaries" -type f -path "*/app/*/bin/$pkg" 2>/dev/null | head -1 || true)
  if [[ -n "$found" ]]; then
    dirname "$(dirname "$found")"
    return 0
  fi
  found=$(find "$ROOT/build/compose/binaries" -type d -path "*/app/*" 2>/dev/null | while read -r dir; do
    [[ -d "$dir/bin" ]] && echo "$dir" && break
  done)
  if [[ -n "${found:-}" ]]; then
    echo "$found"
    return 0
  fi
  return 1
}

APP_BUNDLE="$(resolve_app_bundle "$PACKAGE_NAME")" || die "未找到 Compose 应用目录（请先成功执行 createDistributable）。预期类似: build/compose/binaries/main/app/$PACKAGE_NAME"

LAUNCHER_NAME="$PACKAGE_NAME"
if [[ ! -x "$APP_BUNDLE/bin/$LAUNCHER_NAME" ]]; then
  LAUNCHER_NAME="$(basename "$(find "$APP_BUNDLE/bin" -maxdepth 1 -type f -executable 2>/dev/null | head -1)")"
  [[ -n "$LAUNCHER_NAME" ]] || die "无法在 $APP_BUNDLE/bin 中找到启动脚本"
fi

echo "==> 应用目录: $APP_BUNDLE"
echo "==> 启动器: bin/$LAUNCHER_NAME"

APPDIR_ROOT="$ROOT/build/appimage/AppDir"
rm -rf "$APPDIR_ROOT"
mkdir -p "$APPDIR_ROOT/usr/lib/$PACKAGE_NAME"
mkdir -p "$APPDIR_ROOT/usr/bin"
mkdir -p "$APPDIR_ROOT/usr/share/applications"
mkdir -p "$APPDIR_ROOT/usr/share/icons/hicolor/256x256/apps"

echo "==> 复制应用文件到 AppDir"
cp -a "$APP_BUNDLE/." "$APPDIR_ROOT/usr/lib/$PACKAGE_NAME/"

ln -sf "../lib/$PACKAGE_NAME/bin/$LAUNCHER_NAME" "$APPDIR_ROOT/usr/bin/$LAUNCHER_NAME"

# 最小占位图标（1x1 PNG），避免 appimagetool 因缺图标报错；可替换为 assets/icon.png
ICON_TARGET="$APPDIR_ROOT/usr/share/icons/hicolor/256x256/apps/${PACKAGE_NAME}.png"
if [[ -f "$ROOT/assets/icon.png" ]]; then
  cp -a "$ROOT/assets/icon.png" "$ICON_TARGET"
else
  printf '%s' 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==' | base64 -d > "$ICON_TARGET"
fi

DESKTOP_ID="${PACKAGE_NAME}.desktop"
cat > "$APPDIR_ROOT/usr/share/applications/$DESKTOP_ID" <<EOF
[Desktop Entry]
Type=Application
Name=$PACKAGE_NAME
Comment=HTTP client (Compose Desktop)
Exec=$LAUNCHER_NAME %u
Icon=$PACKAGE_NAME
Terminal=false
Categories=Development;Network;
StartupWMClass=$PACKAGE_NAME
EOF

# AppImage 约定：AppDir 根目录也放一份 desktop，便于部分工具识别
cp "$APPDIR_ROOT/usr/share/applications/$DESKTOP_ID" "$APPDIR_ROOT/$DESKTOP_ID"

cat > "$APPDIR_ROOT/AppRun" <<EOF
#!/bin/sh
SELF="\$(readlink -f "\$0" 2>/dev/null || readlink "\$0" 2>/dev/null || echo "\$0")"
HERE="\$(dirname "\$SELF")"
export PATH="\$HERE/usr/bin:\$PATH"
exec "\$HERE/usr/bin/$LAUNCHER_NAME" "\$@"
EOF
chmod +x "$APPDIR_ROOT/AppRun"

detect_arch() {
  case "$(uname -m)" in
    x86_64) echo x86_64 ;;
    aarch64|arm64) echo aarch64 ;;
    *) die "未支持的架构: $(uname -m)（AppImage 需 x86_64 或 aarch64）" ;;
  esac
}

ARCH="$(detect_arch)"

ensure_appimagetool() {
  if [[ -n "${APPIMAGE_TOOL:-}" && -x "${APPIMAGE_TOOL}" ]]; then
    echo "$APPIMAGE_TOOL"
    return 0
  fi
  if command -v appimagetool >/dev/null 2>&1; then
    command -v appimagetool
    return 0
  fi
  local cache="$ROOT/build/appimage/tools"
  # 使用官方 continuous 构建；也可自行下载后通过 APPIMAGE_TOOL 指定路径
  local name="appimagetool-${ARCH}.AppImage"
  local url="https://github.com/AppImage/AppImageKit/releases/download/continuous/${name}"
  mkdir -p "$cache"
  if [[ ! -x "$cache/$name" ]]; then
    echo "==> 下载 appimagetool: $url"
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL -o "$cache/$name" "$url"
    elif command -v wget >/dev/null 2>&1; then
      wget -q -O "$cache/$name" "$url"
    else
      die "未找到 appimagetool，且无法使用 curl/wget 下载。请安装 appimagetool 或设置 APPIMAGE_TOOL=/path/to/appimagetool"
    fi
    chmod +x "$cache/$name"
  fi
  echo "$cache/$name"
}

APPIMAGE_TOOL_BIN="$(ensure_appimagetool)"
OUT_NAME="${PACKAGE_NAME}-${PACKAGE_VERSION}-${ARCH}.AppImage"
OUT_PATH="$ROOT/build/appimage/$OUT_NAME"

echo "==> 生成 AppImage: $OUT_PATH"
mkdir -p "$(dirname "$OUT_PATH")"
rm -f "$OUT_PATH"

# -n: 非交互
export ARCH
"$APPIMAGE_TOOL_BIN" -n "$APPDIR_ROOT" "$OUT_PATH"

echo "==> 完成: $OUT_PATH"
ls -lh "$OUT_PATH"
