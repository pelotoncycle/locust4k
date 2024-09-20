@file:JvmName("ExampleApp")

package com.onepeloton.locust4k.examples

import com.onepeloton.locust4k.LocustTask
import com.onepeloton.locust4k.LocustTaskReporter
import com.onepeloton.locust4k.LocustWorker
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.lang.Runtime.getRuntime
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Example load test application.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun main() {
    val host = "127.0.0.1"
    val port = 5557
    val worker = LocustWorker(host, port, listOf(ExampleLocustTask()))

    getRuntime().addShutdownHook(Thread { worker.shutdown() })

    runBlocking {
        try {
            worker.startup()
        } catch (e: Exception) {
            logger.error(e) { "Uncaught exception" }
        } finally {
            worker.shutdown()
        }
    }
}

private class ExampleLocustTask : LocustTask {
    private val logger = KotlinLogging.logger {}

    override fun name(): String = "exampleTask"

    override suspend fun beforeExecuteLoop(context: CoroutineContext) {
        logger.info { "onStart invoked" }
    }

    override suspend fun afterExecuteLoop(context: CoroutineContext) {
        logger.info { "onStop invoked" }
    }

    override suspend fun execute(
        reporter: LocustTaskReporter,
        context: CoroutineContext,
    ) {
        logger.trace { "execute invoked" }

        // simulate HTTP response latency
        val responseTimeMillis = Random.nextLong(0, 25)
        delay(responseTimeMillis)

        // 5% chance of an error occurring
        if (Random.nextInt(100) > 5) {
            val contentLength = Random.nextLong(5_000, 10_000)
            reporter.success(responseTimeMillis, contentLength, name())
        } else {
            reporter.failure(responseTimeMillis, "exampleError", name())
        }
    }

    override fun instance(): LocustTask = ExampleLocustTask()
}
