package com.save.screenmirror

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Utilidades para obtener la IP local del telefono dentro de la red Wi-Fi.
 */
object NetworkUtils {

    fun getLocalIp(): String? {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val candidates = ArrayList<String>()

            for (nif in ifaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
                            return ip // las mas tipicas de una LAN domestica
                        }
                        candidates.add(ip)
                    }
                }
            }
            return candidates.firstOrNull()
        } catch (_: Exception) {
            return null
        }
    }
}
