package com.onepeloton.locust4k.examples

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.images.builder.Transferable
import org.testcontainers.utility.DockerImageName
import java.time.Duration

abstract class TestcontainersBase {
    companion object {
        val locustMasterVersion = System.getenv("LOCUST_MASTER_VERSION") ?: "latest"

        val locustWorkerExampleVersion = System.getenv("LOCUST_WORKER_EXAMPLE_VERSION") ?: "latest"

        val network = Network.newNetwork()

        val locustMasterNetworkAlias = "locust-master"

        private val noOpLocustFile =
            """
            from locust import User, task
            class Locust4k(User):
                @task(1)
                def workerTask(self):
                    pass
            """.trimIndent()
    }

    fun buildLocustMasterContainer(): GenericContainer<*> =
        GenericContainer(DockerImageName.parse("locustio/locust:$locustMasterVersion"))
            .withNetwork(network)
            .withNetworkAliases(locustMasterNetworkAlias)
            .withExposedPorts(8089) // Web UI port
            .withCopyToContainer(Transferable.of(noOpLocustFile), "/tmp/locust-master.py")
            .withCommand("-f /tmp/locust-master.py --master")
            .waitingFor(
                LogMessageWaitStrategy()
                    .withRegEx(".* Starting Locust.*\\s")
                    .withStartupTimeout(Duration.ofSeconds(30)),
            )

    fun buildLocustWorkerExampleContainer(): GenericContainer<*> =
        GenericContainer(DockerImageName.parse("locust4k/example:$locustWorkerExampleVersion"))
            .withNetwork(network)
            .withEnv("LOCUST_MASTER_HOST", locustMasterNetworkAlias)
            .waitingFor(
                LogMessageWaitStrategy()
                    .withRegEx(".* Starting Locust Worker.*\\s")
                    .withStartupTimeout(Duration.ofSeconds(30)),
            )
}
