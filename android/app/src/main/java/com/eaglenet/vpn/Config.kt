package com.eaglenet.vpn

import android.content.Context
import android.content.SharedPreferences

/**
 * Holds all user-configured proxy/DNS settings.
 */
data class VpnConfig(
    val proxyType: String  = "socks5",   // "socks5" | "http"
    val proxyHost: String  = "",
    val proxyPort: Int     = 1080,
    val username:  String  = "",
    val password:  String  = "",
    val dnsEnabled: Boolean = false,
    val dnsServer:  String  = "223.5.5.5",  // AliDNS default
)

object Prefs {
    private const val NAME = "eaglenet_prefs"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun save(ctx: Context, cfg: VpnConfig) {
        sp(ctx).edit().apply {
            putString("proxy_type",  cfg.proxyType)
            putString("proxy_host",  cfg.proxyHost)
            putInt   ("proxy_port",  cfg.proxyPort)
            putString("username",    cfg.username)
            putString("password",    cfg.password)
            putBoolean("dns_enabled", cfg.dnsEnabled)
            putString("dns_server",  cfg.dnsServer)
            apply()
        }
    }

    fun load(ctx: Context): VpnConfig {
        val sp = sp(ctx)
        return VpnConfig(
            proxyType  = sp.getString("proxy_type", "socks5") ?: "socks5",
            proxyHost  = sp.getString("proxy_host",  "") ?: "",
            proxyPort  = sp.getInt   ("proxy_port",  1080),
            username   = sp.getString("username",    "") ?: "",
            password   = sp.getString("password",    "") ?: "",
            dnsEnabled = sp.getBoolean("dns_enabled", false),
            dnsServer  = sp.getString("dns_server",  "223.5.5.5") ?: "223.5.5.5",
        )
    }
}
