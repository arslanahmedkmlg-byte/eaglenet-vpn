# EagleNet VPN

A proper Android VPN client (ARM64) that routes all device traffic through an
upstream **HTTP** or **SOCKS5** proxy using a kernel TUN interface — the same
mechanism WireGuard uses.  Custom DNS support included.

---

## Architecture

```
┌──────────────────── Android Device ─────────────────────────┐
│                                                              │
│  App traffic  →  VPN TUN (10.88.0.1/30)                     │
│                        │                                     │
│                 tun2socks (Go engine)                        │
│                        │                                     │
│           ┌────────────┴────────────┐                       │
│           │  gVisor TCP/IP stack    │                       │
│           └────────────┬────────────┘                       │
│                        │  TCP/UDP streams                    │
│                        ▼                                     │
│             Upstream proxy connection                        │
│             (excluded from VPN routes)                       │
│                        │                                     │
└────────────────────────┼─────────────────────────────────────┘
                         │ Internet
                  ┌──────┴──────┐
                  │  HTTP/SOCKS5│
                  │  proxy      │
                  └─────────────┘
```

| Component              | Role                                                   |
|------------------------|--------------------------------------------------------|
| `EagleVpnService.kt`   | Android `VpnService` — owns the TUN fd lifecycle       |
| `RouteUtils.kt`        | CIDR-inversion to exclude proxy IP from VPN routes     |
| `go/vpn.go`            | gomobile library wrapping tun2socks v2 engine          |
| tun2socks v2           | Converts raw TUN packets → SOCKS5 / HTTP proxy traffic |
| gVisor netstack        | Userspace TCP/IP stack inside tun2socks                |

### Why CIDR exclusion?

Android's `VpnService` intercepts **all** traffic including the connection to
the proxy server itself.  Without exclusion this creates an infinite loop.
`RouteUtils.routesExcluding(proxyIp)` generates the ~32 CIDR blocks that cover
`0.0.0.0/0` minus the single proxy IP, which are added via `addRoute()`.

If the proxy is specified as a **hostname** (not an IP) the app falls back to
routing everything — resolve the hostname to an IP first or add it manually.

---

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Go   | ≥ 1.22  | https://go.dev/dl/ |
| gomobile | latest | `go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init` |
| Android SDK | API 34 | Android Studio SDK Manager |
| Android NDK | r26+ | SDK Manager → NDK (Side by side) |
| JDK | 17+ | OpenJDK |

Set environment variables:
```bash
export ANDROID_HOME=$HOME/Android/Sdk
export NDK_HOME=$ANDROID_HOME/ndk/26.x.x
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

---

## Build

```bash
# Clone / unzip the project
cd eaglenet-vpn

# Make the build script executable
chmod +x build.sh

# Build debug APK  (arm64 only)
./build.sh

# Build release APK (unsigned)
./build.sh release
```

Output:
- Debug:   `android/app/build/outputs/apk/debug/app-debug.apk`
- Release: `android/app/build/outputs/apk/release/app-release-unsigned.apk`

Install:
```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

First run via Gradle Wrapper:
```bash
cd android
gradle wrapper --gradle-version 8.4   # only needed once
cd ..
./build.sh
```

---

## Features

- **HTTP proxy** — supports `CONNECT` tunnelling (HTTPS) + plain HTTP forwarding
- **SOCKS5 proxy** — full TCP support; DNS resolved on the proxy side (no leaks)
- **Custom DNS** — any IP; set in `VpnService.Builder.addDnsServer()`; goes
  through the VPN so DNS is also tunnelled
- **Auth** — optional username/password for both proxy types
- **Persistent config** — saved in `SharedPreferences`, loaded on next launch
- **Boot auto-start** — restores last session after reboot (if host configured)
- **Foreground service** — persistent notification with one-tap Disconnect
- **Kill switch** — all traffic blocked if VPN drops (Android default behaviour)

---

## Usage

1. Open EagleNet
2. Select **SOCKS5** or **HTTP**
3. Enter proxy **Host** and **Port**
4. Optionally enter **Username / Password**
5. Toggle **Custom DNS** and enter your DNS server IP if desired
6. Tap **Connect** — grant the VPN permission prompt
7. Status turns green and a persistent notification appears
8. Tap **Disconnect** or the notification action to stop

---

## File Structure

```
eaglenet-vpn/
├── build.sh                          ← build everything
├── go/
│   ├── go.mod
│   └── vpn.go                        ← gomobile library (tun2socks wrapper)
└── android/
    ├── settings.gradle
    ├── build.gradle
    ├── gradle.properties
    └── app/
        ├── build.gradle
        ├── libs/                     ← vpn.aar placed here by build.sh
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/com/eaglenet/vpn/
            │   ├── MainActivity.kt
            │   ├── EagleVpnService.kt
            │   ├── RouteUtils.kt
            │   ├── Config.kt
            │   └── BootReceiver.kt
            └── res/
                ├── layout/activity_main.xml
                ├── values/colors.xml
                ├── values/strings.xml
                ├── values/themes.xml
                └── drawable/
```

---

## Notes

- **gomobile bind** compiles the entire Go module (including tun2socks + gVisor)
  into a single `.aar`.  First build takes 5–15 min; subsequent builds are cached.
- tun2socks v2 uses **gVisor's userspace netstack** — no kernel modules required.
- MTU is set to **1500**.  If you see fragmentation on mobile data, try 1400.
- UDP is forwarded if your proxy supports it (SOCKS5 UDP ASSOCIATE).  Most
  HTTP proxies do not support UDP — UDP traffic will silently fail.
