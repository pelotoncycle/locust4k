# Locust4k

Client library for writing [Locust](https://locust.io/) load test scenarios in Kotlin. This is not a full-fledged
load testing framework, but facilitates communication from Worker apps (Kotlin) to Locust master (Python).

Inspired by [locust4j](https://github.com/myzhan/locust4j).

## Usage

Import as a Maven or Gradle dependency:

```text
implementation("com.onepeloton.locust4k:locust4k:1.1.0")
```

Create one or more
[LocustTask](https://github.com/pelotoncycle/locust4k/blob/main/src/main/kotlin/com/onepeloton/locust4k/LocustTask.kt)
implementations as demonstrated in the
[ExampleApp](https://github.com/pelotoncycle/locust4k/blob/main/src/main/kotlin/com/onepeloton/locust4k/examples/ExampleApp.kt).
The `execute` method of a `LocustTask` will be repeatedly invoked while the test is running. Within this method
you can use your choice of networking libraries to make requests to your service under load. For each request, or
series of interactions making up a scenario, invoke the `success` or `failure` callbacks to update statistics on
Locust master.

Within the Locust web UI, for each "user" that you add, each Worker node that is connected to the master node will
create one _instance_ of a given `LocustTask`. By default, `execute` will be invoked as rapidly as possible. If one
wishes to simulate human user behavior, Kotlin `delay` statements are one possible solution. However, if you are testing
the saturation point of your service under load, you would typically not introduce any delays, but instead gradually
increase nodes and the number of "user" units per node.

This library uses Kotlin coroutines with a minimum number of threads by default. Your `LocustTask` implementations must
be thread safe and ideally use non-blocking coroutines. Additional control of resources and settings are exposed via the
[LocustWorker](https://github.com/pelotoncycle/locust4k/blob/main/src/main/kotlin/com/onepeloton/locust4k/LocustWorker.kt)
constructor.

## Run the ExampleApp

Refer to Locust installation instructions at https://docs.locust.io/

Install Locust on macOS using Homebrew:

```shell
brew update
brew install locust
```

Start Locust master from the root of this project:

```shell
locust -f locust-master.py --master --master-bind-host=127.0.0.1 --master-bind-port=5557
```

Note [locust-master.py](https://github.com/pelotoncycle/locust4k/blob/locust-master.py) above, which is essentially
a no-op `locustfile` and required to start Locust.

In another terminal, run the example Worker application:

```shell
./gradlew build
./gradlew runExample -Pname=ExampleApp
```

Visit http://localhost:8089/ to start the load test.

## Tests

We use Docker and [Testcontainers](https://java.testcontainers.org) to test interoperability with Locust master.
Note that `docker` must be available on the command-line (see [shell completions](https://docs.docker.com/engine/cli/completion/)).

```shell
# build ExampleApp Docker image
./gradlew jibDockerBuild

# execute test suite
./gradlew test
```

## Logging

This library includes a dependency on [slf4j-api](https://slf4j.org), and a SLF4J binding must be provided on the
classpath in order to output logs. Log messages are succinct, with additional details set as structured logging
arguments. A common practice is to output structured logs as JSON via the
[logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder) library.
