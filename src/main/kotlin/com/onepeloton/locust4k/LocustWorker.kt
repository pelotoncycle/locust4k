package com.onepeloton.locust4k

import com.onepeloton.locust4k.LocustWorkerState.NOT_READY
import com.onepeloton.locust4k.LocustWorkerState.READY
import com.onepeloton.locust4k.LocustWorkerState.RUNNING
import com.onepeloton.locust4k.LocustWorkerState.SHUTDOWN
import com.onepeloton.locust4k.LocustWorkerState.SPAWNING
import com.onepeloton.locust4k.LocustWorkerState.STOPPED
import com.onepeloton.locust4k.LocustWorkerState.WAITING
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
import java.lang.management.ManagementFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.system.exitProcess

@OptIn(DelicateCoroutinesApi::class)
@ExperimentalCoroutinesApi
class LocustWorker(
    private val host: String,
    private val port: Int,
    private val tasks: List<LocustTask>,
    taskThreads: Int = 2,
    uniqueNodeId: String? = null,
    private val messageProducerBufferSize: Int = 1024,
    private val messageConsumerBufferSize: Int = 1024,
    private val successOrFailureBufferSize: Int = 8192,
    private val ackTimeoutMillis: Long = 5_000,
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

    // since controlContext is single-threaded, this object does not need to be thread-safe
    private val perUserTaskJobs = ArrayList<Map<LocustTask, Job>>()

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

    suspend fun startup(): Unit = coroutineScope {
        logger.info { "Starting Locust Worker" }

        if (workerState.get() != NOT_READY) {
            throw IllegalStateException("Unexpected state: ${workerState.get()}")
        }

        if (client.connect().not()) {
            throw IllegalStateException("Unable to connect to Locust at $host:$port")
        }
        logger.info { "Connected to Locust at $host:$port" }

        val receiveMessageChannel = Channel<Message>(capacity = messageProducerBufferSize)
        launch(context = blockingIoContext) {
            try {
                while (isActive) {
                    client.receiveMessageBlocking()?.let { receiveMessageChannel.send(it) }
                }
            } catch (e: ZMQException) {
                if (workerState.get() == SHUTDOWN) {
                    logger.debug { "Receive Message producer ZMQ socket closed" }
                } else {
                    logger.warn(e) { "Receive Message producer ZMQ error" }
                }
            } catch (e: CancellationException) {
                if (workerState.get() == SHUTDOWN) {
                    logger.debug { "Receive Message producer closed" }
                } else {
                    logger.warn(e) { "Receive Message producer cancelled error" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Receive Message producer error" }
            }
        }

        val sendMessageChannel = Channel<Message>(capacity = messageConsumerBufferSize)
        launch(context = controlContext) {
            try {
                for (message in sendMessageChannel) {
                    if (client.sendMessageAsync(message).not()) {
                        logger.warn { "Unable to send ZMQ message, type=${message.type}" }
                    }
                }
            } catch (e: ZMQException) {
                logger.warn(e) { "Send Message consumer ZMQ error" }
            } catch (e: CancellationException) {
                if (workerState.get() == SHUTDOWN) {
                    logger.debug { "Send Message consumer closed" }
                } else {
                    logger.warn(e) { "Send Message consumer cancelled error" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Send Message consumer error" }
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
                    while (true) {
                        try {
                            delay(heartbeatMillis)

                            val stateName = when (val state = workerState.get()) {
                                READY, WAITING -> READY.lowerCase
                                SPAWNING, RUNNING, STOPPED -> state.lowerCase
                                SHUTDOWN -> break
                                else -> throw IllegalStateException("Unexpected state: ${workerState.get()}")
                            }
                            val cpuUsage = (osBean.cpuLoad * 100).roundToInt()
                            val data = mapOf<String, Any>(
                                "state" to stateName,
                                "current_cpu_usage" to cpuUsage,
                                "current_memory_usage" to osBean.freeMemorySize,
                            )
                            sendMessageChannel.send(Message(HEARTBEAT, nodeId, data))

                            logger.trace { "Sent heartbeat with state=$stateName" }
                        } catch (e: Exception) {
                            logger.error(e) { "Error from heartbeat loop" }
                        }
                    }
                }
                stats.start(statsReportMillis)
                eventLoop(stats, sendMessageChannel, receiveMessageChannel)
            }
        }
    }

    private suspend fun receiveAck(receiveMessageChannel: ReceiveChannel<Message>) = coroutineScope {
        withTimeout(ackTimeoutMillis * 1_000) {
            val receivedMessage = receiveMessageChannel.receive()
            if (receivedMessage.type != ACK) {
                throw IllegalStateException("Expected ack, but got: ${receivedMessage.type}")
            }
            val workerIndex = receivedMessage.data!!["index"]
            logger.info { "Received ack, worker-index=$workerIndex" }
        }
        if (workerState.compareAndSet(READY, WAITING).not()) {
            throw IllegalStateException("Unexpected state: ${workerState.get()}")
        }
    }

    private suspend fun eventLoop(
        stats: LocustStats,
        sendMessageChannel: SendChannel<Message>,
        receiveMessageChannel: ReceiveChannel<Message>,
    ) = coroutineScope {
        launch(context = controlContext) {
            for (message in receiveMessageChannel) {
                when (message.type) {
                    QUIT -> {
                        logger.info { "Quit message from controller, state=${workerState.get()}" }
                        workerState.set(STOPPED)

                        perUserTaskJobs.forEach { user -> user.forEach { it.value } }
                        perUserTaskJobs.clear()

                        // send last stats message before exiting
                        stats.stop()
                        exitProcess(0)
                    }

                    RECONNECT -> {
                        logger.info { "Reconnect message from controller, state=${workerState.get()}" }
                        if (client.connect(reconnect = true).not()) {
                            throw IllegalStateException("Unable to reconnect to Locust at $host:$port")
                        }
                    }

                    SPAWN -> {
                        logger.info { "Spawn message from controller, state=${workerState.get()}" }
                        spawn(stats, message, sendMessageChannel)
                    }

                    SPAWNING_COMPLETE -> {
                        logger.info { "Spawning Complete message from controller, state=${workerState.get()}" }
                    }

                    STOP -> {
                        logger.info { "Stop message from controller, state=${workerState.get()}" }
                        stop(sendMessageChannel)
                    }

                    ACK -> {
                        val workerIndex = message.data!!["index"]
                        logger.info { "Ack message from controller, worker-index=$workerIndex, state=${workerState.get()}" }
                        if (workerState.compareAndSet(READY, WAITING).not()) {
                            throw IllegalStateException("Unexpected state: ${workerState.get()}")
                        }
                    }

                    HEARTBEAT -> {
                        logger.trace { "Heartbeat message from controller, state=${workerState.get()}" }
                    }

                    else -> logger.warn { "Unexpected message type received: ${message.type}" }
                }
            }
        }
    }

    private suspend fun spawn(
        stats: LocustStats,
        message: Message,
        sendMessageChannel: SendChannel<Message>,
    ) = coroutineScope {
        if (workerState.getAndSet(SPAWNING) == WAITING) {
            // only reset stats once
            stats.reset()
        }
        sendMessageChannel.send(Message(LocustMessageType.SPAWNING, nodeId))

        @Suppress("UNCHECKED_CAST") val userClassesCountMap = message.data!!["user_classes_count"] as Map<String, Int>
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
            logger.info { "Spawn resulted in no change in user-units" }
            return@coroutineScope
        }

        logger.info { "+$numUsersToCreate (-$numUsersToDelete) user-units" }

        for (userNum in 1..numUsersToDelete) {
            perUserTaskJobs.removeLast().forEach { if (it.value.isActive) it.value.cancel() }
        }

        // spawn worker tasks with numUsers (can be more spawns during test run)
        for (userNum in 1..numUsersToCreate) {
            val taskJobs = HashMap<LocustTask, Job>(tasks.size)
            perUserTaskJobs.add(taskJobs)

            for (task in tasks) {
                val taskInstance = task.instance()
                taskJobs[taskInstance] = GlobalScope.launch(context = taskContext) {
                    try {
                        try {
                            taskInstance.beforeExecuteLoop(taskContext)
                        } catch (e: CancellationException) {
                            logger.info { "Task (${taskInstance.name()}) cancelled (onStart)" }
                            return@launch
                        } catch (e: Exception) {
                            logger.error(e) { "Task (${taskInstance.name()}) onStart exception caught" }
                            stats.failure("onStart", taskInstance.name(), 0, e.message ?: "")
                            return@launch
                        }
                        try {
                            while (isActive) {
                                taskInstance.execute(stats, taskContext)
                            }
                            if (isActive.not()) {
                                logger.warn {
                                    "Task (${taskInstance.name()}) no longer active. Was CancellationException suppressed?"
                                }
                                return@launch
                            }
                        } catch (e: CancellationException) {
                            logger.info { "Task (${taskInstance.name()}) cancelled" }
                            return@launch
                        } catch (e: Exception) {
                            logger.error(e) { "Task (${taskInstance.name()}) execute exception caught" }
                            stats.failure("unknown", taskInstance.name(), 0, e.message ?: "")
                        } finally {
                            try {
                                taskInstance.afterExecuteLoop(taskContext)
                            } catch (e: CancellationException) {
                                logger.info { "Task (${taskInstance.name()}) cancelled (onStop)" }
                            } catch (e: Exception) {
                                logger.warn(e) { "Task (${taskInstance.name()}) onStop exception caught" }
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

        val messageData = mapOf(
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
        logger.info { "Shutting down Locust Worker" }
        workerState.set(SHUTDOWN)
        taskContext.close()
        controlContext.close()
        client.close()
    }
}
