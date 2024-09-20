package com.onepeloton.locust4k

import kotlin.time.TimeMark

/** Default `method` argument for [LocustTaskReporter] callbacks. */
const val EXECUTE_METHOD = "execute"

/**
 * Callback functions for each iteration of a [LocustTask] execution.
 */
interface LocustTaskReporter {
    /**
     * Report successful load test network request with elapsed [responseTimeMillis] and [contentLength] of response.
     * The optional [method] argument could be used where there are multiple interactions being measured per task.
     */
    suspend fun success(
        responseTimeMillis: Long,
        contentLength: Long,
        taskName: String,
        method: String = EXECUTE_METHOD,
    )

    /**
     * Report successful load test network request with elapsed [responseTimeMark] and [contentLength] of response.
     * The optional [method] argument could be used where there are multiple interactions being measured per task.
     */
    suspend fun success(
        responseTimeMark: TimeMark,
        contentLength: Long,
        taskName: String,
        method: String = EXECUTE_METHOD,
    )

    /**
     * Report failure response of load test network request with elapsed [responseTimeMillis] and [error] message.
     * The optional [method] argument could be used where there are multiple interactions being measured per task.
     */
    suspend fun failure(
        responseTimeMillis: Long,
        error: String,
        taskName: String,
        method: String = EXECUTE_METHOD,
    )

    /**
     * Report failure response of load test network request with elapsed [responseTimeMark] and [error] message.
     * The optional [method] argument could be used where there are multiple interactions being measured per task.
     */
    suspend fun failure(
        responseTimeMark: TimeMark,
        error: String,
        taskName: String,
        method: String = EXECUTE_METHOD,
    )
}
