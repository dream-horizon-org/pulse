/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.BatteryManager
import android.os.Build
import com.pulse.semconv.PulseDeviceAttributes
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AndroidResourceTest {
    private val appName: String = "robotron"
    private val rumSdkVersion: String = BuildConfig.OTEL_ANDROID_VERSION
    private val systemTotalMemoryBytes: Long = 8_589_934_592L
    private val batteryChargeCounterUah: Long = 2_500L
    private val batteryCapacityPercent: Int = 50
    private val expectedBatteryFullCapacityUah: Long = 5_000L

    @RelaxedMockK
    private lateinit var app: Application

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        stubSystemServices(
            totalMem = systemTotalMemoryBytes,
            chargeCounterUah = batteryChargeCounterUah,
            capacityPercent = batteryCapacityPercent,
        )
    }

    private fun stubSystemServices(
        totalMem: Long,
        chargeCounterUah: Long?,
        capacityPercent: Int?,
    ) {
        val activityManager = mockk<ActivityManager>()
        every { activityManager.getMemoryInfo(any()) } answers {
            firstArg<ActivityManager.MemoryInfo>().totalMem = totalMem
        }
        val batteryManager = mockk<BatteryManager>()
        if (capacityPercent != null && chargeCounterUah != null) {
            every { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns capacityPercent
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                every {
                    batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                } returns chargeCounterUah
            } else {
                every {
                    batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                } returns chargeCounterUah.toInt()
            }
        }
        every { app.getSystemService(any()) } answers {
            when (firstArg<String>()) {
                Context.ACTIVITY_SERVICE -> activityManager
                Context.BATTERY_SERVICE -> batteryManager
                else -> null
            }
        }
    }

    @Test
    fun testFullResource() {
        val appInfo =
            ApplicationInfo().apply {
                labelRes = 12345
            }

        every { app.applicationInfo } returns appInfo
        every { app.applicationContext.getString(appInfo.labelRes) } returns appName

        val expected =
            Resource
                .getDefault()
                .merge(
                    Resource
                        .builder()
                        .put(ServiceAttributes.SERVICE_NAME, appName)
                        .put(ServiceAttributes.SERVICE_VERSION, "_0")
                        .put(RumConstants.RUM_SDK_VERSION, rumSdkVersion)
                        .put(DeviceIncubatingAttributes.DEVICE_MODEL_NAME, Build.MODEL.orEmpty())
                        .put(DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER, Build.MODEL.orEmpty())
                        .put(DeviceIncubatingAttributes.DEVICE_MANUFACTURER, Build.MANUFACTURER)
                        .put(OsIncubatingAttributes.OS_NAME, "Android")
                        .put(OsIncubatingAttributes.OS_TYPE, "linux")
                        .put(OsIncubatingAttributes.OS_VERSION, Build.VERSION.RELEASE)
                        .put(OsIncubatingAttributes.OS_DESCRIPTION, Build.DISPLAY)
                        .put(RumConstants.Android.OS_API_LEVEL, Build.VERSION.SDK_INT.toString())
                        .put(RumConstants.App.BUILD_ID, "0")
                        .put(RumConstants.App.BUILD_NAME, "_0")
                        .put(PulseDeviceAttributes.PULSE_SYSTEM_MEMORY_SIZE, systemTotalMemoryBytes)
                        .put(
                            PulseDeviceAttributes.PULSE_SYSTEM_BATTERY_CAPACITY_UAH,
                            expectedBatteryFullCapacityUah,
                        ).build(),
                )

        val result = AndroidResource.createDefault(app)
        assertEquals(expected, result)
    }

    @Test
    fun `fall back to nonLocalizedLabel if needed`() {
        val appInfo =
            ApplicationInfo().apply {
                labelRes = 0
                nonLocalizedLabel = "shim sham"
            }

        every { app.applicationContext.applicationInfo } returns appInfo
        every { app.applicationInfo } returns appInfo

        val expected =
            Resource
                .getDefault()
                .merge(
                    Resource
                        .builder()
                        .put(ServiceAttributes.SERVICE_NAME, "shim sham")
                        .put(ServiceAttributes.SERVICE_VERSION, "_0")
                        .put(RumConstants.RUM_SDK_VERSION, rumSdkVersion)
                        .put(DeviceIncubatingAttributes.DEVICE_MODEL_NAME, Build.MODEL.orEmpty())
                        .put(DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER, Build.MODEL.orEmpty())
                        .put(DeviceIncubatingAttributes.DEVICE_MANUFACTURER, Build.MANUFACTURER)
                        .put(OsIncubatingAttributes.OS_NAME, "Android")
                        .put(OsIncubatingAttributes.OS_TYPE, "linux")
                        .put(OsIncubatingAttributes.OS_VERSION, Build.VERSION.RELEASE)
                        .put(OsIncubatingAttributes.OS_DESCRIPTION, Build.DISPLAY)
                        .put(RumConstants.Android.OS_API_LEVEL, Build.VERSION.SDK_INT.toString())
                        .put(RumConstants.App.BUILD_ID, "0")
                        .put(RumConstants.App.BUILD_NAME, "_0")
                        .put(PulseDeviceAttributes.PULSE_SYSTEM_MEMORY_SIZE, systemTotalMemoryBytes)
                        .put(
                            PulseDeviceAttributes.PULSE_SYSTEM_BATTERY_CAPACITY_UAH,
                            expectedBatteryFullCapacityUah,
                        ).build(),
                )

        val result = AndroidResource.createDefault(app)
        assertEquals(expected, result)
    }

    @Test
    fun testProblematicContext() {
        every { app.getSystemService(any()) } returns null
        every { app.applicationContext.applicationInfo } throws SecurityException("cannot do that")
        every { app.applicationContext.resources } throws SecurityException("boom")

        val expected =
            Resource
                .getDefault()
                .merge(
                    Resource
                        .builder()
                        .put(ServiceAttributes.SERVICE_NAME, "unknown_service:android")
                        .put(ServiceAttributes.SERVICE_VERSION, "_0")
                        .put(RumConstants.RUM_SDK_VERSION, rumSdkVersion)
                        .put(DeviceIncubatingAttributes.DEVICE_MODEL_NAME, Build.MODEL.orEmpty())
                        .put(DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER, Build.MODEL.orEmpty())
                        .put(DeviceIncubatingAttributes.DEVICE_MANUFACTURER, Build.MANUFACTURER)
                        .put(OsIncubatingAttributes.OS_NAME, "Android")
                        .put(OsIncubatingAttributes.OS_TYPE, "linux")
                        .put(OsIncubatingAttributes.OS_VERSION, Build.VERSION.RELEASE)
                        .put(OsIncubatingAttributes.OS_DESCRIPTION, Build.DISPLAY)
                        .put(RumConstants.Android.OS_API_LEVEL, Build.VERSION.SDK_INT.toString())
                        .put(RumConstants.App.BUILD_ID, "0")
                        .put(RumConstants.App.BUILD_NAME, "_0")
                        .build(),
                )

        val result = AndroidResource.createDefault(app)
        assertEquals(expected, result)
    }
}
