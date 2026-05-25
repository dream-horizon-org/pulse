/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.android.internal.services.visiblescreen.VisibleScreenState
import io.opentelemetry.android.internal.services.visiblescreen.VisibleScreenTracker
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import kotlinx.coroutines.flow.MutableStateFlow
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

internal class ScreenAttributesSpanProcessorTest {
    @Test
    fun append() {
        val screenName = "my cool screen"
        val visibleScreenTracker = mockk<VisibleScreenTracker>()
        val contenxt = mockk<Context>()
        val span = mockk<ReadWriteSpan>()

        every { visibleScreenTracker.visibleScreenState } returns
            MutableStateFlow(VisibleScreenState(screenName, null, null, null, null, null))
        every { span.attributes } returns Attributes.empty()
        every { span.setAttribute(any<AttributeKey<String>>(), any()) } returns span

        val testClass =
            ScreenAttributesSpanProcessor(visibleScreenTracker)
        assertThat(testClass.isStartRequired).isTrue()
        assertThat(testClass.isEndRequired).isFalse()
        assertThatCode {
            testClass.onEnd(mockk<ReadableSpan>())
        }.doesNotThrowAnyException()

        testClass.onStart(contenxt, span)
        verify {
            span.setAttribute(RumConstants.SCREEN_NAME_KEY, screenName)
        }
    }
}
