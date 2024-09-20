package com.onepeloton.locust4k.stats

import org.eclipse.collections.impl.map.mutable.primitive.IntIntHashMap
import org.eclipse.collections.impl.map.mutable.primitive.LongIntHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * Based on [com.github.myzhan.locust4j.stats.StatsEntry](https://github.com/myzhan/locust4j/blob/2.2.4/src/main/java/com/github/myzhan/locust4j/stats/StatsEntry.java).
 */
class StatsEntry(
    val name: String,
    val method: String = "",
) {
    var numRequests: Long = 0
        private set
    var numFailures: Long = 0
        private set
    private var totalResponseTime: Long = 0
    private var minResponseTime: Long = 0
    private var maxResponseTime: Long = 0
    private var totalContentLength: Long = 0
    private var startTime: Long = currentTimeInSeconds()
    private var lastRequestTimestamp: Long = startTime
    private var numReqsPerSec = LongIntHashMap(REQS_PER_SEC_INITIAL_CAPACITY)
    private var numFailPerSec = LongIntHashMap(REQS_PER_SEC_INITIAL_CAPACITY)
    private var responseTimes = IntIntHashMap(RESPONSE_TIMES_INITIAL_CAPACITY)

    companion object {
        private const val REQS_PER_SEC_INITIAL_CAPACITY = 4
        private const val RESPONSE_TIMES_INITIAL_CAPACITY = 32

        fun currentTimeInSeconds(): Long = System.currentTimeMillis() / 1000

        fun round(
            value: Long,
            places: Int,
        ): Int {
            val pow: Double = 10.0.pow(places.toDouble())
            val digit = pow * value
            val div = digit % 1
            val round =
                if (div > 0.5f) {
                    ceil(digit)
                } else {
                    floor(digit)
                }
            val result = round / pow
            return result.toInt()
        }
    }

    fun reset() {
        numRequests = 0
        numFailures = 0
        totalResponseTime = 0
        minResponseTime = 0
        maxResponseTime = 0
        totalContentLength = 0
        startTime = currentTimeInSeconds()
        lastRequestTimestamp = startTime
        numReqsPerSec = LongIntHashMap(REQS_PER_SEC_INITIAL_CAPACITY)
        numFailPerSec = LongIntHashMap(REQS_PER_SEC_INITIAL_CAPACITY)
        responseTimes = IntIntHashMap(RESPONSE_TIMES_INITIAL_CAPACITY)
    }

    fun log(
        now: Long,
        responseTime: Long,
        contentLength: Long,
    ) {
        ++numRequests
        logTimeOfRequest(now)
        logResponseTime(responseTime)
        totalContentLength += contentLength
    }

    private fun logTimeOfRequest(now: Long) {
        numReqsPerSec.addToValue(now, 1)
        lastRequestTimestamp = now
    }

    private fun logResponseTime(responseTime: Long) {
        totalResponseTime += responseTime
        if (minResponseTime == 0L) {
            minResponseTime = responseTime
        }
        if (responseTime < minResponseTime) {
            minResponseTime = responseTime
        }
        if (responseTime > maxResponseTime) {
            maxResponseTime = responseTime
        }
        val roundedResponseTime =
            if (responseTime < 100) {
                responseTime.toInt()
            } else if (responseTime < 1000) {
                round(responseTime, -1)
            } else if (responseTime < 10000) {
                round(responseTime, -2)
            } else if (responseTime > Integer.MAX_VALUE) {
                round(Integer.MAX_VALUE.toLong(), -3)
            } else {
                round(responseTime, -3)
            }
        responseTimes.addToValue(roundedResponseTime, 1)
    }

    fun logError(now: Long) {
        ++numFailures
        numFailPerSec.addToValue(now, 1)
    }

    fun toMap(): Map<String, Any> =
        mapOf(
            "name" to name,
            "method" to method,
            "last_request_timestamp" to lastRequestTimestamp,
            "start_time" to startTime,
            "num_requests" to numRequests,
            "num_none_requests" to 0,
            "num_failures" to numFailures,
            "total_response_time" to totalResponseTime,
            "max_response_time" to maxResponseTime,
            "min_response_time" to minResponseTime,
            "total_content_length" to totalContentLength,
            "response_times" to responseTimes,
            "num_reqs_per_sec" to numReqsPerSec,
            "num_fail_per_sec" to numFailPerSec,
        )

    fun getStrippedReport(): Map<String, Any> {
        val report = toMap()
        reset()
        return report
    }
}
