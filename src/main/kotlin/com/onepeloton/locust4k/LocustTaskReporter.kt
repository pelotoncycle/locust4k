package com.onepeloton.locust4k

import kotlin.time.TimeMark

const val EXECUTE = "execute"

interface LocustTaskReporter {
    suspend fun success(method: String, taskName: String, responseTimeMillis: Long, contentLength: Long)

    suspend fun success(method: String, taskName: String, responseTimeMark: TimeMark, contentLength: Long)

    suspend fun failure(method: String, taskName: String, responseTimeMillis: Long, error: String)

    suspend fun failure(method: String, taskName: String, responseTimeMark: TimeMark, error: String)
}
