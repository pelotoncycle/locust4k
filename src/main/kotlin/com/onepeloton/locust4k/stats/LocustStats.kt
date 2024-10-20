package com.onepeloton.locust4k.stats

import com.onepeloton.locust4k.LocustTaskReporter
import com.onepeloton.locust4k.LocustWorkerState
import com.onepeloton.locust4k.LocustWorkerState.SHUTDOWN
import com.onepeloton.locust4k.LocustWorkerState.STOPPED
import com.onepeloton.locust4k.logging.LoggingConstants.Companion.NODE_ID_LOG_PARAM
import com.onepeloton.locust4k.messages.LocustMessageType.STATS
import com.onepeloton.locust4k.messages.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.TimeMark

class LocustStats(
    private val nodeId: String,
    private val workerState: AtomicReference<LocustWorkerState>,
    private val controlContext: CoroutineContext,
    private val sendMessageChannel: SendChannel<Message>,
    successOrFailureBufferSize: Int = 8192,
) : LocustTaskReporter {
    private val logger = KotlinLogging.logger {}

    private val loggerPayload: Map<String, Any?> = mapOf(NODE_ID_LOG_PARAM to nodeId)

    private val successOrFailureChannel = Channel<SuccessOrFailure>(capacity = successOrFailureBufferSize)

    @Volatile
    private lateinit var statsConsumerJob: Job

    @Volatile
    private lateinit var statsReporterJob: Job

    private val total = StatsEntry(name = "Total")
    private val entries = mutableMapOf<String, StatsEntry>()
    private val errors = mutableMapOf<String, StatsError>()

    private var numUsers = 0
    private var userClassesCountMap: Map<String, Int> = emptyMap()

    fun updateUserCounts(
        numUsers: Int,
        userClassesCountMap: Map<String, Int>,
    ) {
        this.numUsers = numUsers
        this.userClassesCountMap = userClassesCountMap
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun start(statsReportMillis: Long = 3_000): Unit =
        coroutineScope {
            statsReporterJob =
                GlobalScope.launch(context = controlContext) {
                    try {
                        while (isActive) {
                            delay(statsReportMillis)
                            sendReport()
                            logger.atTrace {
                                message = "Stats reporter iteration"
                                payload = loggerPayload
                            }
                        }
                        if (workerState.get() == STOPPED) {
                            // send last stats message
                            sendReport()
                            logger.atDebug {
                                message = "Stats reporter stopped (inactive)"
                                payload = loggerPayload
                            }
                        }
                    } catch (e: CancellationException) {
                        if (workerState.get() == STOPPED) {
                            // send last stats message
                            sendReport()
                            logger.atDebug {
                                message = "Stats reporter stopped"
                                payload = loggerPayload
                            }
                        } else if (workerState.get() == SHUTDOWN) {
                            logger.atDebug {
                                message = "Stats reporter closed"
                                payload = loggerPayload
                            }
                        } else {
                            logger.atWarn {
                                message = "Stats reporter cancelled error"
                                payload = loggerPayload
                                cause = e
                            }
                        }
                    } catch (e: Exception) {
                        logger.atError {
                            message = "Stats reporter error"
                            payload = loggerPayload
                            cause = e
                        }
                    }
                }
            statsConsumerJob =
                GlobalScope.launch(context = controlContext) {
                    try {
                        for (item in successOrFailureChannel) {
                            consume(item)
                            logger.atTrace {
                                message = "Consumed stats message"
                                payload = loggerPayload
                            }
                        }
                    } catch (e: CancellationException) {
                        if (workerState.get() == STOPPED) {
                            logger.atDebug {
                                message = "Stats consumer stopped"
                                payload = loggerPayload
                            }
                        } else if (workerState.get() == SHUTDOWN) {
                            logger.atDebug {
                                message = "Stats consumer closed"
                                payload = loggerPayload
                            }
                        } else {
                            logger.atWarn {
                                message = "Stats consumer cancelled error"
                                payload = loggerPayload
                                cause = e
                            }
                        }
                    } catch (e: Exception) {
                        logger.atError {
                            message = "Stats consumer error"
                            payload = loggerPayload
                            cause = e
                        }
                    }
                }
        }

    suspend fun stop(): Unit =
        coroutineScope {
            statsConsumerJob.cancelAndJoin()
            statsReporterJob.cancelAndJoin()
        }

    override suspend fun success(
        responseTimeMillis: Long,
        contentLength: Long,
        taskName: String,
        method: String,
    ) {
        val item = SuccessOrFailure(method, taskName, responseTimeMillis, contentLength = contentLength)
        successOrFailureChannel.trySend(item)
            .onFailure {
                if (it == null) {
                    logger.atWarn {
                        message = "Failed to record success result. May have reached successOrFailureBufferSize."
                        payload = loggerPayload
                    }
                } else {
                    logger.atWarn {
                        message = "Failed to record success result"
                        payload = loggerPayload
                        cause = it
                    }
                }
            }
    }

    override suspend fun success(
        responseTimeMark: TimeMark,
        contentLength: Long,
        taskName: String,
        method: String,
    ) {
        this.success(responseTimeMark.elapsedNow().toLong(MILLISECONDS), contentLength, taskName, method)
    }

    override suspend fun failure(
        responseTimeMillis: Long,
        error: String,
        taskName: String,
        method: String,
    ) {
        val item = SuccessOrFailure(method, taskName, responseTimeMillis, error = error)
        successOrFailureChannel.trySend(item)
            .onFailure {
                if (it == null) {
                    logger.atWarn {
                        message = "Failed to record failure result. May have reached successOrFailureBufferSize."
                        payload = loggerPayload
                    }
                } else {
                    logger.atWarn {
                        message = "Failed to record failure result"
                        payload = loggerPayload
                        cause = it
                    }
                }
            }
    }

    override suspend fun failure(
        responseTimeMark: TimeMark,
        error: String,
        taskName: String,
        method: String,
    ) {
        this.failure(responseTimeMark.elapsedNow().toLong(MILLISECONDS), error, taskName, method)
    }

    private fun consume(item: SuccessOrFailure) {
        val now = StatsEntry.currentTimeInSeconds()
        total.log(now, item.responseTimeMillis, item.contentLength)

        val entryKey = item.taskName + item.method
        entries.computeIfAbsent(entryKey) {
            StatsEntry(item.taskName, item.method)
        }.log(now, item.responseTimeMillis, item.contentLength)

        item.error?.let {
            total.logError(now)

            val errorKey = StatsError.buildKey(item.taskName, item.method, item.error)
            errors.computeIfAbsent(errorKey) {
                StatsError(item.taskName, item.method, item.error)
            }.occurrences++
        }
    }

    private suspend fun sendReport() {
        val serializedStats = ArrayList<Map<String, Any>>(entries.size)
        entries.filterValues { it.numRequests > 0 || it.numFailures > 0 }
            .mapTo(serializedStats) { it.value.getStrippedReport() }

        val serializedErrors = HashMap<String, Map<String, Any>>()
        errors.forEach { (k, v) -> serializedErrors[k] = v.toMap() }

        val messageData =
            mutableMapOf(
                "stats" to serializedStats,
                "stats_total" to total.getStrippedReport(),
                "errors" to serializedErrors,
                "count" to numUsers,
                "user_classes_count" to userClassesCountMap,
            )
        errors.clear()

        sendMessageChannel.send(Message(STATS, nodeId, messageData))
    }

    fun reset() {
        total.reset()
        entries.clear()
        errors.clear()
        logger.atDebug {
            message = "Stats reset"
            payload = loggerPayload
        }
    }
}

/**
 * Only Success records have [contentLength] and only Failure records have [error] properties set.
 */
data class SuccessOrFailure(
    val method: String,
    val taskName: String,
    val responseTimeMillis: Long,
    val contentLength: Long = 0,
    val error: String? = null,
)
