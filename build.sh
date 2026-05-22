#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# EagleNet VPN — build script
#
# Prerequisites:
#   • Go  >= 1.22        (https://go.dev/dl/)
#   • gomobile           (go install golang.org/x/mobile/cmd/gomobile@latest)
#   • Android SDK        (set ANDROID_HOME or ANDROID_SDK_ROOT)
#   • Android NDK r26+   (set NDK_HOME, or let gomobile locate via SDK)
#   • JDK 17+
#   • Gradle wrapper (./gradlew) — first run auto-downloads
#
# Usage:
#   chmod +x build.sh
#   ./build.sh            # debug APK
#   ./build.sh release    # release APK (unsigned)
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GO_DIR="$SCRIPT_DIR/go"
AAR_OUT="$SCRIPT_DIR/android/app/libs/vpn.aar"
BUILD_TYPE="${1:-debug}"

echo "══════════════════════════════════════════"
echo "  EagleNet VPN build  [$BUILD_TYPE]"
echo "══════════════════════════════════════════"

# ── 1. Check tools ────────────────────────────────────────────────────────────
for cmd in go gomobile; do
    if ! command -v "$cmd" &>/dev/null; then
        echo "❌  '$cmd' not found in PATH"
        [[ "$cmd" == "gomobile" ]] && echo "    Install: go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init"
        exit 1
    fi
done

echo "✓  Go      : $(go version)"
echo "✓  gomobile : $(gomobile version 2>&1 | head -1)"

# ── 2. Initialise gomobile (idempotent) ───────────────────────────────────────
echo ""
echo "→  Initialising gomobile…"
gomobile init

# ── 3. Download Go dependencies ───────────────────────────────────────────────
echo ""
echo "→  Downloading Go modules…"
cd "$GO_DIR"
go mod tidy
go mod download

# ── 4. Build Go library → vpn.aar (arm64 only) ───────────────────────────────
echo ""
echo "→  Building Go AAR (android/arm64)…"
gomobile bind \
    -target  android/arm64 \
    -o       "$AAR_OUT" \
    -ldflags "-s -w" \
    -v \
    .

echo "✓  AAR written to: $AAR_OUT"

# ── 5. Build Android APK ─────────────────────────────────────────────────────
echo ""
echo "→  Building Android APK ($BUILD_TYPE)…"
cd "$SCRIPT_DIR/android"

if [[ ! -f gradlew ]]; then
    echo "❌  gradlew not found — run 'gradle wrapper' inside android/ first"
    exit 1
fi

chmod +x gradlew

if [[ "$BUILD_TYPE" == "release" ]]; then
    ./gradlew assembleRelease
    APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
else
    ./gradlew assembleDebug
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

echo ""
echo "══════════════════════════════════════════"
echo "  ✅  Build complete"
echo "  APK: $SCRIPT_DIR/android/$APK_PATH"
echo "══════════════════════════════════════════"
