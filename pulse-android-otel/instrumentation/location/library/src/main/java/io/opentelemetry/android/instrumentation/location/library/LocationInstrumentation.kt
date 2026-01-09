package io.opentelemetry.android.instrumentation.location.library

import com.google.auto.service.AutoService
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.android.instrumentation.location.core.LocationProvider
import io.opentelemetry.android.instrumentation.location.models.LocationConstants
import io.opentelemetry.android.instrumentation.location.processors.LocationInstrumentationConstants
import io.opentelemetry.android.internal.services.Services.Companion.get
import io.opentelemetry.android.internal.services.applifecycle.ApplicationStateListener

@AutoService(AndroidInstrumentation::class)
public class LocationInstrumentation : AndroidInstrumentation {
    private var initializedLocationProvider: LocationProvider? = null
    private var appLifecycleListener: ApplicationStateListener? = null

    override val name: String = LocationInstrumentationConstants.INSTRUMENTATION_NAME

    override fun install(ctx: InstallationContext) {
        val sharedPreferences =
            ctx.application.getSharedPreferences(
                SHARED_PREFS_NAME,
                android.content.Context.MODE_PRIVATE,
            )
        val locationProvider =
            LocationProvider(
                ctx.application,
                sharedPreferences,
                LocationConstants.DEFAULT_CACHE_INVALIDATION_TIME_MS,
            )
        initializedLocationProvider = locationProvider

        val listener: ApplicationStateListener =
            object : ApplicationStateListener {
                override fun onApplicationForegrounded() {
                    initializedLocationProvider?.startPeriodicRefresh()
                }

                override fun onApplicationBackgrounded() {
                    initializedLocationProvider?.stopPeriodicRefresh()
                }
            }
        appLifecycleListener = listener
        get(ctx.application).appLifecycle.registerListener(listener)
    }

    override fun uninstall(ctx: InstallationContext) {
        initializedLocationProvider?.stopPeriodicRefresh()
        initializedLocationProvider = null
        appLifecycleListener = null
    }

    private companion object {
        private const val SHARED_PREFS_NAME = "pulse_location_data"
    }
}
