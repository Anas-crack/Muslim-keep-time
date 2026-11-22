package com.muslimtimekeeper.app.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

/**
 * Local Family-Safe DNS VPN Service (Routes DNS through Cloudflare Family 1.1.1.3 / CleanBrowsing 185.228.168.168)
 * Blocks adult, explicit, and dangerous websites across all mobile browsers without root.
 */
class SafeDnsVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_VPN") {
            vpnInterface?.close()
            vpnInterface = null
            stopSelf()
            return START_NOT_STICKY
        }

        if (vpnInterface == null) {
            val builder = Builder()
                .setSession("Muslim Time Keeper Safe DNS")
                .addAddress("10.111.222.1", 32)
                .addDnsServer("1.1.1.3")
                .addDnsServer("185.228.168.168")
            vpnInterface = builder.establish()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }
}
