# locust4k

[Locust](https://locust.io/) Worker client for Kotlin. Inspired by [locust4j](https://github.com/myzhan/locust4j).

## Running Locust locally

Install Locust on macOS using Homebrew:

```shell
brew update
brew install locust
```

Start Locust master from the root of this project:

```shell
# start in the root directory of this Git project
locust -f locust-master.py --master --master-bind-host=127.0.0.1 --master-bind-port=5557
```

Run the example application locally:

```shell
./gradlew runExample -Pname=ExampleApp
```

Visit [Locust UI](http://localhost:8089/) to start the load test.
