package com.onepeloton.locust4k

enum class LocustWorkerState(val lowerCase: String) {
    NOT_READY("not_ready"),
    READY("ready"),
    WAITING("waiting"),
    SPAWNING("spawning"),
    RUNNING("running"),
    STOPPED("stopped"),
    SHUTDOWN("shutdown");

    companion object {
        private val lowerCaseStateMap: Map<String, LocustWorkerState> =
            LocustWorkerState.entries.associateBy { it.lowerCase }

        fun valueOfLowerCase(state: String): LocustWorkerState =
            lowerCaseStateMap[state] ?: throw IllegalArgumentException("Unknown state=$state")
    }
}
