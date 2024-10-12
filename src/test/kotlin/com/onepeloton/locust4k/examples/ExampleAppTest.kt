package com.onepeloton.locust4k.examples

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.text.StringContainsInOrder.stringContainsInOrder
import org.junit.jupiter.api.Test
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import java.time.Duration

class ExampleAppTest : TestcontainersBase() {
    @Test
    fun singleWorkerWithLocustMasterEndingTest() {
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
                .withRegEx(".* Receive Message producer ZMQ socket closed.*\\s")
                .withStartupTimeout(Duration.ofSeconds(5))
                .waitUntilReady(locustWorkerExampleContainer)

            val workerLogs = locustWorkerExampleContainer.logs

            // then
            assertThat(
                workerLogs,
                stringContainsInOrder(
                    "Starting Locust Worker",
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
                    "Closing connection",
                    "Receive Message producer ZMQ socket closed",
                ),
            )
        } finally {
            stopContainers(locustMasterContainer, locustWorkerExampleContainer)
        }
    }

    @Test
    fun singleWorkerMultipleUsersWithLocustMasterEndingTest() {
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
                .withRegEx(".* Receive Message producer ZMQ socket closed.*\\s")
                .withStartupTimeout(Duration.ofSeconds(5))
                .waitUntilReady(locustWorkerExampleContainer)

            val workerLogs = locustWorkerExampleContainer.logs

            // then
            assertThat(
                workerLogs,
                stringContainsInOrder(
                    "Starting Locust Worker",
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
                    "afterExecuteLoop invoked",
                    "afterExecuteLoop invoked",
                    "afterExecuteLoop invoked",
                    "Stats consumer stopped",
                    "Stats reporter stopped",
                    "Shutting down Locust Worker",
                    "Closing connection",
                    "Receive Message producer ZMQ socket closed",
                ),
            )
        } finally {
            stopContainers(locustMasterContainer, locustWorkerExampleContainer)
        }
    }
}
