package com.pulse.android.sdk.replay.internal.capture

import android.view.View
import com.pulse.android.sdk.replay.SessionReplayConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class MaskRectCacheTest {
    private lateinit var cache: MaskRectCache
    private val logger: (String) -> Unit = {}

    @Before
    fun setUp() {
        cache = MaskRectCache(SessionReplayConfig(), logger)
    }

    @Test
    fun `isDirty returns true initially`() {
        assertThat(cache.isDirty()).isTrue
    }

    @Test
    fun `rects is empty and valid is false initially`() {
        assertThat(cache.rects).isEmpty()
        assertThat(cache.valid).isFalse
    }

    @Test
    fun `invalidate sets dirty to true`() {
        val view = View(RuntimeEnvironment.getApplication())
        cache.collectIfNeeded(view) { false }
        assertThat(cache.isDirty()).isFalse
        cache.invalidate()
        assertThat(cache.isDirty()).isTrue
    }

    @Test
    fun `clear resets rects to empty valid to false and dirty to true`() {
        cache.clear()
        assertThat(cache.rects).isEmpty()
        assertThat(cache.valid).isFalse
        assertThat(cache.isDirty()).isTrue
    }

    @Test
    fun `after clear isDirty returns true`() {
        val view = View(RuntimeEnvironment.getApplication())
        cache.collectIfNeeded(view) { false }
        cache.clear()
        assertThat(cache.isDirty()).isTrue
    }

    @Test
    fun `registerListeners does not crash with real view`() {
        val view = View(RuntimeEnvironment.getApplication())
        assertThatCode { cache.registerListeners(view) }.doesNotThrowAnyException()
    }

    @Test
    fun `unregisterListeners does not crash after registerListeners`() {
        val view = View(RuntimeEnvironment.getApplication())
        cache.registerListeners(view)
        assertThatCode { cache.unregisterListeners() }.doesNotThrowAnyException()
    }

    @Test
    fun `unregisterListeners does not crash when nothing registered`() {
        assertThatCode { cache.unregisterListeners() }.doesNotThrowAnyException()
    }
}
