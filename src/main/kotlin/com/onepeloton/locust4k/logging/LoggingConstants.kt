package com.onepeloton.locust4k.logging

class LoggingConstants private constructor() {
    companion object {
        const val STATE_LOG_PARAM = "worker_state"
        const val NODE_ID_LOG_PARAM = "worker_node_id"
        const val INDEX_LOG_PARAM = "worker_index"
        const val INCREASE_USERS_LOG_PARAM = "increase_users"
        const val DECREASE_USERS_LOG_PARAM = "decrease_users"
        const val TASK_NAME_LOG_PARAM = "task_name"
        const val HEARTBEAT_STATE_LOG_PARAM = "heartbeat_state"
        const val MESSAGE_TYPE_LOG_PARAM = "message_type"
        const val HOST_LOG_PARAM = "locust_host"
        const val PORT_LOG_PARAM = "locust_port"
    }
}
