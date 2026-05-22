package com.eaglenet.vpn

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    // ── UI refs ──────────────────────────────────────────────────────────────
    private lateinit var tvStatus:       TextView
    private lateinit var tvStatusSub:    TextView
    private lateinit var viewIndicator:  View
    private lateinit var btnConnect:     Button
    private lateinit var spinnerType:    Spinner
    private lateinit var etHost:         TextInputEditText
    private lateinit var etPort:         TextInputEditText
    private lateinit var etUser:         TextInputEditText
    private lateinit var etPass:         TextInputEditText
    private lateinit var switchDns:      SwitchMaterial
    private lateinit var layoutDns:      View
    private lateinit var etDns:          TextInputEditText
    private lateinit var cardProxy:      MaterialCardView
    private lateinit var cardDns:        MaterialCardView

    private var connected = false

    // ── VPN permission launcher ───────────────────────────────────────────────
    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) doConnect()
            else showToast("VPN permission denied")
        }

    // ── Notification permission (Android 13+) ─────────────────────────────────
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignore */ }

    // ── Broadcast receiver ────────────────────────────────────────────────────
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                EagleVpnService.BROADCAST_CONNECTED    -> setUiConnected(true)
                EagleVpnService.BROADCAST_DISCONNECTED -> setUiConnected(false)
                EagleVpnService.BROADCAST_ERROR -> {
                    val msg = intent.getStringExtra(EagleVpnService.EXTRA_ERROR_MSG) ?: "Unknown error"
                    setUiConnected(false)
                    showToast("Error: $msg")
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupSpinner()
        setupDnsToggle()
        setupConnectButton()
        loadSavedConfig()

        // Ask for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(EagleVpnService.BROADCAST_CONNECTED)
            addAction(EagleVpnService.BROADCAST_DISCONNECTED)
            addAction(EagleVpnService.BROADCAST_ERROR)
        }
        registerReceiver(receiver, filter)

        // Reflect real engine state in case activity was recreated while running.
        try {
            val running = vpn.Vpn.running()
            if (running != connected) setUiConnected(running)
        } catch (e: Exception) { /* gomobile not loaded yet */ }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
        saveCurrentConfig()
    }

    // ─── View wiring ──────────────────────────────────────────────────────────

    private fun bindViews() {
        tvStatus      = findViewById(R.id.tv_status)
        tvStatusSub   = findViewById(R.id.tv_status_sub)
        viewIndicator = findViewById(R.id.view_indicator)
        btnConnect    = findViewById(R.id.btn_connect)
        spinnerType   = findViewById(R.id.spinner_type)
        etHost        = findViewById(R.id.et_host)
        etPort        = findViewById(R.id.et_port)
        etUser        = findViewById(R.id.et_user)
        etPass        = findViewById(R.id.et_pass)
        switchDns     = findViewById(R.id.switch_dns)
        layoutDns     = findViewById(R.id.layout_dns)
        etDns         = findViewById(R.id.et_dns)
        cardProxy     = findViewById(R.id.card_proxy)
        cardDns       = findViewById(R.id.card_dns)
    }

    private fun setupSpinner() {
        val types = arrayOf("SOCKS5", "HTTP")
        spinnerType.adapter =
            ArrayAdapter(this, R.layout.spinner_item, types).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
    }

    private fun setupDnsToggle() {
        layoutDns.visibility = if (switchDns.isChecked) View.VISIBLE else View.GONE
        switchDns.setOnCheckedChangeListener { _, checked ->
            layoutDns.visibility = if (checked) View.VISIBLE else View.GONE
        }
    }

    private fun setupConnectButton() {
        btnConnect.setOnClickListener {
            hideKeyboard()
            if (connected) doDisconnect() else requestVpnPermission()
        }
    }

    // ─── VPN permission ───────────────────────────────────────────────────────

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent)
        else doConnect()
    }

    // ─── Connect / Disconnect ─────────────────────────────────────────────────

    private fun doConnect() {
        val host     = etHost.text.toString().trim()
        val portStr  = etPort.text.toString().trim()
        val user     = etUser.text.toString().trim()
        val pass     = etPass.text.toString().trim()
        val dnsOn    = switchDns.isChecked
        val dns      = etDns.text.toString().trim()
        val proxyType = if (spinnerType.selectedItemPosition == 0) "socks5" else "http"
        val port     = portStr.toIntOrNull()

        // Validate
        if (host.isEmpty()) { etHost.error = "Required"; return }
        if (port == null || port !in 1..65535) { etPort.error = "1–65535"; return }
        if (dnsOn && dns.isEmpty()) { etDns.error = "Required"; return }

        saveCurrentConfig()

        val intent = Intent(this, EagleVpnService::class.java).apply {
            action = EagleVpnService.ACTION_START
            putExtra(EagleVpnService.EXTRA_PROXY_TYPE,  proxyType)
            putExtra(EagleVpnService.EXTRA_PROXY_HOST,  host)
            putExtra(EagleVpnService.EXTRA_PROXY_PORT,  port)
            putExtra(EagleVpnService.EXTRA_USERNAME,    user)
            putExtra(EagleVpnService.EXTRA_PASSWORD,    pass)
            putExtra(EagleVpnService.EXTRA_DNS_ENABLED, dnsOn)
            putExtra(EagleVpnService.EXTRA_DNS_SERVER,  if (dnsOn) dns else "")
        }
        ContextCompat.startForegroundService(this, intent)

        // Optimistic UI — receiver will confirm
        tvStatus.text    = "Connecting…"
        tvStatusSub.text = "$proxyType · $host:$port"
        btnConnect.isEnabled = false
        viewIndicator.setBackgroundResource(R.drawable.indicator_yellow)
    }

    private fun doDisconnect() {
        startService(Intent(this, EagleVpnService::class.java).apply {
            action = EagleVpnService.ACTION_STOP
        })
        btnConnect.isEnabled = false
    }

    // ─── UI state ─────────────────────────────────────────────────────────────

    private fun setUiConnected(on: Boolean) {
        connected = on
        runOnUiThread {
            btnConnect.isEnabled = true
            cardProxy.isEnabled  = !on
            cardDns.isEnabled    = !on
            setFieldsEnabled(!on)

            if (on) {
                tvStatus.text    = "Connected"
                tvStatusSub.text = buildSubtitle()
                viewIndicator.setBackgroundResource(R.drawable.indicator_green)
                btnConnect.text  = "Disconnect"
                btnConnect.setBackgroundColor(getColor(R.color.btn_disconnect))
            } else {
                tvStatus.text    = "Disconnected"
                tvStatusSub.text = "Tap Connect to start"
                viewIndicator.setBackgroundResource(R.drawable.indicator_red)
                btnConnect.text  = "Connect"
                btnConnect.setBackgroundColor(getColor(R.color.btn_connect))
            }
        }
    }

    private fun setFieldsEnabled(enabled: Boolean) {
        spinnerType.isEnabled = enabled
        etHost.isEnabled      = enabled
        etPort.isEnabled      = enabled
        etUser.isEnabled      = enabled
        etPass.isEnabled      = enabled
        switchDns.isEnabled   = enabled
        etDns.isEnabled       = enabled
    }

    private fun buildSubtitle(): String {
        val type = if (spinnerType.selectedItemPosition == 0) "SOCKS5" else "HTTP"
        val host = etHost.text.toString().trim()
        val port = etPort.text.toString().trim()
        return "$type · $host:$port"
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    private fun saveCurrentConfig() {
        val cfg = VpnConfig(
            proxyType  = if (spinnerType.selectedItemPosition == 0) "socks5" else "http",
            proxyHost  = etHost.text.toString().trim(),
            proxyPort  = etPort.text.toString().trim().toIntOrNull() ?: 1080,
            username   = etUser.text.toString().trim(),
            password   = etPass.text.toString().trim(),
            dnsEnabled = switchDns.isChecked,
            dnsServer  = etDns.text.toString().trim().ifEmpty { "223.5.5.5" },
        )
        Prefs.save(this, cfg)
    }

    private fun loadSavedConfig() {
        val cfg = Prefs.load(this)
        spinnerType.setSelection(if (cfg.proxyType == "socks5") 0 else 1)
        etHost.setText(cfg.proxyHost)
        etPort.setText(cfg.proxyPort.toString())
        etUser.setText(cfg.username)
        etPass.setText(cfg.password)
        switchDns.isChecked = cfg.dnsEnabled
        etDns.setText(cfg.dnsServer)
        layoutDns.visibility = if (cfg.dnsEnabled) View.VISIBLE else View.GONE
    }

    // ─── Misc helpers ─────────────────────────────────────────────────────────

    private fun showToast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java)
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }
}
