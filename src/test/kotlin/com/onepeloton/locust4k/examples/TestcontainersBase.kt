package com.onepeloton.locust4k.examples

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.fail
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.images.builder.Transferable
import org.testcontainers.utility.DockerImageName
import java.time.Duration

abstract class TestcontainersBase {
    companion object {
        private val locustMasterVersion = System.getenv("LOCUST_MASTER_VERSION") ?: "latest"

        private val locustWorkerExampleVersion = System.getenv("LOCUST_WORKER_EXAMPLE_VERSION") ?: "latest"

        private val network = Network.newNetwork()!!

        private const val LOCUST_MASTER_NETWORK_ALIAS = "locust-master"

        private const val LOCUST_FILE_PATH = "/tmp/locust-master.py"

        private val noOpLocustFile =
            """
            from locust import User, task
            class Locust4k(User):
                @task(1)
                def workerTask(self):
                    pass
            """.trimIndent()

        private val logger = KotlinLogging.logger {}

        init {
            logger.info { "LOCUST_MASTER_VERSION=$locustMasterVersion" }
            logger.info { "LOCUST_WORKER_EXAMPLE_VERSION=$locustWorkerExampleVersion" }
        }
    }

    fun buildLocustMasterContainer(
        headless: Boolean = true,
        users: Int = 1,
        spawnRate: Int = 1,
        expectWorkers: Int = 1,
        runtimeSeconds: Int = 1,
    ): GenericContainer<*> {
        val command =
            if (headless) {
                "-f $LOCUST_FILE_PATH --master --headless " +
                    "--expect-workers $expectWorkers " +
                    "--users $users " +
                    "--spawn-rate $spawnRate " +
                    "--run-time $runtimeSeconds"
            } else {
                "-f $LOCUST_FILE_PATH --master"
            }
        return GenericContainer(DockerImageName.parse("locustio/locust:$locustMasterVersion"))
            .withNetwork(network)
            .withNetworkAliases(LOCUST_MASTER_NETWORK_ALIAS)
            .withExposedPorts(5557, 8089)
            .withCopyToContainer(Transferable.of(noOpLocustFile), LOCUST_FILE_PATH)
            .withCommand(command)
            .waitingFor(
                LogMessageWaitStrategy()
                    .withRegEx(".* Starting Locust.*\\s")
                    .withStartupTimeout(Duration.ofSeconds(30)),
            )
    }

    fun buildLocustWorkerExampleContainer(): GenericContainer<*> =
        GenericContainer(DockerImageName.parse("locust4k/example:$locustWorkerExampleVersion"))
            .withNetwork(network)
            .withEnv("LOCUST_MASTER_HOST", LOCUST_MASTER_NETWORK_ALIAS)
            .waitingFor(
                LogMessageWaitStrategy()
                    .withRegEx(".* Starting Locust Worker.*\\s")
                    .withStartupTimeout(Duration.ofSeconds(30)),
            )

    protected fun startContainers(vararg containers: GenericContainer<*>) {
        containers.forEach { container ->
            try {
                container.start()
            } catch (e: Exception) {
                fail("\nFailed to start ${container.dockerImageName} with logs=\n${container.logs}", e)
            }
        }
    }

    protected fun stopContainers(vararg containers: GenericContainer<*>) {
        containers.forEach { container ->
            if (container.isRunning) {
                try {
                    container.stop()
                } catch (e: Exception) {
                    logger.warn(e) { "Unexpected exception while stopping ${container.dockerImageName}" }
                }
            }
        }
    }
}
