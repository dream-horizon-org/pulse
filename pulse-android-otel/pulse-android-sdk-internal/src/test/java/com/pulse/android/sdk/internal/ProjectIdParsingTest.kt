package com.pulse.android.sdk.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ProjectIdParsingTest {
    @Test
    fun `when API key contains underscore, should return prefix before last underscore`() {
        val apiKey = "test_project-XwzBrFCb_fYJmt8hy0wmZcXvDq3DGRn7x"
        val result = PulseSDKInternal.extractProjectID(apiKey)
        assertThat(result).isEqualTo("test_project-XwzBrFCb")
    }

    @Test
    fun `when API key has no underscore, should return original ID`() {
        val apiKey = "simpleid"
        val result = PulseSDKInternal.extractProjectID(apiKey)
        assertThat(result).isEqualTo("simpleid")
    }

    @Test
    fun `when API key contains multiple underscores, should return prefix before last underscore`() {
        val apiKey = "project_name_random_secret"
        val result = PulseSDKInternal.extractProjectID(apiKey)
        assertThat(result).isEqualTo("project_name_random")
    }

    @Test
    fun `when underscore is at start of API key, should return original ID`() {
        val apiKey = "_project123"
        val result = PulseSDKInternal.extractProjectID(apiKey)
        assertThat(result).isEqualTo("_project123")
    }

    @Test
    fun `when API key is empty string, should return empty string`() {
        val apiKey = ""
        val result = PulseSDKInternal.extractProjectID(apiKey)
        assertThat(result).isEqualTo("")
    }

    @Test
    fun `when API key has single character before last underscore, should return that character`() {
        val apiKey = "a_secret"
        val result = PulseSDKInternal.extractProjectID(apiKey)
        assertThat(result).isEqualTo("a")
    }

    @Test
    fun `when API key is only underscore, should return underscore string`() {
        val apiKey = "_"
        val result = PulseSDKInternal.extractProjectID(apiKey)
        assertThat(result).isEqualTo("_")
    }

    @Test
    fun `when API key contains project ID with hyphen and secret, should return project ID correctly`() {
        val apiKey = "test_project-XwzBrFCb_fYJmt8hy0wmZcXvDq3DGRn7x"
        val result = PulseSDKInternal.extractProjectID(apiKey)
        assertThat(result).isEqualTo("test_project-XwzBrFCb")
    }

    @Test
    fun `when API key contains underscores in project ID part, should return all before last underscore`() {
        val apiKey = "tenant_123_secret456"
        val result = PulseSDKInternal.extractProjectID(apiKey)
        assertThat(result).isEqualTo("tenant_123")
    }

    @Test
    fun `when API key ends with underscore, should return prefix correctly`() {
        val apiKey = "project123_"
        val result = PulseSDKInternal.extractProjectID(apiKey)
        assertThat(result).isEqualTo("project123")
    }
}
