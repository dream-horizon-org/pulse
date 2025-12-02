package pulsereactnativeotel.example

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import com.facebook.react.module.annotations.ReactModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

@ReactModule(name = NativePulseExampleModule.NAME)
class NativePulseExampleModule(reactContext: ReactApplicationContext) : NativePulseExampleModuleSpec(reactContext) {

    private val client = OkHttpClient()

    override fun getName(): String {
        return NAME
    }

    companion object {
        const val NAME = "NativePulseExampleModule"
    }

    override fun makeGetRequest(url: String, promise: Promise) {
        Thread {
            try {
                val request = Request.Builder()
                    .url(url)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                val result = Arguments.createMap().apply {
                    putInt("status", response.code)
                    putString("body", responseBody)

                    val headersMap = Arguments.createMap()
                    response.headers.forEach { header ->
                        val values = Arguments.createArray()
                        response.headers.values(header.first).forEach { value ->
                            values.pushString(value)
                        }
                        headersMap.putArray(header.first, values)
                    }
                    putMap("headers", headersMap)
                }

                promise.resolve(result)
            } catch (e: IOException) {
                promise.reject("NETWORK_ERROR", e.message, e)
            } catch (e: Exception) {
                promise.reject("UNKNOWN_ERROR", e.message, e)
            }
        }.start()
    }

    override fun makePostRequest(url: String, body: String, promise: Promise) {
        Thread {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = RequestBody.create(mediaType, body)

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                val result = Arguments.createMap().apply {
                    putInt("status", response.code)
                    putString("body", responseBody)

                    val headersMap = Arguments.createMap()
                    response.headers.forEach { header ->
                        val values = Arguments.createArray()
                        response.headers.values(header.first).forEach { value ->
                            values.pushString(value)
                        }
                        headersMap.putArray(header.first, values)
                    }
                    putMap("headers", headersMap)
                }

                promise.resolve(result)
            } catch (e: IOException) {
                promise.reject("NETWORK_ERROR", e.message, e)
            } catch (e: Exception) {
                promise.reject("UNKNOWN_ERROR", e.message, e)
            }
        }.start()
    }
}

