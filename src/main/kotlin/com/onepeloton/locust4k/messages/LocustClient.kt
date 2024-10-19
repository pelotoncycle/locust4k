package com.onepeloton.locust4k.messages

import com.onepeloton.locust4k.logging.LoggingConstants.Companion.HOST_LOG_PARAM
import com.onepeloton.locust4k.logging.LoggingConstants.Companion.NODE_ID_LOG_PARAM
import com.onepeloton.locust4k.logging.LoggingConstants.Companion.PORT_LOG_PARAM
import io.github.oshai.kotlinlogging.KotlinLogging
import org.zeromq.SocketType
import org.zeromq.ZMQ
import org.zeromq.ZMQ.Socket
import zmq.ZMQ.ZMQ_DONTWAIT
import java.io.Closeable

class LocustClient(
    private val host: String,
    private val port: Int,
    private val nodeId: String,
) : Closeable {
    private val logger = KotlinLogging.logger {}

    private val loggerPayload: Map<String, Any?> =
        mapOf(
            NODE_ID_LOG_PARAM to nodeId,
            HOST_LOG_PARAM to host,
            PORT_LOG_PARAM to port,
        )

    private val zmqContext = ZMQ.context(1)

    // NOTE: dealer sockets are not thread-safe
    private var zmqSocket: Socket = zmqContext.socket(SocketType.DEALER)

    fun connect(reconnect: Boolean = false): Boolean {
        if (reconnect) {
            logger.atDebug {
                message = "Reconnecting"
                payload = loggerPayload
            }
            zmqSocket.close()
            zmqSocket = zmqContext.socket(SocketType.DEALER)
        } else {
            logger.atDebug {
                message = "Connecting"
                payload = loggerPayload
            }
        }
        zmqSocket.identity = nodeId.toByteArray()
        zmqSocket.tcpKeepAlive = 1
        return try {
            zmqSocket.connect("tcp://$host:$port")
        } catch (e: Exception) {
            logger.atError {
                message = "Connection failed"
                payload = loggerPayload
                cause = e
            }
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

    override fun close() {
        logger.atDebug {
            message = "Closing connection"
            payload = loggerPayload
        }
        zmqSocket.close()
    }
}
