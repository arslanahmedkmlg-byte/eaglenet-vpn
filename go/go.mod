module eaglenet.vpn/golib

go 1.21

require (
    // Pull in the latest tagged release of xjasonlyu/tun2socks/v2 as of mid‑2025.
    // Newer releases do not include a DNSHijack field in engine.Key, so the application
    // code must not depend on that field. See the official wiki for guidance on
    // hijacking DNS using external iptables rules【828494346506210†L233-L267】.
    github.com/xjasonlyu/tun2socks/v2 v2.6.0
    // Keep the gomobile version pinned so gomobile bind remains deterministic.
    golang.org/x/mobile v0.0.0-20240213143359-d1f7d3436075
)
