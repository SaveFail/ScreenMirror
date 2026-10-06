package com.save.screenmirror.core

import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Descubrimiento alternativo por TCP: escanea la subred local buscando el
 * puerto HTTP de una emisora y confirma que responde la pagina de ScreenMirror.
 * Funciona aunque el router bloquee el broadcast UDP.
 */
object NetworkScanner {

    fun scan(port: Int = 8080, connectTimeoutMs: Int = 220): List<Discovery.Device> {
        val ip = NetworkUtils.getLocalIp() ?: return emptyList()
        val prefix = ip.substringBeforeLast('.', "")
        if (prefix.isEmpty()) return emptyList()

        val results = Collections.synchronizedList(ArrayList<Discovery.Device>())
        val pool = Executors.newFixedThreadPool(48)
        val latch = CountDownLatch(254)

        for (i in 1..254) {
            val host = "$prefix.$i"
            pool.execute {
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                        if (looksLikeEmitter(socket, host, connectTimeoutMs)) {
                            results.add(Discovery.Device(host, port))
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    latch.countDown()
                }
            }
        }

        try {
            latch.await(4000, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
        }
        pool.shutdownNow()
        return results.toList()
    }

    private fun looksLikeEmitter(socket: Socket, host: String, timeoutMs: Int): Boolean {
        return try {
            socket.soTimeout = timeoutMs
            val request = "GET / HTTP/1.0\r\nHost: $host\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(request.toByteArray())
            socket.getOutputStream().flush()
            val buf = ByteArray(768)
            val n = socket.getInputStream().read(buf)
            if (n <= 0) return false
            val body = String(buf, 0, n)
            body.contains("Pantalla en vivo") || body.contains("Transmision en vivo")
        } catch (_: Exception) {
            false
        }
    }
}
