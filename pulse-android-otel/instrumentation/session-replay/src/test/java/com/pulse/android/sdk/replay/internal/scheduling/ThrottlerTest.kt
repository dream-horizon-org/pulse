package com.pulse.android.sdk.replay.internal.scheduling

import android.os.Looper
import com.pulse.android.sdk.replay.internal.util.DateProvider
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
    private lateinit var dateProvider: FakeDateProvider
    private lateinit var throttler: Throttler

    @Before
    fun setUp() {
        dateProvider = FakeDateProvider()
        val handler = android.os.Handler(Looper.getMainLooper())
        throttler = Throttler(handler, dateProvider, throttleDelayMs = 500L)
    }

    @Test
    fun `first call executes immediately when enough time has passed`() {
        dateProvider.currentNanos = TimeUnit.MILLISECONDS.toNanos(500L)
        val executed = AtomicInteger(0)
        throttler.throttle(Runnable { executed.incrementAndGet() })
        ShadowLooper.idleMainLooper(1, TimeUnit.MILLISECONDS)
        assertThat(executed.get()).isEqualTo(1)
    }

    @Test
    fun `second call within throttle delay is deferred not executed immediately`() {
        dateProvider.currentNanos = TimeUnit.MILLISECONDS.toNanos(500L)
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
        dateProvider.currentNanos = TimeUnit.MILLISECONDS.toNanos(500L)
        val executed = AtomicInteger(0)
        throttler.throttle(Runnable { executed.incrementAndGet() })
        ShadowLooper.idleMainLooper(1, TimeUnit.MILLISECONDS)
        assertThat(executed.get()).isEqualTo(1)

        throttler.throttle(Runnable { executed.incrementAndGet() })
        dateProvider.currentNanos = TimeUnit.MILLISECONDS.toNanos(1000L)
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        assertThat(executed.get()).isEqualTo(2)
    }

    @Test
    fun `rapid calls do not cause double execution`() {
        dateProvider.currentNanos = TimeUnit.MILLISECONDS.toNanos(500L)
        val executed = AtomicInteger(0)
        throttler.throttle(Runnable { executed.incrementAndGet() })
        throttler.throttle(Runnable { executed.incrementAndGet() })
        throttler.throttle(Runnable { executed.incrementAndGet() })
        ShadowLooper.idleMainLooper(1, TimeUnit.MILLISECONDS)
        assertThat(executed.get()).isEqualTo(1)

        dateProvider.currentNanos = TimeUnit.MILLISECONDS.toNanos(1000L)
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        assertThat(executed.get()).isEqualTo(2)
    }

    @Test
    fun `after throttle period expires next call runs immediately again`() {
        dateProvider.currentNanos = TimeUnit.MILLISECONDS.toNanos(500L)
        val executed = AtomicInteger(0)
        throttler.throttle(Runnable { executed.incrementAndGet() })
        ShadowLooper.idleMainLooper(1, TimeUnit.MILLISECONDS)
        assertThat(executed.get()).isEqualTo(1)

        dateProvider.currentNanos = TimeUnit.MILLISECONDS.toNanos(1500L)
        throttler.throttle(Runnable { executed.incrementAndGet() })
        ShadowLooper.idleMainLooper(1, TimeUnit.MILLISECONDS)
        assertThat(executed.get()).isEqualTo(2)
    }

    private class FakeDateProvider : DateProvider {
        var currentNanos = 0L

        override fun currentTimeMillis(): Long = currentNanos / 1_000_000

        override fun nanoTime(): Long = currentNanos
    }
}
