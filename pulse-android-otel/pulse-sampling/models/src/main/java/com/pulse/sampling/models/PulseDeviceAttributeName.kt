package com.pulse.sampling.models

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.Keep
import com.pulse.otel.utils.PulseFallbackToUnknownEnumSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable(with = PulseDeviceAttributeNameSerializer::class)
public enum class PulseDeviceAttributeName {
    @SerialName("os_version")
    OS_VERSION,

    @SerialName("app_version")
    APP_VERSION,

    @SerialName("country")
    COUNTRY,

    @SerialName("state")
    STATE,

    @SerialName("platform")
    PLATFORM,

    /**
     * Unknown device attr name which is may come in future
     */
    @SerialName(PulseFallbackToUnknownEnumSerializer.UNKNOWN_KEY_NAME)
    UNKNOWN,
    ;

    public fun matches(
        context: Context,
        value: String,
    ): Boolean {
        val regex = value.toRegex()

        val currentValue =
            when (this) {
                OS_VERSION -> {
                    Build.VERSION.SDK_INT.toString()
                }

                APP_VERSION -> {
                    try {
                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        packageInfo.versionName
                    } catch (e: PackageManager.NameNotFoundException) {
                        if (BuildConfig.DEBUG) throw e
                        null
                    }
                }

                COUNTRY -> {
                    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    tm?.networkCountryIso
                }

                STATE -> {
                    // TODO see if state handling can happen with android apis only without adding GeoCoder or any similar lib
                    null
                }

                PLATFORM -> {
                    PulseSdkName.ANDROID_JAVA_SDK_NAME_STR
                }

                UNKNOWN -> {
                    null
                }
            }

        return currentValue?.matches(regex) == true
    }
}

private class PulseDeviceAttributeNameSerializer :
    PulseFallbackToUnknownEnumSerializer<PulseDeviceAttributeName>(PulseDeviceAttributeName::class)
