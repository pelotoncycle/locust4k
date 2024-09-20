package com.onepeloton.locust4k

enum class LocustWorkerState(val lowerCase: String) {
    NOT_READY("not_ready"),
    READY("ready"),
    WAITING("waiting"),
    SPAWNING("spawning"),
    RUNNING("running"),
    STOPPED("stopped"),
    SHUTDOWN("shutdown");
}
