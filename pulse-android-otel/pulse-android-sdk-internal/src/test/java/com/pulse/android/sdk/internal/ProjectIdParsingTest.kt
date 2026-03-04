package com.pulse.android.sdk.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ProjectIdParsingTest {
    @Test
    fun `when project ID contains hyphen, should return prefix before hyphen`() {
        val projectId = "tenant123-7876796bhbghb"
        val result = PulseSDKInternal.extractProjectID(projectId)
        assertThat(result).isEqualTo("tenant123")
    }

    @Test
    fun `when project ID has no hyphen, should return original ID`() {
        val projectId = "simpleid"
        val result = PulseSDKInternal.extractProjectID(projectId)
        assertThat(result).isEqualTo("simpleid")
    }

    @Test
    fun `when project ID contains multiple hyphens, should return prefix before first hyphen`() {
        val projectId = "tenant123-7876796bhbghb-extra-suffix"
        val result = PulseSDKInternal.extractProjectID(projectId)
        assertThat(result).isEqualTo("tenant123")
    }

    @Test
    fun `when hyphen is at start of project ID, should return original ID`() {
        val projectId = "-tenant123"
        val result = PulseSDKInternal.extractProjectID(projectId)
        assertThat(result).isEqualTo("-tenant123")
    }

    @Test
    fun `when project ID is empty string, should return empty string`() {
        val projectId = ""
        val result = PulseSDKInternal.extractProjectID(projectId)
        assertThat(result).isEqualTo("")
    }

    @Test
    fun `when project ID has single character before hyphen, should return that character`() {
        val projectId = "a-123456"
        val result = PulseSDKInternal.extractProjectID(projectId)
        assertThat(result).isEqualTo("a")
    }

    @Test
    fun `when project ID is only hyphen, should return hyphen`() {
        val projectId = "-"
        val result = PulseSDKInternal.extractProjectID(projectId)
        assertThat(result).isEqualTo("-")
    }

    @Test
    fun `when project ID contains numbers and letters, should return prefix correctly`() {
        val projectId = "project123-abc456def"
        val result = PulseSDKInternal.extractProjectID(projectId)
        assertThat(result).isEqualTo("project123")
    }

    @Test
    fun `when project ID prefix contains underscores, should return prefix with underscores`() {
        val projectId = "tenant_123-7876796bhbghb"
        val result = PulseSDKInternal.extractProjectID(projectId)
        assertThat(result).isEqualTo("tenant_123")
    }
}
