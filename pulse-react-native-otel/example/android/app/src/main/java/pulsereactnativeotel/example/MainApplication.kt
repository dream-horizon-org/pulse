package pulsereactnativeotel.example

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.facebook.react.defaults.DefaultReactNativeHost
import com.pulsereactnativeotel.Pulse
import io.opentelemetry.android.instrumentation.AndroidInstrumentationLoader
import io.opentelemetry.instrumentation.library.okhttp.v3_0.OkHttpInstrumentation
import android.util.Log
import pulsereactnativeotel.example.NativePulseExamplePackage

class MainApplication : Application(), ReactApplication {

  override val reactNativeHost: ReactNativeHost =
      object : DefaultReactNativeHost(this) {
        override fun getPackages(): List<ReactPackage> =
          PackageList(this).packages.apply {
            // Add native network module for testing native OkHttp calls
            add(NativePulseExamplePackage())
          }

        override fun getJSMainModuleName(): String = "index"

        override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

        override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
        override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
      }

  override val reactHost: ReactHost
    get() = getDefaultReactHost(applicationContext, reactNativeHost)

  override fun onCreate() {
    super.onCreate()

    try {
      AndroidInstrumentationLoader.getInstrumentation(OkHttpInstrumentation::class.java)
    } catch (e: Exception) {
      Log.w("MainApplication", "OkHttp instrumentation not available: ${e.message}")
    }

    Pulse.initialize(this, "http://10.0.2.2:4318") {
      interaction {
        enabled(true)
        setConfigUrl { "http://10.0.2.2:8080/v1/interactions/all-active-interactions/" }
      }
    }
    loadReactNative(this)
  }
}
