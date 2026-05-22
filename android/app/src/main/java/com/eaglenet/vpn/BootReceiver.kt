package com.eaglenet.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restores the last VPN session after a device reboot — only if the user
 * had it connected at shutdown (tracked via Prefs).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val cfg = Prefs.load(ctx)
        // Only auto-start if a proxy host has been configured.
        if (cfg.proxyHost.isBlank()) return

        val svcIntent = Intent(ctx, EagleVpnService::class.java).apply {
            action = EagleVpnService.ACTION_START
            putExtra(EagleVpnService.EXTRA_PROXY_TYPE,  cfg.proxyType)
            putExtra(EagleVpnService.EXTRA_PROXY_HOST,  cfg.proxyHost)
            putExtra(EagleVpnService.EXTRA_PROXY_PORT,  cfg.proxyPort)
            putExtra(EagleVpnService.EXTRA_USERNAME,    cfg.username)
            putExtra(EagleVpnService.EXTRA_PASSWORD,    cfg.password)
            putExtra(EagleVpnService.EXTRA_DNS_ENABLED, cfg.dnsEnabled)
            putExtra(EagleVpnService.EXTRA_DNS_SERVER,  cfg.dnsServer)
        }
        ContextCompat.startForegroundService(ctx, svcIntent)
    }
}
