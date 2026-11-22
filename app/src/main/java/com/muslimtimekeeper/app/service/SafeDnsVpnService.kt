package com.muslimtimekeeper.app.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

/**
 * Dual-mode Local VPN Service (No Root Required):
 * 1. SAFE_DNS mode: Routes DNS through Cloudflare Family (1.1.1.3) & CleanBrowsing Family to block adult/malware sites.
 * 2. BLOCK_ALL mode: Sinkholes all IPv4 traffic (0.0.0.0/0) when the device's GB data quota is exhausted!
 */
class SafeDnsVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var currentMode: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "SAFE_DNS"
        if (action == "STOP_VPN") {
            closeVpn()
            stopSelf()
            return START_NOT_STICKY
        }

        if (vpnInterface != null && currentMode == action) {
            return START_STICKY
        }

        closeVpn()
        currentMode = action
        try {
            val builder = Builder()
                .setSession(if (action == "BLOCK_ALL") "Muslim Time Keeper - Data Quota Reached" else "Muslim Time Keeper - Safe DNS")
                .addAddress("10.111.222.1", 32)
                .addDnsServer("1.1.1.3")
                .addDnsServer("185.228.168.168")

            if (action == "BLOCK_ALL") {
                // Capture all IPv4 traffic into blackhole interface (blocks internet cleanly without root)
                builder.addRoute("0.0.0.0", 0)
            }
            vpnInterface = builder.establish()
        } catch (_: Exception) {
            vpnInterface = null
        }
        return START_STICKY
    }

    private fun closeVpn() {
        try { vpnInterface?.close() } catch (_: Exception) { }
        vpnInterface = null
        currentMode = ""
    }

    override fun onDestroy() {
        closeVpn()
        super.onDestroy()
    }
}
