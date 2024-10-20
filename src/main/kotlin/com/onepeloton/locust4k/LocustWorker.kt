package com.onepeloton.locust4k

import com.onepeloton.locust4k.LocustWorkerState.NOT_READY
import com.onepeloton.locust4k.LocustWorkerState.READY
import com.onepeloton.locust4k.LocustWorkerState.RUNNING
import com.onepeloton.locust4k.LocustWorkerState.SHUTDOWN
import com.onepeloton.locust4k.LocustWorkerState.SPAWNING
import com.onepeloton.locust4k.LocustWorkerState.STOPPED
import com.onepeloton.locust4k.LocustWorkerState.WAITING
import com.onepeloton.locust4k.logging.LoggingConstants.Companion.DECREASE_USERS_LOG_PARAM
import com.onepeloton.locust4k.logging.LoggingConstants.Companion.HEARTBEAT_STATE_LOG_PARAM
import com.onepeloton.locust4k.logging.LoggingConstants.Companion.INCREASE_USERS_LOG_PARAM
import com.onepeloton.locust4k.logging.LoggingConstants.Companion.INDEX_LOG_PARAM
import com.onepeloton.locust4k.logging.LoggingConstants.Companion.MESSAGE_TYPE_LOG_PARAM
import com.onepeloton.locust4k.logging.LoggingConstants.Companion.NODE_ID_LOG_PARAM
import com.onepeloton.locust4k.logging.LoggingConstants.Companion.STATE_LOG_PARAM
import com.onepeloton.locust4k.logging.LoggingConstants.Companion.TASK_NAME_LOG_PARAM
import com.onepeloton.locust4k.messages.LocustClient
import com.onepeloton.locust4k.messages.LocustMessageType
import com.onepeloton.locust4k.messages.LocustMessageType.ACK
import com.onepeloton.locust4k.messages.LocustMessageType.CLIENT_READY
import com.onepeloton.locust4k.messages.LocustMessageType.CLIENT_STOPPED
import com.onepeloton.locust4k.messages.LocustMessageType.HEARTBEAT
import com.onepeloton.locust4k.messages.LocustMessageType.QUIT
import com.onepeloton.locust4k.messages.LocustMessageType.RECONNECT
import com.onepeloton.locust4k.messages.LocustMessageType.SPAWN
import com.onepeloton.locust4k.messages.LocustMessageType.SPAWNING_COMPLETE
import com.onepeloton.locust4k.messages.LocustMessageType.STOP
import com.onepeloton.locust4k.messages.Message
import com.onepeloton.locust4k.stats.LocustStats
import com.sun.management.OperatingSystemMXBean
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withTimeout
import org.zeromq.ZMQException
import java.lang.System.currentTimeMillis
import java.lang.management.ManagementFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.system.exitProcess

/**
 * Locust Worker that orchestrates communication with the Master node. At a minimum, provide the [host] and [port]
 * of the Locust Master node, and one or more [LocustTask] implementations to [tasks]. Invoke [startup] to establish
 * the connection to Locust Master and [shutdown] when the application is closing.
 *
 * If you provide a [uniqueNodeId] it will appear in the Locust UI and Master logs. If you're using Kubernetes, you
 * would typically use the `HOSTNAME` environment variable. A random UUID will be generated if not provided.
 *
 * Other arguments provide fine-grained configuration of resources and timeouts.
 *
 * - [taskThreads] is the thread pool size for the coroutine context used to run [LocustTask]s
 * - [messageProducerBufferSize] is the number of outbound messages that can be buffered (to Locust Master)
 * - [messageConsumerBufferSize] is the number of inbound messages that can be buffered (from Locust Master)
 * - [successOrFailureBufferSize] is the number of [LocustTaskReporter] success or failure events that can be buffered
 * - [messageConsumerBackoffMillis] amount of time to sleep after polling for inbound messages (from Locust Master)
 * - [ackTimeoutMillis] timeout for receiving an ACK message from Locust Master after connecting
 * - [heartbeatFromMasterTimeoutMillis] timeout for receiving a heartbeat message from Locust Master
 * - [heartbeatMillis] frequency of outbound heartbeat messages, so that Locust Master knows this worker is alive
 * - [statsReportMillis] frequency of outbound statistics-report messages from [LocustTaskReporter] aggregations
 * - [blockingIoContext] coroutine context used for potentially blocking operations ([Dispatchers.IO] by default)
 */
