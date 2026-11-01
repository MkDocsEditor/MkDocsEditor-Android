package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * Answers every HTTP request with the status of [status] and remembers the requests. The JDK
 * HTTP server is not on the classpath of Android modules.
 */
internal class TestHttpServer {
    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))

    val port: Int get() = socket.localPort

    @Volatile
    var status = 200

    /** path and Authorization header of every request */
    val received = CopyOnWriteArrayList<Pair<String, String?>>()

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val connection = try {
                    socket.accept()
                } catch (_: IOException) {
                    break
                }
                connection.use {
                    val reader = it.getInputStream().bufferedReader()
                    val path = reader.readLine().split(" ")[1]
                    val headers = generateSequence { reader.readLine() }.takeWhile(String::isNotEmpty).toList()
                    val authorization = headers.firstOrNull { line -> line.startsWith("Authorization:", ignoreCase = true) }
                        ?.substringAfter(":")?.trim()
                    received += path to authorization
                    it.getOutputStream().write("HTTP/1.1 $status X\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                }
            }
        }
    }

    fun stop() = socket.close()
}
