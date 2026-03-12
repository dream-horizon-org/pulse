package com.pulse.android.sdk.replay

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionReplayConfigurationTest {

    @Test
    fun `getConfigIfConfigured returns null when not configured`() {
        val config = SessionReplayConfiguration()
        assertThat(config.getConfigIfConfigured()).isNull()
    }

    @Test
    fun `getConfigIfConfigured returns config with SDK defaults after markConfigured`() {
        val config = SessionReplayConfiguration()
        config.markConfigured()
        val built = config.getConfigIfConfigured()
        assertThat(built).isNotNull
        assertThat(built!!.textAndInputPrivacy).isEqualTo(TextAndInputPrivacy.MASK_ALL)
        assertThat(built.imagePrivacy).isEqualTo(ImagePrivacy.MASK_ALL)
        assertThat(built.screenshot).isTrue()
        assertThat(built.throttleDelayMs).isEqualTo(1000L)
        assertThat(built.drawableConverter).isNull()
        assertThat(built.maskViewClasses).isEmpty()
        assertThat(built.unmaskViewClasses).isEmpty()
    }

    @Test
    fun `getConfigIfConfigured applies code-level configs from DSL`() {
        val converter = DrawableConverter { null }
        val config = SessionReplayConfiguration().apply {
            drawableConverter = converter
            addMaskViewClass("com.example.SecretView")
            addUnmaskViewClass("com.example.PublicView")
        }
        config.markConfigured()
        val built = config.getConfigIfConfigured()
        assertThat(built).isNotNull
        assertThat(built!!.drawableConverter).isSameAs(converter)
        assertThat(built.maskViewClasses).containsExactly("com.example.SecretView")
        assertThat(built.unmaskViewClasses).containsExactly("com.example.PublicView")
    }

    @Test
    fun `config is immutable - copy produces independent instance`() {
        val original = SessionReplayConfig(
            textAndInputPrivacy = TextAndInputPrivacy.MASK_ALL,
            imagePrivacy = ImagePrivacy.MASK_ALL,
        )
        val modified = original.copy(textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS)
        assertThat(original.textAndInputPrivacy).isEqualTo(TextAndInputPrivacy.MASK_ALL)
        assertThat(modified.textAndInputPrivacy).isEqualTo(TextAndInputPrivacy.MASK_SENSITIVE_INPUTS)
    }
}
