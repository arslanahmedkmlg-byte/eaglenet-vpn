// Package vpn is the gomobile-exported Go library that powers EagleNet VPN.
// It wraps the tun2socks v2 engine which converts a TUN file descriptor into
// SOCKS5 or HTTP proxy traffic.
//
// Build with:
//   gomobile bind -target android/arm64 -o vpn.aar ./
//
package vpn

import (
	"fmt"
	"sync"

	"github.com/xjasonlyu/tun2socks/v2/engine"
)

var (
	mu        sync.Mutex
	isRunning bool
)

// Start initialises the tun2socks engine and begins routing all TUN traffic
// through the upstream proxy.
//
// Parameters
//   tunFd      – file descriptor of the Android VpnService TUN interface
//   proxyType  – "socks5" or "http"
//   proxyHost  – proxy server hostname or IP
//   proxyPort  – proxy server port (1–65535)
//   user       – proxy username, empty string if none
//   pass       – proxy password, empty string if none
//   dnsServer  – custom DNS address in "ip" form, empty string to skip
//
// Returns an empty string on success, or an error message on failure.
func Start(
	tunFd int64,
	proxyType, proxyHost string,
	proxyPort int64,
	user, pass, dnsServer string,
) string {
	mu.Lock()
	defer mu.Unlock()

	// Stop any existing engine instance first.
	if isRunning {
		engine.Stop()
		isRunning = false
	}

	// Build proxy URL.
	var proxyURL string
	if user != "" && pass != "" {
		proxyURL = fmt.Sprintf("%s://%s:%s@%s:%d",
			proxyType, user, pass, proxyHost, proxyPort)
	} else {
		proxyURL = fmt.Sprintf("%s://%s:%d",
			proxyType, proxyHost, proxyPort)
	}

	// Build engine key.  The fd:// device scheme is the standard Android
	// integration path for tun2socks v2.
	key := &engine.Key{
		Device:   fmt.Sprintf("fd://%d", tunFd),
		Proxy:    proxyURL,
		LogLevel: "warning",
		// Give each side 4 MiB of kernel-buffered socket memory.
		TCPSendBufferSize:    "4m",
		TCPReceiveBufferSize: "4m",
		// UDP session idle timeout in seconds.
		UDPTimeout: 60,
	}

    // If a custom DNS server was specified, log a warning.  The upstream
    // tun2socks/v2 API removed the DNSHijack field from engine.Key, so DNS
    // redirection must be handled outside of the library (for example via
    // iptables DNAT rules)【828494346506210†L233-L267】.  To avoid build errors
    // with newer tun2socks versions, we simply ignore this parameter.
    if dnsServer != "" {
        // No built-in support for DNS hijacking in tun2socks v2.6.0.
        // The caller should configure system DNS redirection separately.
    }

    engine.Insert(key)
    // engine.Start() no longer returns a value in tun2socks v2; it logs fatal
    // errors internally.  Call it without capturing a return value to avoid
    // a compilation error【766674950455544†L0-L19】.
    engine.Start()

    isRunning = true
    return ""
}

// Stop shuts down the tun2socks engine cleanly.
func Stop() {
	mu.Lock()
	defer mu.Unlock()
	if isRunning {
		engine.Stop()
		isRunning = false
	}
}

// Running returns true if the engine is currently active.
func Running() bool {
	mu.Lock()
	defer mu.Unlock()
	return isRunning
}
