package com.onepeloton.locust4k.messages

enum class LocustMessageType(val lowerCase: String) {
    ACK("ack"),
    CLIENT_STOPPED("client_stopped"),
    CLIENT_READY("client_ready"),
    EXCEPTION("exception"),
    HEARTBEAT("heartbeat"),
    QUIT("quit"),
    RECONNECT("reconnect"),
    SPAWN("spawn"),
    SPAWNING("spawning"),
    SPAWNING_COMPLETE("spawning_complete"),
    STOP("stop"),
    STATS("stats");

    companion object {
        private val lowerCaseTypeMap: Map<String, LocustMessageType> = entries.associateBy { it.lowerCase }

        fun valueOfLowerCase(type: String): LocustMessageType =
            lowerCaseTypeMap[type] ?: throw IllegalArgumentException("Unknown type=$type")
    }
}
