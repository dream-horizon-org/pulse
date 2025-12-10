/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.android.common.RumConstants.RUM_SDK_VERSION
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME
import io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes.DEVICE_MANUFACTURER
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes.DEVICE_MODEL_NAME
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OS_DESCRIPTION
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OS_NAME
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OS_TYPE
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OS_VERSION

private const val DEFAULT_APP_NAME = "unknown_service:android"

object AndroidResource {
    @JvmStatic
    fun createDefault(application: Application): Resource {
        val appName = readAppName(application)
        val resourceBuilder =
            Resource.getDefault().toBuilder().put(SERVICE_NAME, appName)
        val appVersion = readAppVersion(application)
        val appVersionCode = readAppVersionCode(application)
        val buildName = "${appVersion.orEmpty()}_${appVersionCode?.toString().orEmpty()}"
        resourceBuilder.put(SERVICE_VERSION, buildName)
        resourceBuilder.put(RumConstants.App.BUILD_NAME, buildName)
        resourceBuilder.put(
            RumConstants.App.BUILD_ID,
            appVersionCode?.toString().orEmpty(),
        )

        return resourceBuilder
            .put(RUM_SDK_VERSION, BuildConfig.OTEL_ANDROID_VERSION)
            .put(DEVICE_MODEL_NAME, getDeviceModel())
            .put(DEVICE_MODEL_IDENTIFIER, modelIdentifier)
            .put(DEVICE_MANUFACTURER, Build.MANUFACTURER)
            .put(OS_NAME, "Android")
            .put(OS_TYPE, "linux")
            .put(OS_VERSION, Build.VERSION.RELEASE)
            .put(OS_DESCRIPTION, Build.DISPLAY)
            .put(RumConstants.Android.OS_API_LEVEL, Build.VERSION.SDK_INT.toString())
            .build()
    }

    private fun readAppName(application: Application): String =
        try {
            val stringId =
                application.applicationInfo.labelRes
            if (stringId == 0) {
                application.applicationInfo.nonLocalizedLabel.toString()
            } else {
                application.applicationContext.getString(stringId)
            }
        } catch (_: Exception) {
            DEFAULT_APP_NAME
        }

    private fun readAppVersion(application: Application): String? {
        val ctx = application.applicationContext
        return try {
            val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            packageInfo.versionName
        } catch (_: Exception) {
            null
        }
    }

    private fun readAppVersionCode(application: Application): Long? {
        val ctx = application.applicationContext
        return try {
            val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getLongVersionCodeApi28(packageInfo)
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun getLongVersionCodeApi28(packageInfo: android.content.pm.PackageInfo): Long = packageInfo.longVersionCode

    private val modelIdentifier: String
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val modelIdApi31 = modelIdentifierApi31
                if (modelIdApi31 == UNKNOWN_MODEL_ID) {
                    getDeviceModel()
                } else {
                    modelIdApi31
                }
            } else {
                getDeviceModel()
            }

    private fun getDeviceModel(): String = Build.MODEL.orEmpty()

    @get:RequiresApi(Build.VERSION_CODES.S)
    private val modelIdentifierApi31: String
        get() = "${Build.ODM_SKU}_${Build.SKU}"

    private const val UNKNOWN_MODEL_ID = "unknown_unknown"
}
