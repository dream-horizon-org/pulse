@file:Suppress("unused")

package io.opentelemetry.android.internal.services.metadata

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.pulse.utils.fromJson
import com.pulse.utils.toFlow
import com.pulse.utils.toJson
import io.opentelemetry.android.common.internal.features.networkattributes.CurrentNetworkAttributesExtractor
import io.opentelemetry.android.internal.services.network.CurrentNetworkProvider
import io.opentelemetry.android.internal.services.visiblescreen.VisibleScreenTracker
import io.opentelemetry.android.session.Session
import io.opentelemetry.android.session.SessionObserver
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.android.session.SessionPublisher
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes.SESSION_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class PulseMetadataUpdater internal constructor(
    private val application: Application,
) {
    private val networkAttributesExtractor = CurrentNetworkAttributesExtractor()
    private var currentMetadata: PulseAppMetadata = readCached(application) ?: PulseAppMetadata()

    @Synchronized
    fun updateString(
        key: String,
        value: String?,
    ) {
        val next =
            currentMetadata.copy(
                stringFields = currentMetadata.stringFields.updateValue(key, value),
            )
        persistIfChanged(next)
    }

    @Synchronized
    fun updateLong(
        key: String,
        value: Long?,
    ) {
        val next =
            currentMetadata.copy(
                longFields = currentMetadata.longFields.updateValue(key, value),
            )
        persistIfChanged(next)
    }

    @Synchronized
    fun updateDouble(
        key: String,
        value: Double?,
    ) {
        val next =
            currentMetadata.copy(
                doubleFields = currentMetadata.doubleFields.updateValue(key, value),
            )
        persistIfChanged(next)
    }

    @Synchronized
    fun updateBoolean(
        key: String,
        value: Boolean?,
    ) {
        val next =
            currentMetadata.copy(
                booleanFields = currentMetadata.booleanFields.updateValue(key, value),
            )
        persistIfChanged(next)
    }

    @Synchronized
    fun read(): PulseAppMetadata = currentMetadata

    fun subscribeToSessionProvider(sessionProvider: SessionProvider) {
        updateString(SESSION_ID.key, sessionProvider.getSessionId())
        (sessionProvider as? SessionPublisher)?.addObserver(
            object : SessionObserver {
                override fun onSessionStarted(
                    newSession: Session,
                    previousSession: Session,
                ) {
                    updateString(SESSION_ID.key, newSession.getId())
                }

                override fun onSessionEnded(
                    session: Session,
                    expirationTimestampNanos: Long?,
                ) = Unit
            },
        )
    }

    fun subscribeToVisibleScreenTracker(
        scope: CoroutineScope,
        visibleScreenTracker: VisibleScreenTracker,
    ): Job =
        scope.launch {
            visibleScreenTracker.visibleScreenState.collect { state ->
                updateString(PulseAppMetadata.SCREEN_NAME, state.screenName)
                updateString(PulseAppMetadata.ACTIVITY_NAME, state.activityName)
                updateString(PulseAppMetadata.FRAGMENT_NAME, state.fragmentName)
            }
        }

    fun subscribeToUserIdPrefs(
        scope: CoroutineScope,
        userPrefs: SharedPreferences,
        key: String,
    ): Job =
        scope.launch {
            userPrefs.toFlow<String>(key).collect { userId ->
                updateString(USER_ID_KEY, userId)
            }
        }

    fun subscribeToNetworkProvider(currentNetworkProvider: CurrentNetworkProvider) {
        updateNetworkAttributes(networkAttributesExtractor.extract(currentNetworkProvider.currentNetwork))
        currentNetworkProvider.addNetworkChangeListener { currentNetwork ->
            updateNetworkAttributes(networkAttributesExtractor.extract(currentNetwork))
        }
    }

    @Synchronized
    fun refreshFromStorage(): PulseAppMetadata {
        currentMetadata = readCached(application) ?: PulseAppMetadata()
        return currentMetadata
    }

    private fun updateNetworkAttributes(attributes: Attributes) {
        attributes.asMap().forEach { (key, value) ->
            if (value is String) {
                updateString(key.key, value)
            }
        }
    }

    private fun persistIfChanged(next: PulseAppMetadata) {
        if (next == currentMetadata) {
            return
        }
        currentMetadata = next
        next.toJson(PulseMetadataInstaller.TAG)?.let {
            writeAtomically(getMetadataFile(application), it)
        }
    }

    companion object {
        private const val METADATA_DIR = "pulse/metadata"
        private const val METADATA_FILE = "pulse_app_metadata.json"
        private const val USER_ID_KEY = "user.id"

        @Volatile
        private var instance: PulseMetadataUpdater? = null

        @JvmStatic
        fun create(application: Application): PulseMetadataUpdater =
            synchronized(this) {
                instance ?: PulseMetadataUpdater(application).also { instance = it }
            }

        @JvmStatic
        fun readCached(application: Application): PulseAppMetadata? {
            val metadataFile = getMetadataFile(application)
            return if (metadataFile.exists()) {
                metadataFile.readText().fromJson<PulseAppMetadata>(PulseMetadataInstaller.TAG)
            } else {
                null
            }
        }

        fun getMetadataFile(context: Context): File {
            val dir = File(context.cacheDir, METADATA_DIR)
            dir.mkdirs()
            return File(dir, METADATA_FILE)
        }

        private fun writeAtomically(
            file: File,
            content: String,
        ) {
            file.parentFile?.mkdirs()
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            tempFile.writeText(content)
            if (!tempFile.renameTo(file)) {
                file.writeText(content)
                tempFile.delete()
            }
        }
    }
}

private fun <T : Any> Map<String, T>.updateValue(
    key: String,
    value: T?,
): Map<String, T> {
    val mutable = toMutableMap()
    if (value == null) {
        mutable.remove(key)
    } else {
        mutable[key] = value
    }
    return mutable.toMap()
}
