package com.pulse.android.sdk.replay.internal.capture

import android.text.InputType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class InputTypeClassifierTest {
    @Nested
    inner class IsPasswordInputType {
        @Test
        fun `returns true for TYPE_TEXT_VARIATION_PASSWORD`() {
            assertThat(
                InputTypeClassifier.isPasswordInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
            ).isTrue()
        }

        @Test
        fun `returns true for TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`() {
            assertThat(
                InputTypeClassifier.isPasswordInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
            ).isTrue()
        }

        @Test
        fun `returns true for TYPE_TEXT_VARIATION_WEB_PASSWORD`() {
            assertThat(
                InputTypeClassifier.isPasswordInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD),
            ).isTrue()
        }

        @Test
        fun `returns true for TYPE_CLASS_NUMBER with TYPE_NUMBER_VARIATION_PASSWORD`() {
            assertThat(
                InputTypeClassifier.isPasswordInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD),
            ).isTrue()
        }

        @Test
        fun `returns false for TYPE_CLASS_TEXT normal text`() {
            assertThat(InputTypeClassifier.isPasswordInputType(InputType.TYPE_CLASS_TEXT)).isFalse()
        }

        @Test
        fun `returns false for 0`() {
            assertThat(InputTypeClassifier.isPasswordInputType(0)).isFalse()
        }
    }

    @Nested
    inner class IsSensitiveInputType {
        @Test
        fun `returns true for TYPE_TEXT_VARIATION_PASSWORD`() {
            assertThat(
                InputTypeClassifier.isSensitiveInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
            ).isTrue()
        }

        @Test
        fun `returns true for TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`() {
            assertThat(
                InputTypeClassifier.isSensitiveInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
            ).isTrue()
        }

        @Test
        fun `returns true for TYPE_TEXT_VARIATION_WEB_PASSWORD`() {
            assertThat(
                InputTypeClassifier.isSensitiveInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD),
            ).isTrue()
        }

        @Test
        fun `returns true for TYPE_CLASS_NUMBER with TYPE_NUMBER_VARIATION_PASSWORD`() {
            assertThat(
                InputTypeClassifier.isSensitiveInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD),
            ).isTrue()
        }

        @Test
        fun `returns true for TYPE_TEXT_VARIATION_EMAIL_ADDRESS`() {
            assertThat(
                InputTypeClassifier.isSensitiveInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
            ).isTrue()
        }

        @Test
        fun `returns true for TYPE_CLASS_PHONE`() {
            assertThat(InputTypeClassifier.isSensitiveInputType(InputType.TYPE_CLASS_PHONE)).isTrue()
        }

        @Test
        fun `returns false for TYPE_CLASS_TEXT normal text`() {
            assertThat(InputTypeClassifier.isSensitiveInputType(InputType.TYPE_CLASS_TEXT)).isFalse()
        }

        @Test
        fun `returns false for 0`() {
            assertThat(InputTypeClassifier.isSensitiveInputType(0)).isFalse()
        }
    }
}
