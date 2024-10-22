package com.onepeloton.locust4k.examples

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.text.StringContainsInOrder.stringContainsInOrder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import java.time.Duration

class MasterHeartbeatTest : TestcontainersBase() {
    @Test
    fun sigkillOfMasterCausesWorkerToExitAfterHeartbeatTimeout() {
        val locustMasterContainer = buildLocustMasterContainer(headless = false)
        val locustWorkerExampleContainer = buildLocustWorkerExampleContainer()
        try {
            // given
            startContainers(locustMasterContainer, locustWorkerExampleContainer)

            LogMessageWaitStrategy()
                .withRegEx(".*1 workers connected.*\\s")
                .withStartupTimeout(Duration.ofSeconds(5))
                .waitUntilReady(locustMasterContainer)

            startLoadTest(locustMasterContainer)

            LogMessageWaitStrategy()
                .withRegEx(".* Spawning Complete message from controller.*\\s")
                .withStartupTimeout(Duration.ofSeconds(5))
                .waitUntilReady(locustWorkerExampleContainer)

            // when
            locustMasterContainer.stop()

            LogMessageWaitStrategy()
                .withRegEx(".* Shutting down Locust Worker.*\\s")
                .withStartupTimeout(Duration.ofSeconds(10))
                .waitUntilReady(locustWorkerExampleContainer)

            val workerLogs = locustWorkerExampleContainer.logs

            // then
            assertThat(
                workerLogs,
                stringContainsInOrder(
                    "Starting Locust Worker",
                    "Connecting",
                    "Connected to Locust",
                    "Ack message from controller",
                    "Spawn message from controller",
                    "Stats reset",
                    "Spawn increasing user-units",
                    "Spawning Complete",
                    "Controller heartbeat timeout",
                    "Task cancelled",
                    "afterExecuteLoop invoked",
                    "Shutting down Locust Worker",
                ),
            )

            assertFalse(locustMasterContainer.isRunning, "locust master running")

            runBlocking {
                // wait for worker container to stop
                for (i in 1..20) {
                    delay(100)
                    if (locustWorkerExampleContainer.isRunning.not()) {
                        break
                    }
                }
            }
            assertFalse(locustWorkerExampleContainer.isRunning, "locust worker running")
        } finally {
            stopContainers(locustMasterContainer, locustWorkerExampleContainer)
        }
    }
}
