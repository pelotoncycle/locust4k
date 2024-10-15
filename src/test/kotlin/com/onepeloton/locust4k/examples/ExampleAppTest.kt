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
                .withStartupTimeout(Duration.ofSeconds(5))
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
                    "+1 (-0) user-units",
                    "beforeExecuteLoop invoked",
                    "Spawning Complete",
                    "Quit message",
                    "Task (exampleTask) cancelled",
                    "afterExecuteLoop invoked",
                    "Stats consumer stopped",
                    "Stats reporter stopped",
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
                .withStartupTimeout(Duration.ofSeconds(5))
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
                    "+2 (-0) user-units",
                    "beforeExecuteLoop invoked",
                    "beforeExecuteLoop invoked",
                    "Spawn message from controller",
                    "+1 (-0) user-units",
                    "beforeExecuteLoop invoked",
                    "Spawning Complete",
                    "Quit message",
                    // next two messages each repeated 3 times, but order inconsistent
                    "Task (exampleTask) cancelled",
                    "afterExecuteLoop invoked",
                    "Stats consumer stopped",
                    "Stats reporter stopped",
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
            repeat(2) {
                startLoadTest(locustMasterContainer)

                LogMessageWaitStrategy()
                    .withRegEx(".* Spawning Complete message from controller.*\\s")
                    .withStartupTimeout(Duration.ofSeconds(5))
                    .waitUntilReady(locustWorkerExampleContainer)

                stopLoadTest(locustMasterContainer)
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
                    "+1 (-0) user-units",
                    "Spawning Complete",
                    "Stop message from controller",
                    "Ack message from controller",
                    // second run
                    "Spawn message from controller",
                    "Stats reset",
                    "+1 (-0) user-units",
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
