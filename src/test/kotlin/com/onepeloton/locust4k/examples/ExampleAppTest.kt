package com.onepeloton.locust4k.examples

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class ExampleAppTest : TestcontainersBase() {
    @Container
    val locustMasterContainer = buildLocustMasterContainer()

    @Container
    val locustWorkerExampleContainer = buildLocustWorkerExampleContainer()

    @Test
    fun test() {
        Assertions.assertTrue(locustMasterContainer.isRunning)
        Assertions.assertTrue(locustWorkerExampleContainer.isRunning)
    }
}
