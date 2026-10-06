package com.save.screenmirror.core

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * Servidor HTTP minimo (sin dependencias).
 *   /         -> pagina web para ver la pantalla
 *   /stream   -> video MJPEG
 */
class StreamServer(private val port: Int) {

    private class Client(val socket: Socket, val out: OutputStream) {
        fun close() {
            try { out.close() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private val clients = CopyOnWriteArrayList<Client>()
    private val boundary = "screenmirrorframe"

    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    fun start() {
        if (running) return
        running = true
        serverSocket = ServerSocket(port).apply { reuseAddress = true }
        acceptThread = thread(name = "http-accept", isDaemon = true) {
            while (running) {
                val socket = try {
                    serverSocket!!.accept()
                } catch (_: IOException) {
                    if (running) continue else break
                } catch (_: Exception) {
                    break
                }
                thread(name = "http-client", isDaemon = true) { handle(socket) }
            }
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        for (c in clients) c.close()
        clients.clear()
    }

    fun broadcast(jpeg: ByteArray) {
        if (clients.isEmpty()) return
        val head = "--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n".toByteArray()
        val tail = "\r\n".toByteArray()
        val it = clients.iterator()
        while (it.hasNext()) {
            val c = it.next()
            try {
                synchronized(c) {
                    c.out.write(head)
                    c.out.write(jpeg)
                    c.out.write(tail)
                    c.out.flush()
                }
            } catch (_: Exception) {
                it.remove()
                c.close()
            }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            val input = BufferedInputStream(socket.getInputStream())
            val requestLine = readLine(input)
            if (requestLine == null) {
                socket.close()
                return
            }
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
            }
            val path = requestLine.split(" ").getOrNull(1) ?: "/"
            when {
                path == "/" || path.startsWith("/index") -> sendHtml(socket)
                path.startsWith("/stream") -> addClient(socket)
                else -> sendSimple(socket, "404 Not Found", "text/plain; charset=utf-8", "No encontrado".toByteArray())
            }
        } catch (_: Exception) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun addClient(socket: Socket) {
        val out = socket.getOutputStream()
        val header = (
            "HTTP/1.0 200 OK\r\n" +
                "Age: 0\r\n" +
                "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                "Pragma: no-cache\r\n" +
                "Connection: close\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=$boundary\r\n\r\n"
            ).toByteArray()
        out.write(header)
        out.flush()
        clients.add(Client(socket, out))
    }

    private fun sendHtml(socket: Socket) {
        val html = """
            <!doctype html>
            <html lang="es">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
              <title>Pantalla en vivo</title>
              <style>
                html, body { margin:0; height:100%; background:#000; overflow:hidden; }
                body { display:flex; align-items:center; justify-content:center; }
                img { max-width:100%; max-height:100%; object-fit:contain; }
                #hint { position:fixed; bottom:8px; left:0; right:0; text-align:center;
                        color:#666; font:12px sans-serif; }
              </style>
            </head>
            <body>
              <img id="v" src="/stream" alt="Conectando con el transmisor...">
              <div id="hint">Transmision en vivo (solo ver)</div>
              <script>
                var img = document.getElementById('v');
                img.onerror = function () {
                  setTimeout(function () { img.src = '/stream?t=' + Date.now(); }, 2000);
                };
              </script>
            </body>
            </html>
        """.trimIndent().toByteArray()
        sendSimple(socket, "200 OK", "text/html; charset=utf-8", html)
    }

    private fun sendSimple(socket: Socket, status: String, contentType: String, body: ByteArray) {
        val header = (
            "HTTP/1.0 $status\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
            ).toByteArray()
        val out = socket.getOutputStream()
        out.write(header)
        out.write(body)
        out.flush()
        try { socket.close() } catch (_: Exception) {}
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var b = input.read()
        if (b == -1) return null
        while (b != -1) {
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
            b = input.read()
        }
        return sb.toString()
    }
}
