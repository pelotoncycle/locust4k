package com.onepeloton.locust4k.stats

import java.security.MessageDigest
import java.util.HexFormat

/**
 * Based on [com.github.myzhan.locust4j.stats.StatsError](https://github.com/myzhan/locust4j/blob/2.2.4/src/main/java/com/github/myzhan/locust4j/stats/StatsError.java).
 */
class StatsError(
    val name: String,
    val method: String,
    val error: String,
) {
    var occurrences: Long = 0

    companion object {
        private const val MAX_HASH_KEY_LENGTH = 128

        /**
         * Build an efficiently sized hash-key, for use in maps, because errors could be large strings.
         */
        fun buildKey(
            name: String,
            method: String,
            error: String,
        ): String {
            val simpleHashKeyLength = name.length + method.length + error.length
            return if (simpleHashKeyLength < MAX_HASH_KEY_LENGTH) {
                StringBuilder(simpleHashKeyLength)
                    .append(name)
                    .append(method)
                    .append(error)
                    .toString()
            } else {
                // on modern CPUs, SHA-1 is the fastest hash algorithm
                val messageDigest = MessageDigest.getInstance("SHA-1")
                messageDigest.update(name.toByteArray())
                messageDigest.update(method.toByteArray())
                val bytes = messageDigest.digest(error.toByteArray())
                HexFormat.of().formatHex(bytes)
            }
        }
    }

    fun toMap(): Map<String, Any> =
        mapOf(
            "name" to name,
            "method" to method,
            "error" to error,
            "occurrences" to occurrences,
        )
}
