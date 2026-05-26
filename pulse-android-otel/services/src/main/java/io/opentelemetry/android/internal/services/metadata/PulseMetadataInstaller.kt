package io.opentelemetry.android.internal.services.metadata

import android.app.Application
import android.content.Context
import io.opentelemetry.android.internal.services.Service
import io.opentelemetry.android.internal.services.Services
import io.opentelemetry.android.session.SessionProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class PulseMetadataInstaller internal constructor(
    private val application: Application,
    private val services: Services = Services.get(application),
    private val pulseMetadataUpdater: PulseMetadataUpdater = PulseMetadataUpdater.create(application),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Service {
    private var scope: CoroutineScope? = null
    private var visibleScreenJob: Job? = null
    private var userIdJob: Job? = null

    fun install(sessionProvider: SessionProvider) {
        if (scope != null) {
            return
        }
        val activeScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
        scope = activeScope

        pulseMetadataUpdater.subscribeToSessionProvider(sessionProvider)
        visibleScreenJob =
            pulseMetadataUpdater.subscribeToVisibleScreenTracker(activeScope, services.visibleScreenTracker)
        pulseMetadataUpdater.subscribeToNetworkProvider(services.currentNetworkProvider)
        val userPrefs = application.getSharedPreferences(SDK_DATA_PREFS, Context.MODE_PRIVATE)
        userIdJob = pulseMetadataUpdater.subscribeToUserIdPrefs(activeScope, userPrefs, USER_ID_PREFS_KEY)
    }

    override fun close() {
        visibleScreenJob?.cancel()
        userIdJob?.cancel()
        visibleScreenJob = null
        userIdJob = null
        scope?.cancel()
        scope = null
    }

    companion object {
        /** Must match Pulse SDK `pulse_sdk_data` SharedPreferences name. */
        private const val SDK_DATA_PREFS = "pulse_sdk_data"

        /** Must match PulseUserSessionEmitter user id prefs key (`user_id`). */
        private const val USER_ID_PREFS_KEY = "user_id"

        internal const val TAG = "MetadataInstaller"

        @Volatile
        private var instance: PulseMetadataInstaller? = null

        @JvmStatic
        fun get(application: Application): PulseMetadataInstaller =
            synchronized(this) {
                instance ?: PulseMetadataInstaller(application).also { instance = it }
            }
    }
}
