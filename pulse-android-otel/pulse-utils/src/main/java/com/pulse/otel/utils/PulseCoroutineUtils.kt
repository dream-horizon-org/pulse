package com.pulse.otel.utils

import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// taken from https://github.com/Kotlin/kotlinx.coroutines/blob/master/integration/kotlinx-coroutines-play-services/src/Tasks.kt

/**
 * Awaits the completion of the task without blocking a thread.
 *
 * This suspending function is cancellable.
 * If the [kotlinx.coroutines.Job] of the current coroutine is cancelled while this suspending function is waiting, this function
 * stops waiting for the completion stage and immediately resumes with [CancellationException].
 *
 * For bi-directional cancellation, an overload that accepts [CancellationTokenSource] can be used.
 */
public suspend fun <T> Task<T>.await(): T = awaitImpl(null)

/**
 * Awaits the completion of the task that is linked to the given [CancellationTokenSource] to control cancellation.
 *
 * This suspending function is cancellable and cancellation is bi-directional:
 * - If the [kotlinx.coroutines.Job] of the current coroutine is cancelled while this suspending function is waiting, this function
 * cancels the [cancellationTokenSource] and throws a [CancellationException].
 * - If the task is cancelled, then this function will throw a [CancellationException].
 *
 * Providing a [CancellationTokenSource] that is unrelated to the receiving [Task] is not supported and
 * leads to an unspecified behaviour.
 */
@ExperimentalCoroutinesApi // Since 1.5.1, tentatively until 1.6.0
public suspend fun <T> Task<T>.await(cancellationTokenSource: CancellationTokenSource): T =
    awaitImpl(cancellationTokenSource)

private suspend fun <T> Task<T>.awaitImpl(cancellationTokenSource: CancellationTokenSource?): T {
    // fast path
    if (isComplete) {
        val e = exception
        return if (e == null) {
            if (isCanceled) {
                throw CancellationException("Task $this was cancelled normally.")
            } else {
                @Suppress("UNCHECKED_CAST")
                result as T
            }
        } else {
            throw e
        }
    }

    return suspendCancellableCoroutine { cont ->
        // Run the callback directly to avoid unnecessarily scheduling on the main thread.
        addOnCompleteListener(DirectExecutor) {
            val e = it.exception
            if (e == null) {
                @Suppress("UNCHECKED_CAST")
                if (it.isCanceled) cont.cancel() else cont.resume(it.result as T)
            } else {
                cont.resumeWithException(e)
            }
        }

        if (cancellationTokenSource != null) {
            cont.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
        }
    }
}

/**
 * An [Executor] that just directly executes the [Runnable].
 */
private object DirectExecutor : Executor {
    override fun execute(r: Runnable) {
        r.run()
    }
}
