/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pulse.otel.utils

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class TaskAwaitTest {
    private val standardTestDispatcher =
        StandardTestDispatcher(name = "InteractionManagerTest\$standardTestDispatcher")

    @Test
    fun `await returns result when task succeeds`() =
        runTest(standardTestDispatcher) {
            val expectedResult = "test-result"
            val task = Tasks.forResult(expectedResult)

            val result = task.await()

            assertThat(result).isEqualTo(expectedResult)
        }

    @Test
    fun `await handles null result type`() =
        runTest(standardTestDispatcher) {
            val task = Tasks.forResult<String?>(null)

            val result = task.await()

            assertThat(result).isNull()
        }

    @Test
    fun `await throws exception when task fails`() =
        runTest(standardTestDispatcher) {
            val exception = RuntimeException("Task failed")
            val task = Tasks.forException<String>(exception)

            try {
                task.await()
                Assertions.fail<Nothing>("Expected exception was not thrown")
            } catch (e: RuntimeException) {
                assertThat(e).isInstanceOf(RuntimeException::class.java)
                assertThat(e.message).isEqualTo("Task failed")
            }
        }

    @Test
    fun `await does not resume if continuation is cancelled before task completes`() =
        runTest(standardTestDispatcher) {
            val task = createDelayedSuccessTask("resultCanceled")

            var receivedResult: String? = null

            val job =
                launch {
                    receivedResult = task.await()
                }

            // Cancel the coroutine before task completes
            delay(10)
            job.cancel()

            // Wait a bit for any callbacks
            delay(300)

            // Since we cancelled before task completed, we should not have received the result
            // The continuation.isActive check should have prevented resuming
            assertThat(receivedResult).isNull()
        }

    @Test
    fun `await resume with the value after delay`() =
        runTest(standardTestDispatcher) {
            val task = createDelayedSuccessTask("resultSuccess")

            var receivedResult: String? = null
            launch {
                receivedResult = task.await()
            }

            // Cancel the coroutine before task completes
            delay(300)

            // Verify that the result was received after the delay
            assertThat(receivedResult).isEqualTo("resultSuccess")
        }

    @Test
    fun `await handles multiple sequential calls`() =
        runTest(standardTestDispatcher) {
            val task1 = Tasks.forResult("result1")
            val task2 = Tasks.forResult("result2")
            val task3 = Tasks.forResult("result3")

            val result1 = task1.await()
            val result2 = task2.await()
            val result3 = task3.await()

            assertThat(result1).isEqualTo("result1")
            assertThat(result2).isEqualTo("result2")
            assertThat(result3).isEqualTo("result3")
        }

    private fun createDelayedSuccessTask(result: String): com.google.android.gms.tasks.Task<String> {
        val taskCompletionSource = TaskCompletionSource<String>()

        // Complete the task after a delay
        CoroutineScope(standardTestDispatcher).launch {
            delay(200)
            taskCompletionSource.setResult(result)
        }

        return taskCompletionSource.task
    }
}
