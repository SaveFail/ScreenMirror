package com.save.screenmirror.core

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

/**
 * Descubrimiento en la red local por UDP (peticion/respuesta).
 *  - La app EMISORA ejecuta [DiscoveryResponder] y responde a las peticiones.
 *  - La app RECEPTORA usa [DiscoveryClient] para encontrar emisoras en la LAN.
 */
object Discovery {
    const val PORT = 8888
    const val REQUEST = "SCREENMIRROR_DISCOVER"
    const val REPLY_PREFIX = "SCREENMIRROR_HERE"

    data class Device(val host: String, val port: Int) {
        val url: String get() = "http://$host:$port"
    }
}

/** Lado emisor: responde a las peticiones de descubrimiento. */
class DiscoveryResponder(private val httpPort: Int) {

    private var socket: DatagramSocket? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        thread = thread(name = "discovery-responder", isDaemon = true) {
            try {
                val s = DatagramSocket(null)
                s.reuseAddress = true
                s.broadcast = true
                s.bind(InetSocketAddress(Discovery.PORT))
                socket = s
                val buf = ByteArray(256)
                while (running) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        s.receive(packet)
                    } catch (_: Exception) {
                        if (running) continue else break
                    }
                    val msg = String(packet.data, 0, packet.length).trim()
                    if (msg == Discovery.REQUEST) {
                        val reply = "${Discovery.REPLY_PREFIX} $httpPort".toByteArray()
                        try {
                            s.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}

/** Lado receptor: busca emisoras en la red local. */
class DiscoveryClient {

    fun discover(timeoutMs: Long = 2500): List<Discovery.Device> {
        val found = LinkedHashMap<String, Discovery.Device>()
        var socket: DatagramSocket? = null
        try {
            val s = DatagramSocket()
            s.broadcast = true
            s.soTimeout = 400
            socket = s

            val req = Discovery.REQUEST.toByteArray()
            for (bcast in broadcastAddresses()) {
                try {
                    s.send(DatagramPacket(req, req.size, bcast, Discovery.PORT))
                } catch (_: Exception) {}
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(256)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    s.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: Exception) {
                    break
                }
                val msg = String(packet.data, 0, packet.length).trim()
                if (msg.startsWith(Discovery.REPLY_PREFIX)) {
                    val port = msg.substringAfter(' ', "").trim().toIntOrNull() ?: 8080
                    val host = packet.address.hostAddress ?: continue
                    found[host] = Discovery.Device(host, port)
                }
            }
        } catch (_: Exception) {
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
        return found.values.toList()
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val list = ArrayList<InetAddress>()
        try {
            list.add(InetAddress.getByName("255.255.255.255"))
            val ifaces = NetworkInterface.getNetworkInterfaces()
            for (nif in ifaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (ia in nif.interfaceAddresses) {
                    ia.broadcast?.let { list.add(it) }
                }
            }
        } catch (_: Exception) {
        }
        return list
    }
}
