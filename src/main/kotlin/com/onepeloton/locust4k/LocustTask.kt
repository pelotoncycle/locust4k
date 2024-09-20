package com.onepeloton.locust4k

import kotlin.coroutines.CoroutineContext

/**
 * Locust Worker test scenario interface. Each Locust "user" results in one instance of this class being created on each
 * Worker node. Classes implementing this interface must handle their own thread safety (e.g., class variables or
 * objects shared between instances).
 */
interface LocustTask {
    /**
     * Task name.
     */
    fun name(): String

    /**
     * Optional callback function that is invoked once before the task execution loop starts.
     *
     * All code must be non-blocking and the given [context] should be used to launch child coroutines.
     */
    suspend fun beforeExecuteLoop(context: CoroutineContext) {}

    /**
     * Optional callback function that is invoked once after the task execution loop completes.
     *
     * All code must be non-blocking and the given [context] should be used to launch child coroutines.
     */
    suspend fun afterExecuteLoop(context: CoroutineContext) {}

    /**
     * Execute the task scenario (e.g., issue HTTP requests) in the task execution loop, and report
     * scenario result to [reporter].
     *
     * All code must be non-blocking and the given [context] should be used to launch child coroutines.
     */
    suspend fun execute(
        reporter: LocustTaskReporter,
        context: CoroutineContext,
    )

    /**
     * Builds an instance of this task, where each "Locust user" is a distinct instance. Instances must be
     * thread-safe.
     */
    fun instance(): LocustTask
}
