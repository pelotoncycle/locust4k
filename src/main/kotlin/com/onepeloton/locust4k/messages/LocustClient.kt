package com.onepeloton.locust4k.messages

import io.github.oshai.kotlinlogging.KotlinLogging
import org.zeromq.SocketType
import org.zeromq.ZMQ
import org.zeromq.ZMQ.Socket
import zmq.ZMQ.ZMQ_DONTWAIT
import java.io.Closeable

class LocustClient(
    private val host: String,
    private val port: Int,
    private val nodeId: String
) : Closeable {

    private val logger = KotlinLogging.logger {}

    private val zmqContext = ZMQ.context(1)

    // NOTE: dealer sockets are not thread-safe
    private var zmqSocket: Socket = zmqContext.socket(SocketType.DEALER)

    fun connect(reconnect: Boolean = false): Boolean {
        if (reconnect) {
            logger.debug { "Reconnecting" }
            zmqSocket.close()
            zmqSocket = zmqContext.socket(SocketType.DEALER)
        } else {
            logger.debug { "Connecting" }
        }
        zmqSocket.identity = nodeId.toByteArray()
        zmqSocket.tcpKeepAlive = 1
        return try {
            zmqSocket.connect("tcp://$host:$port")
        } catch (e: Exception) {
            logger.error(e) { "Connection failed" }
            false
        }
    }

    fun sendMessageAsync(message: Message): Boolean {
        return zmqSocket.send(message.bytes, ZMQ_DONTWAIT)
    }

    fun receiveMessageAsync(): Message? {
        return zmqSocket.recv(ZMQ_DONTWAIT)?.let { bytes ->
            Message(bytes)
        }
    }

    fun receiveMessageBlocking(): Message? {
        return zmqSocket.recv()?.let { bytes ->
            Message(bytes)
        }
    }

    override fun close() {
        logger.debug { "Closing connection" }
        zmqContext.use { _ ->
            zmqSocket.close()
        }
    }
}
