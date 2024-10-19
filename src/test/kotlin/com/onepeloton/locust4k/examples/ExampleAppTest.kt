package com.onepeloton.locust4k.examples

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.text.StringContainsInOrder.stringContainsInOrder
import org.junit.jupiter.api.Test
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import java.time.Duration

class ExampleAppTest : TestcontainersBase() {
    @Test
    fun singleUserWithHeadlessLocustMasterEndingTest() {
        val locustMasterContainer = buildLocustMasterContainer(runtimeSeconds = 5)
        val locustWorkerExampleContainer = buildLocustWorkerExampleContainer()
        try {
            // given
            startContainers(locustMasterContainer, locustWorkerExampleContainer)

            LogMessageWaitStrategy()
                .withRegEx(".*All users spawned.*\\s")
                .withStartupTimeout(Duration.ofSeconds(5))
                .waitUntilReady(locustMasterContainer)

            LogMessageWaitStrategy()
                .withTimes(2)
                .withRegEx(".*Aggregated.*\\s")
                .withStartupTimeout(Duration.ofSeconds(5))
                .waitUntilReady(locustMasterContainer)

            // when
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
                    "Received ack",
                    "Spawn message from controller",
                    "Stats reset",
                    "Spawn increasing user-units",
                    "beforeExecuteLoop invoked",
                    "Spawning Complete",
                    "Quit message",
                    "Task cancelled",
                    "afterExecuteLoop invoked",
                    "Shutting down Locust Worker",
                ),
            )
        } finally {
            stopContainers(locustMasterContainer, locustWorkerExampleContainer)
        }
    }

    @Test
    fun multipleUsersWithHeadlessLocustMasterEndingTest() {
        val locustMasterContainer = buildLocustMasterContainer(users = 3, spawnRate = 2, runtimeSeconds = 5)
        val locustWorkerExampleContainer = buildLocustWorkerExampleContainer()
        try {
            // given
            startContainers(locustMasterContainer, locustWorkerExampleContainer)

            LogMessageWaitStrategy()
                .withRegEx(".*All users spawned.*\\s")
                .withStartupTimeout(Duration.ofSeconds(5))
                .waitUntilReady(locustMasterContainer)

            LogMessageWaitStrategy()
                .withTimes(2)
                .withRegEx(".*Aggregated.*\\s")
                .withStartupTimeout(Duration.ofSeconds(5))
                .waitUntilReady(locustMasterContainer)

            // when
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
                    "Received ack",
                    "Spawn message from controller",
                    "Stats reset",
                    "Spawn increasing user-units",
                    "beforeExecuteLoop invoked",
                    "beforeExecuteLoop invoked",
                    "Spawn message from controller",
                    "Spawn increasing user-units",
                    "beforeExecuteLoop invoked",
                    "Spawning Complete",
                    "Quit message",
                    // next two messages each repeated 3 times, but order inconsistent
                    "Task cancelled",
                    "afterExecuteLoop invoked",
                    "Shutting down Locust Worker",
                ),
            )
        } finally {
            stopContainers(locustMasterContainer, locustWorkerExampleContainer)
        }
    }

    @Test
    fun singleUserWithRepeatedStartAndStop() {
        val locustMasterContainer = buildLocustMasterContainer(headless = false)
        val locustWorkerExampleContainer = buildLocustWorkerExampleContainer()
        try {
            // given
            startContainers(locustMasterContainer, locustWorkerExampleContainer)

            LogMessageWaitStrategy()
                .withRegEx(".*1 workers connected.*\\s")
                .withStartupTimeout(Duration.ofSeconds(5))
                .waitUntilReady(locustMasterContainer)

            // when
            val repeatTimes = 2
            for (i in 1..repeatTimes) {
                startLoadTest(locustMasterContainer)

                LogMessageWaitStrategy()
                    .withTimes(i)
                    .withRegEx(".* Spawning Complete message from controller.*\\s")
                    .withStartupTimeout(Duration.ofSeconds(5))
                    .waitUntilReady(locustWorkerExampleContainer)

                stopLoadTest(locustMasterContainer)

                LogMessageWaitStrategy()
                    .withTimes(i)
                    .withRegEx(".* Ack message from controller.*\\s")
                    .withStartupTimeout(Duration.ofSeconds(5))
                    .waitUntilReady(locustWorkerExampleContainer)
            }

            val workerLogs = locustWorkerExampleContainer.logs

            // then
            assertThat(
                workerLogs,
                stringContainsInOrder(
                    "Starting Locust Worker",
                    "Connecting",
                    "Connected to Locust",
                    "Received ack",
                    // first run
                    "Spawn message from controller",
                    "Stats reset",
                    "Spawn increasing user-units",
                    "Spawning Complete",
                    "Stop message from controller",
                    "Ack message from controller",
                    // second run
                    "Spawn message from controller",
                    "Stats reset",
                    "Spawn increasing user-units",
                    "Spawning Complete",
                    "Stop message from controller",
                    "Ack message from controller",
                ),
            )
        } finally {
            stopContainers(locustMasterContainer, locustWorkerExampleContainer)
        }
    }
}
