package com.onepeloton.locust4k.examples

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.text.StringContainsInOrder.stringContainsInOrder
import org.junit.jupiter.api.Test
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import java.time.Duration

class ExampleAppTest : TestcontainersBase() {
    @Test
    fun singleWorkerWithLocustMasterEndingTest() {
        val locustMasterContainer = buildLocustMasterContainer()
        val locustWorkerExampleContainer = buildLocustWorkerExampleContainer()
        try {
            startContainers(locustMasterContainer, locustWorkerExampleContainer)

            LogMessageWaitStrategy()
                .withRegEx(".* Receive Message producer ZMQ socket closed.*\\s")
                .withStartupTimeout(Duration.ofSeconds(5))
                .waitUntilReady(locustWorkerExampleContainer)

            val workerLogs = locustWorkerExampleContainer.logs

            assertThat(
                workerLogs,
                stringContainsInOrder(
                    "Starting Locust Worker",
                    "Connected to Locust",
                    "Received ack",
                    "Spawn message from controller",
                    "Stats reset",
                    "+1 (-0) user-units",
                    "onStart invoked",
                    "Spawning Complete",
                    "Quit message",
                    "Task (exampleTask) cancelled",
                    "onStop invoked",
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
