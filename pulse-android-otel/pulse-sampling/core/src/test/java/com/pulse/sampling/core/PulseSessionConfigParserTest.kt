package com.pulse.sampling.core

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import com.pulse.sampling.models.PulseDeviceAttributeName
import com.pulse.sampling.models.PulseSdkConfigFakeUtils
import com.pulse.sampling.models.PulseSdkName
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class PulseSessionConfigParserTest {
    private lateinit var context: Context
    private lateinit var parser: PulseSessionParser
    private val currentSdkName = PulseSdkName.ANDROID_JAVA

    @BeforeEach
    fun setUp() {
        context = mockk<Context>(relaxed = true)
        parser = PulseSessionConfigParser()
    }

    @Nested
    inner class `When no rules exist` {
        @Test
        fun `returns default session sample rate`() {
            val defaultRate = 0.5f
            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules = emptyList(),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(defaultRate)
        }
    }

    @Nested
    inner class `When rules exist but none match` {
        @Test
        fun `returns default when SDK name does not match`() {
            val defaultRate = 0.3f
            val ruleRate = 0.8f
            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.PLATFORM,
                                value = ".*",
                                sdks = setOf(PulseSdkName.IOS_SWIFT),
                                sessionSampleRate = ruleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(defaultRate)
        }

        @Test
        fun `returns default when context does not match`() {
            val defaultRate = 0.4f
            val ruleRate = 0.9f
            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.PLATFORM,
                                value = "ios.*",
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = ruleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(defaultRate)
        }

        @Test
        fun `returns default when both SDK and context do not match`() {
            val defaultRate = 0.2f
            val ruleRate = 0.7f
            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.PLATFORM,
                                value = "ios.*",
                                sdks = setOf(PulseSdkName.IOS_SWIFT),
                                sessionSampleRate = ruleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(defaultRate)
        }
    }

    @Nested
    inner class `When a rule matches` {
        @Test
        fun `returns rule's session sample rate when SDK and context match`() {
            val defaultRate = 0.1f
            val ruleRate = 0.75f
            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.PLATFORM,
                                value = "android.*",
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = ruleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(ruleRate)
        }

        @Test
        fun `returns first matching rule's rate when multiple rules exist`() {
            val defaultRate = 0.1f
            val firstRuleRate = 0.6f
            val secondRuleRate = 0.9f
            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.PLATFORM,
                                value = "android.*",
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = firstRuleRate,
                            ),
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.PLATFORM,
                                value = "android.*",
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = secondRuleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(firstRuleRate)
        }

        @Test
        fun `skips non-matching rules and returns first matching rule`() {
            val defaultRate = 0.1f
            val matchingRuleRate = 0.85f
            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.PLATFORM,
                                value = "ios.*",
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = 0.5f,
                            ),
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.PLATFORM,
                                value = "android.*",
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = matchingRuleRate,
                            ),
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.PLATFORM,
                                value = "android.*",
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = 0.95f,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(matchingRuleRate)
        }

        @Test
        fun `skips rules with wrong SDK even if context would match`() {
            val defaultRate = 0.1f
            val matchingRuleRate = 0.7f
            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.PLATFORM,
                                value = "android.*",
                                sdks = setOf(PulseSdkName.IOS_SWIFT),
                                sessionSampleRate = 0.5f,
                            ),
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.PLATFORM,
                                value = "android.*",
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = matchingRuleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(matchingRuleRate)
        }
    }

    @Nested
    inner class `When multiple SDKs are specified in rule` {
        @Test
        fun `matches when current SDK is in the set`() {
            val defaultRate = 0.1f
            val ruleRate = 0.65f
            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.PLATFORM,
                                value = "android.*",
                                sdks =
                                    setOf(
                                        PulseSdkName.IOS_SWIFT,
                                        PulseSdkName.ANDROID_JAVA,
                                        PulseSdkName.ANDROID_RN,
                                    ),
                                sessionSampleRate = ruleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(ruleRate)
        }

        @Test
        fun `does not match when current SDK is not in the set`() {
            val defaultRate = 0.1f
            val ruleRate = 0.65f
            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.PLATFORM,
                                value = "android.*",
                                sdks =
                                    setOf(
                                        PulseSdkName.IOS_SWIFT,
                                        PulseSdkName.ANDROID_RN,
                                    ),
                                sessionSampleRate = ruleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(defaultRate)
        }
    }

    @Nested
    inner class `When rules use OS_VERSION attribute` {
        @Test
        fun `returns rule's rate when OS version matches`() {
            val defaultRate = 0.1f
            val ruleRate = 0.8f
            val sdkInt = Build.VERSION.SDK_INT.toString()
            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.OS_VERSION,
                                value = sdkInt,
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = ruleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(ruleRate)
        }

        @Test
        fun `returns default when OS version does not match`() {
            val defaultRate = 0.2f
            val ruleRate = 0.9f
            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.OS_VERSION,
                                value = "999",
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = ruleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(defaultRate)
        }

        @Test
        fun `returns rule's rate when OS version matches regex pattern`() {
            val defaultRate = 0.1f
            val ruleRate = 0.7f
            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.OS_VERSION,
                                value = "\\d+",
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = ruleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(ruleRate)
        }
    }

    @Nested
    inner class `When rules use APP_VERSION attribute` {
        @Test
        fun `returns rule's rate when app version matches`() {
            val defaultRate = 0.1f
            val ruleRate = 0.85f
            val appVersion = "1.2.3"
            val packageName = "com.test.app"

            val packageManager = mockk<PackageManager>(relaxed = true)
            val packageInfo = mockk<PackageInfo>(relaxed = true)
            packageInfo.versionName = appVersion

            every { context.packageManager } returns packageManager
            every { context.packageName } returns packageName
            every { packageManager.getPackageInfo(packageName, 0) } returns packageInfo

            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.APP_VERSION,
                                value = appVersion,
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = ruleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(ruleRate)
        }

        @Test
        fun `returns default when app version does not match`() {
            val defaultRate = 0.2f
            val ruleRate = 0.9f
            val appVersion = "1.2.3"
            val packageName = "com.test.app"

            val packageManager = mockk<PackageManager>(relaxed = true)
            val packageInfo = mockk<PackageInfo>(relaxed = true)
            packageInfo.versionName = appVersion

            every { context.packageManager } returns packageManager
            every { context.packageName } returns packageName
            every { packageManager.getPackageInfo(packageName, 0) } returns packageInfo

            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.APP_VERSION,
                                value = "2.0.0",
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = ruleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(defaultRate)
        }
    }

    @Nested
    inner class `When rules use COUNTRY attribute` {
        @Test
        fun `returns rule's rate when country matches`() {
            val defaultRate = 0.1f
            val ruleRate = 0.8f
            val countryCode = "US"

            val telephonyManager = mockk<TelephonyManager>(relaxed = true)
            every { telephonyManager.networkCountryIso } returns countryCode
            every { context.getSystemService(Context.TELEPHONY_SERVICE) } returns telephonyManager

            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.COUNTRY,
                                value = countryCode,
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = ruleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(ruleRate)
        }

        @Test
        fun `returns default when country does not match`() {
            val defaultRate = 0.2f
            val ruleRate = 0.9f
            val countryCode = "US"

            val telephonyManager = mockk<TelephonyManager>(relaxed = true)
            every { telephonyManager.networkCountryIso } returns countryCode
            every { context.getSystemService(Context.TELEPHONY_SERVICE) } returns telephonyManager

            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.COUNTRY,
                                value = "CA",
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = ruleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(defaultRate)
        }

        @Test
        fun `returns default when telephony manager is null`() {
            val defaultRate = 0.3f
            val ruleRate = 0.85f

            every { context.getSystemService(Context.TELEPHONY_SERVICE) } returns null

            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.COUNTRY,
                                value = ".*",
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = ruleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(defaultRate)
        }
    }

    @Nested
    inner class `When rules use UNKNOWN attribute` {
        @Test
        fun `returns default when unknown attribute is used`() {
            val defaultRate = 0.5f
            val ruleRate = 0.95f

            val config =
                PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                    default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                    rules =
                        listOf(
                            PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                                name = PulseDeviceAttributeName.UNKNOWN,
                                value = ".*",
                                sdks = setOf(PulseSdkName.ANDROID_JAVA),
                                sessionSampleRate = ruleRate,
                            ),
                        ),
                )

            val result = parser.parses(context, config, currentSdkName)

            assertThat(result).isEqualTo(defaultRate)
        }
    }

    @Test
    fun `returns first matching rule across different attribute types`() {
        val defaultRate = 0.1f
        val osVersionRate = 0.6f
        val appVersionRate = 0.8f
        val sdkInt = Build.VERSION.SDK_INT.toString()
        val appVersion = "2.0.0"
        val packageName = "com.test.app"

        val packageManager = mockk<PackageManager>(relaxed = true)
        val packageInfo = mockk<PackageInfo>(relaxed = true)
        packageInfo.versionName = appVersion

        every { context.packageManager } returns packageManager
        every { context.packageName } returns packageName
        every { packageManager.getPackageInfo(packageName, 0) } returns packageInfo

        val config =
            PulseSdkConfigFakeUtils.createFakeSamplingConfig(
                default = PulseSdkConfigFakeUtils.createFakeDefaultSamplingConfig(sessionSampleRate = defaultRate),
                rules =
                    listOf(
                        PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                            name = PulseDeviceAttributeName.OS_VERSION,
                            value = sdkInt,
                            sdks = setOf(PulseSdkName.ANDROID_JAVA),
                            sessionSampleRate = osVersionRate,
                        ),
                        PulseSdkConfigFakeUtils.createFakeSessionSamplingRule(
                            name = PulseDeviceAttributeName.APP_VERSION,
                            value = appVersion,
                            sdks = setOf(PulseSdkName.ANDROID_JAVA),
                            sessionSampleRate = appVersionRate,
                        ),
                    ),
            )

        val result = parser.parses(context, config, currentSdkName)

        assertThat(result).isEqualTo(osVersionRate)
    }
}