@OptIn(DelicateCoroutinesApi::class)
@ExperimentalCoroutinesApi
class LocustWorker(
    private val host: String,
    private val port: Int,
    private val tasks: List<LocustTask>,
    private val uniqueNodeId: String? = null,
    private val taskThreads: Int = 2,
    private val messageProducerBufferSize: Int = 1024,
    private val messageConsumerBufferSize: Int = 1024,
    private val successOrFailureBufferSize: Int = 8192,
    private val messageConsumerBackoffMillis: Long = 10,
    private val ackTimeoutMillis: Long = 5_000,
    private val heartbeatFromMasterTimeoutMillis: Long = 5_000,
    private val heartbeatMillis: Long = 1_000,
    private val statsReportMillis: Long = 3_000,
    private val blockingIoContext: CoroutineContext = Dispatchers.IO,
) {
    private val logger = KotlinLogging.logger {}

    // each worker needs a unique ID
    private val nodeId = uniqueNodeId ?: UUID.randomUUID().toString().replace("-", "")

    private val client = LocustClient(host, port, nodeId)

    private val controlContext = newSingleThreadContext("locust-worker-control")
    private val taskContext = newFixedThreadPoolContext(taskThreads, "locust-worker-tasks")

    // since controlContext is single-threaded, these variables do not need to be thread-safe
    private var lastHeartbeatFromMasterTimeMillis: Long = 0
    private val perUserTaskJobs = ArrayList<Map<LocustTask, Job>>()

    @Volatile
    private var receiveMessageJob: Job? = null

    @Volatile
    private var sendMessageJob: Job? = null

    private val workerState = AtomicReference(NOT_READY)

    private val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean

    init {
        if (tasks.isEmpty()) {
            throw IllegalArgumentException("`tasks` cannot be empty")
        }
        if (nodeId.isBlank()) {
            throw IllegalArgumentException("`uniqueNodeId` cannot be an empty-string")
        }
    }

    private fun loggerPayloadOf(vararg pairs: Pair<String, Any?>): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        if (pairs.isNotEmpty()) {
            map.putAll(pairs)
        }
        map[NODE_ID_LOG_PARAM] = nodeId
        map[STATE_LOG_PARAM] = workerState.get().lowerCase
        return map
    }

    private suspend fun checkForHeartbeatFromMasterTimeout(): Boolean {
        if (currentTimeMillis() - lastHeartbeatFromMasterTimeMillis > heartbeatFromMasterTimeoutMillis) {
            logger.atInfo {
                message = "Controller heartbeat timeout"
                payload = loggerPayloadOf()
            }
            quitAndExit()
            return true
        }
        return false
    }

    suspend fun startup(): Unit =
        coroutineScope {
            logger.atInfo {
                message = "Starting Locust Worker"
                payload = loggerPayloadOf()
            }

            if (workerState.get() != NOT_READY) {
                throw IllegalStateException("Unexpected state: ${workerState.get()}")
            }

            if (client.connect().not()) {
                throw IllegalStateException("Unable to connect to Locust")
            }
            logger.atInfo {
                message = "Connected to Locust"
                payload = loggerPayloadOf()
            }
            lastHeartbeatFromMasterTimeMillis = currentTimeMillis()

            val receiveMessageChannel = Channel<Message>(capacity = messageConsumerBufferSize)
            receiveMessageJob =
                launch(context = blockingIoContext) {
                    try {
                        while (isActive) {
                            val message = client.receiveMessageAsync()
                            if (message != null) {
                                receiveMessageChannel.send(message)
                            } else {
                                delay(messageConsumerBackoffMillis)
                            }
                        }
                    } catch (e: ZMQException) {
                        if (workerState.get() == SHUTDOWN) {
                            logger.atDebug {
                                message = "Receive Message consumer ZMQ socket closed"
                                payload = loggerPayloadOf()
                            }
                        } else {
                            logger.atWarn {
                                message = "Receive Message consumer ZMQ error"
                                payload = loggerPayloadOf()
                                cause = e
                            }
                        }
                    } catch (e: CancellationException) {
                        if (workerState.get() == SHUTDOWN) {
                            logger.atDebug {
                                message = "Receive Message consumer closed"
                                payload = loggerPayloadOf()
                            }
                        } else {
                            logger.atWarn {
                                message = "Receive Message consumer cancelled error"
                                payload = loggerPayloadOf()
                                cause = e
                            }
                        }
                    } catch (e: Exception) {
                        logger.atError {
                            message = "Receive Message consumer error"
                            payload = loggerPayloadOf()
                            cause = e
                        }
                    }
                }

            val sendMessageChannel = Channel<Message>(capacity = messageProducerBufferSize)
            sendMessageJob =
                launch(context = controlContext) {
                    try {
                        for (msg in sendMessageChannel) {
                            if (checkForHeartbeatFromMasterTimeout()) {
                                return@launch
                            }
                            if (client.sendMessageAsync(msg).not()) {
                                logger.atWarn {
                                    message = "Unable to send ZMQ message"
                                    payload = loggerPayloadOf(MESSAGE_TYPE_LOG_PARAM to msg.type.lowerCase)
                                }
                            }
                        }
                    } catch (e: ZMQException) {
                        logger.atWarn {
                            message = "Send Message producer ZMQ error"
                            payload = loggerPayloadOf()
                            cause = e
                        }
                    } catch (e: CancellationException) {
                        if (workerState.get() == SHUTDOWN) {
                            logger.atDebug {
                                message = "Send Message producer closed"
                                payload = loggerPayloadOf()
                            }
                        } else {
                            logger.atWarn {
                                message = "Send Message producer cancelled error"
                                payload = loggerPayloadOf()
                                cause = e
                            }
                        }
                    } catch (e: Exception) {
                        logger.atError {
                            message = "Send Message producer error"
                            payload = loggerPayloadOf()
                            cause = e
                        }
                    }
                }

            sendMessageChannel.send(Message(CLIENT_READY, nodeId))
            if (workerState.compareAndSet(NOT_READY, READY).not()) {
                throw IllegalStateException("Unexpected state: ${workerState.get()}")
            }

            val stats = LocustStats(nodeId, workerState, controlContext, sendMessageChannel, successOrFailureBufferSize)

            launch(context = controlContext) {
                receiveAck(receiveMessageChannel)
                coroutineScope {
                    launch(context = controlContext) {
                        while (isActive) {
                            try {
                                delay(heartbeatMillis)

                                val stateName =
                                    when (val state = workerState.get()) {
                                        READY, WAITING -> READY.lowerCase
                                        SPAWNING, RUNNING, STOPPED -> state.lowerCase
                                        SHUTDOWN -> break
                                        else -> throw IllegalStateException("Unexpected state: ${workerState.get()}")
                                    }
                                val cpuUsage = (osBean.cpuLoad * 100).roundToInt()
                                val data =
                                    mapOf<String, Any>(
                                        "state" to stateName,
                                        "current_cpu_usage" to cpuUsage,
                                        "current_memory_usage" to osBean.freeMemorySize,
                                    )
                                sendMessageChannel.send(Message(HEARTBEAT, nodeId, data))
                                logger.atTrace {
                                    message = "Sent heartbeat"
                                    payload = loggerPayloadOf(HEARTBEAT_STATE_LOG_PARAM to stateName)
                                }
                            } catch (e: Exception) {
                                logger.atError {
                                    message = "Error from heartbeat loop"
                                    payload = loggerPayloadOf()
                                    cause = e
                                }
                            }
                        }
                    }
                    stats.start(statsReportMillis)
                    eventLoop(stats, sendMessageChannel, receiveMessageChannel)
                }
            }
        }

    private suspend fun receiveAck(receiveMessageChannel: ReceiveChannel<Message>) =
        coroutineScope {
            withTimeout(ackTimeoutMillis * 1_000) {
                val receivedMessage = receiveMessageChannel.receive()
                if (receivedMessage.type != ACK) {
                    throw IllegalStateException("Expected ack, but got: ${receivedMessage.type}")
                }
                logger.atInfo {
                    message = "Ack message from controller"
                    payload = loggerPayloadOf(INDEX_LOG_PARAM to receivedMessage.data!!["index"])
                }
                lastHeartbeatFromMasterTimeMillis = currentTimeMillis()
            }
            if (workerState.compareAndSet(READY, WAITING).not()) {
                throw IllegalStateException("Unexpected state: ${workerState.get()}")
            }
        }

    private suspend fun quitAndExit(stats: LocustStats? = null) =
        coroutineScope {
            try {
                workerState.set(STOPPED)

                perUserTaskJobs.forEach { user -> user.forEach { it.value.cancel() } }
                perUserTaskJobs.clear()

                // send last stats message before exiting
                stats?.stop()
            } finally {
                launch(context = blockingIoContext) {
                    exitProcess(0)
                }
            }
        }

    private suspend fun eventLoop(
        stats: LocustStats,
        sendMessageChannel: SendChannel<Message>,
        receiveMessageChannel: ReceiveChannel<Message>,
    ) = coroutineScope {
        launch(context = controlContext) {
            for (msg in receiveMessageChannel) {
                lastHeartbeatFromMasterTimeMillis = currentTimeMillis()
                when (msg.type) {
                    QUIT -> {
                        logger.atInfo {
                            message = "Quit message from controller"
                            payload = loggerPayloadOf()
                        }
                        quitAndExit(stats)
                    }

                    RECONNECT -> {
                        logger.atInfo {
                            message = "Reconnect message from controller"
                            payload = loggerPayloadOf()
                        }
                        if (client.connect(reconnect = true).not()) {
                            throw IllegalStateException("Unable to reconnect to Locust at $host:$port")
                        }
                    }

                    SPAWN -> {
                        logger.atInfo {
                            message = "Spawn message from controller"
                            payload = loggerPayloadOf()
                        }
                        spawn(stats, msg, sendMessageChannel)
                    }

                    SPAWNING_COMPLETE ->
                        logger.atDebug {
                            message = "Spawning Complete message from controller"
                            payload = loggerPayloadOf()
                        }

                    STOP -> {
                        logger.atInfo {
                            message = "Stop message from controller"
                            payload = loggerPayloadOf()
                        }
                        stop(sendMessageChannel)
                    }

                    ACK -> {
                        logger.atInfo {
                            message = "Ack message from controller"
                            payload = loggerPayloadOf(INDEX_LOG_PARAM to msg.data!!["index"])
                        }
                        if (workerState.compareAndSet(READY, WAITING).not()) {
                            throw IllegalStateException("Unexpected state: ${workerState.get()}")
                        }
                    }

                    HEARTBEAT ->
                        logger.atTrace {
                            message = "Heartbeat message from controller"
                            payload = loggerPayloadOf()
                        }

                    else ->
                        logger.atWarn {
                            message = "Unexpected message type received"
                            payload = loggerPayloadOf(MESSAGE_TYPE_LOG_PARAM to msg.type.lowerCase)
                        }
                }
            }
        }
    }

    private suspend fun spawn(
        stats: LocustStats,
        msg: Message,
        sendMessageChannel: SendChannel<Message>,
    ) = coroutineScope {
        if (workerState.getAndSet(SPAWNING) == WAITING) {
            // only reset stats once
            stats.reset()
        }
        sendMessageChannel.send(Message(LocustMessageType.SPAWNING, nodeId))

        @Suppress("UNCHECKED_CAST")
        val userClassesCountMap = msg.data!!["user_classes_count"] as Map<String, Int>
        val numUsers: Int = userClassesCountMap.values.fold(0) { acc, next -> acc + next }

        // cleanup any empty task-sets (e.g., all failed to start)
        val listIterator = perUserTaskJobs.listIterator()
        while (listIterator.hasNext()) {
            if (listIterator.next().isEmpty()) listIterator.remove()
        }

        // determine how many users to add or remove
        val numUsersDiff = numUsers - perUserTaskJobs.size
        val numUsersToCreate = max(0, numUsersDiff)
        val numUsersToDelete = if (numUsersDiff < 0) abs(numUsersDiff) else 0

        if (numUsersToCreate == 0 && numUsersToDelete == 0) {
            logger.atDebug {
                message = "Spawn resulted in no change in user-units"
                payload = loggerPayloadOf()
            }
            return@coroutineScope
        } else if (numUsersToCreate != 0) {
            logger.atDebug {
                message = "Spawn increasing user-units"
                payload =
                    loggerPayloadOf(INCREASE_USERS_LOG_PARAM to numUsersToCreate)
            }
        } else {
            logger.atDebug {
                message = "Spawn decreasing user-units"
                payload =
                    loggerPayloadOf(DECREASE_USERS_LOG_PARAM to numUsersToDelete)
            }
        }

        for (userNum in 1..numUsersToDelete) {
            perUserTaskJobs.removeLast().forEach { if (it.value.isActive) it.value.cancel() }
        }

        // spawn worker tasks with numUsers (can be more spawns during test run)
        for (userNum in 1..numUsersToCreate) {
            val taskJobs = HashMap<LocustTask, Job>(tasks.size)
            perUserTaskJobs.add(taskJobs)

            for (task in tasks) {
                val taskInstance = task.instance()
                taskJobs[taskInstance] =
                    GlobalScope.launch(context = taskContext) {
                        try {
                            try {
                                taskInstance.beforeExecuteLoop(taskContext)
                            } catch (e: CancellationException) {
                                logger.atDebug {
                                    message = "Task cancelled (beforeExecuteLoop)"
                                    payload = loggerPayloadOf(TASK_NAME_LOG_PARAM to taskInstance.name())
                                }
                                return@launch
                            } catch (e: Exception) {
                                logger.atError {
                                    message = "Task beforeExecuteLoop exception caught"
                                    payload = loggerPayloadOf(TASK_NAME_LOG_PARAM to taskInstance.name())
                                    cause = e
                                }
                                stats.failure(0, e.message ?: "", taskInstance.name(), "beforeExecuteLoop")
                                return@launch
                            }
                            try {
                                while (isActive) {
                                    taskInstance.execute(stats, taskContext)
                                }
                                if (isActive.not()) {
                                    logger.atDebug {
                                        message = "Task cancelled (inactive)"
                                        payload = loggerPayloadOf(TASK_NAME_LOG_PARAM to taskInstance.name())
                                    }
                                    return@launch
                                }
                            } catch (e: CancellationException) {
                                logger.atDebug {
                                    message = "Task cancelled"
                                    payload = loggerPayloadOf(TASK_NAME_LOG_PARAM to taskInstance.name())
                                }
                                return@launch
                            } catch (e: Exception) {
                                logger.atError {
                                    message = "Task execute exception caught"
                                    payload = loggerPayloadOf(TASK_NAME_LOG_PARAM to taskInstance.name())
                                    cause = e
                                }
                                stats.failure(0, e.message ?: "", taskInstance.name(), "unknown")
                            } finally {
                                try {
                                    taskInstance.afterExecuteLoop(taskContext)
                                } catch (e: CancellationException) {
                                    logger.atDebug {
                                        message = "Task cancelled (afterExecuteLoop)"
                                        payload = loggerPayloadOf(TASK_NAME_LOG_PARAM to taskInstance.name())
                                    }
                                } catch (e: Exception) {
                                    logger.atWarn {
                                        message = "Task afterExecuteLoop exception caught"
                                        payload = loggerPayloadOf(TASK_NAME_LOG_PARAM to taskInstance.name())
                                        cause = e
                                    }
                                }
                            }
                        } finally {
                            // remove ourselves from taskJobs
                            taskJobs.remove(taskInstance)
                        }
                    }
            }
        }

        stats.updateUserCounts(numUsers, userClassesCountMap)

        val messageData =
            mapOf(
                "count" to numUsers,
                "user_classes_count" to userClassesCountMap,
            )
        sendMessageChannel.send(Message(SPAWNING_COMPLETE, nodeId, messageData))
        workerState.set(RUNNING)
    }

    private suspend fun stop(sendMessageChannel: SendChannel<Message>) {
        workerState.set(STOPPED)
        sendMessageChannel.send(Message(CLIENT_STOPPED, nodeId))

        perUserTaskJobs.forEach { user -> user.forEach { it.value.cancel() } }
        perUserTaskJobs.clear()

        workerState.set(READY)
        sendMessageChannel.send(Message(CLIENT_READY, nodeId))
    }

    fun shutdown() {
        if (workerState.get() != SHUTDOWN) {
            workerState.set(SHUTDOWN)
            logger.atInfo {
                message = "Shutting down Locust Worker"
                payload = loggerPayloadOf()
            }
            receiveMessageJob?.cancel()
            sendMessageJob?.cancel()
            taskContext.close()
            controlContext.close()
            client.close()
        }
    }
}
