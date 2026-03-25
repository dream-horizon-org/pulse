package com.pulse.android.sdk.replay.internal.scheduling

import android.os.Looper
import io.opentelemetry.sdk.testing.time.TestClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class ThrottlerTest {
    private lateinit var clock: TestClock
    private lateinit var throttler: Throttler

    @Before
    fun setUp() {
        clock = TestClock.create()
        val handler = android.os.Handler(Looper.getMainLooper())
        throttler = Throttler(handler, clock, throttleDelayMs = 500L)
    }

    @Test
    fun `first call executes immediately when enough time has passed`() {
        clock.advance(500, TimeUnit.MILLISECONDS)
        val executed = AtomicInteger(0)
        throttler.throttle(Runnable { executed.incrementAndGet() })
        ShadowLooper.idleMainLooper(1, TimeUnit.MILLISECONDS)
        assertThat(executed.get()).isEqualTo(1)
    }

    @Test
    fun `second call within throttle delay is deferred not executed immediately`() {
        clock.advance(500, TimeUnit.MILLISECONDS)
        val executed = AtomicInteger(0)
        throttler.throttle(Runnable { executed.incrementAndGet() })
        ShadowLooper.idleMainLooper(1, TimeUnit.MILLISECONDS)
        assertThat(executed.get()).isEqualTo(1)

        throttler.throttle(Runnable { executed.incrementAndGet() })
        ShadowLooper.idleMainLooper(1, TimeUnit.MILLISECONDS)
        assertThat(executed.get()).isEqualTo(1)
    }

    @Test
    fun `after advancing clock past delay deferred call executes`() {
        clock.advance(500, TimeUnit.MILLISECONDS)
        val executed = AtomicInteger(0)
        throttler.throttle(Runnable { executed.incrementAndGet() })
        ShadowLooper.idleMainLooper(1, TimeUnit.MILLISECONDS)
        assertThat(executed.get()).isEqualTo(1)

        throttler.throttle(Runnable { executed.incrementAndGet() })
        clock.advance(500, TimeUnit.MILLISECONDS)
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        assertThat(executed.get()).isEqualTo(2)
    }

    @Test
    fun `rapid calls do not cause double execution`() {
        clock.advance(500, TimeUnit.MILLISECONDS)
        val executed = AtomicInteger(0)
        throttler.throttle(Runnable { executed.incrementAndGet() })
        throttler.throttle(Runnable { executed.incrementAndGet() })
        throttler.throttle(Runnable { executed.incrementAndGet() })
        ShadowLooper.idleMainLooper(1, TimeUnit.MILLISECONDS)
        assertThat(executed.get()).isEqualTo(1)

        clock.advance(500, TimeUnit.MILLISECONDS)
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        assertThat(executed.get()).isEqualTo(2)
    }

    @Test
    fun `after throttle period expires next call runs immediately again`() {
        clock.advance(500, TimeUnit.MILLISECONDS)
        val executed = AtomicInteger(0)
        throttler.throttle(Runnable { executed.incrementAndGet() })
        ShadowLooper.idleMainLooper(1, TimeUnit.MILLISECONDS)
        assertThat(executed.get()).isEqualTo(1)

        clock.advance(1000, TimeUnit.MILLISECONDS)
        throttler.throttle(Runnable { executed.incrementAndGet() })
        ShadowLooper.idleMainLooper(1, TimeUnit.MILLISECONDS)
        assertThat(executed.get()).isEqualTo(2)
    }
}
