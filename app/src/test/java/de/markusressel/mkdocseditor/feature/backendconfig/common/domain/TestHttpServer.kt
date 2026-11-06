package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import java.io.IOException
import java.io.InputStream
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

    /** the JSON of every answer, empty for none */
    @Volatile
    var body = ""

    /** how long the server works on a request after it has read it, like a slow move */
    @Volatile
    var delayMs = 0L

    /** path and Authorization header of every request */
    val received = CopyOnWriteArrayList<Pair<String, String?>>()

    /** the size of the body of every request */
    val receivedBodySizes = CopyOnWriteArrayList<Int>()

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val connection = try {
                    socket.accept()
                } catch (_: IOException) {
                    break
                }
                // a slow answer must not hold up the next request
                thread(isDaemon = true) {
                    try {
                        connection.use {
                            val input = it.getInputStream()
                            val path = input.readLine().split(" ")[1]
                            val headers = generateSequence { input.readLine() }.takeWhile(String::isNotEmpty).toList()
                            val authorization = headers.header("Authorization")
                            received += path to authorization
                            val length = headers.header("Content-Length")?.toInt() ?: 0
                            receivedBodySizes += input.readNBytes(length).size
                            Thread.sleep(delayMs)
                            val content = body.toByteArray()
                            it.getOutputStream().write(
                                ("HTTP/1.1 $status X\r\nContent-Type: application/json\r\nContent-Length: ${content.size}\r\n" +
                                    "Connection: close\r\n\r\n").toByteArray() + content
                            )
                        }
                    } catch (_: IOException) {
                        // the client gave up
                    } catch (_: InterruptedException) {
                    }
                }
            }
        }
    }

    fun stop() = socket.close()

    private fun List<String>.header(name: String): String? =
        firstOrNull { line -> line.startsWith("$name:", ignoreCase = true) }?.substringAfter(":")?.trim()

    /** a line of the request head; the body may hold bytes that are no text */
    private fun InputStream.readLine(): String {
        val line = StringBuilder()
        while (true) {
            val byte = read()
            if (byte == -1 || byte == '\n'.code) break
            if (byte != '\r'.code) line.append(byte.toChar())
        }
        return line.toString()
    }
}
