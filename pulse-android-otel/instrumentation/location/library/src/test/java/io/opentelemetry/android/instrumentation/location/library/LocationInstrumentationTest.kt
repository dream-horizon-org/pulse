package io.opentelemetry.android.instrumentation.location.library

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.android.instrumentation.location.processors.LocationInstrumentationConstants
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class LocationInstrumentationTest {
    private lateinit var instrumentation: LocationInstrumentation
    private lateinit var application: Application
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences

    @BeforeEach
    fun setUp() {
        instrumentation = LocationInstrumentation()
        application = mockk(relaxed = true)
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)

        every { application.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { application.applicationContext } returns context
    }

    @Test
    fun `instrumentation has correct name`() {
        assertThat(instrumentation.name).isEqualTo(LocationInstrumentationConstants.INSTRUMENTATION_NAME)
    }
}
