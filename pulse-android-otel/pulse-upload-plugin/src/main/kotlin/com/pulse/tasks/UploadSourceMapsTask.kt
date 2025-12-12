package com.pulse.tasks

import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.Locale
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.TaskAction

abstract class UploadSourceMapsTask : DefaultTask() {

    companion object {
        private const val CRLF = "\r\n"
        private const val DEBUG_URL_PREFIX = "   URL: "
        private const val DEBUG_RESPONSE_PREFIX = "   Response: "
    }

    @get:Option(option = "api-url", description = "API URL for uploading source maps")
    @get:Input
    abstract val apiUrl: Property<String>

    @get:Option(option = "mapping-file", description = "ProGuard/R8 mapping file path")
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mappingFile: RegularFileProperty

    @get:Option(option = "app-version", description = "App version (e.g., 1.0.0). Required.")
    @get:Input
    abstract val appVersion: Property<String>

    @get:Option(option = "version-code", description = "Version code (positive integer, e.g., 1). Required.")
    @get:Input
    abstract val versionCode: Property<Int>

    @get:Internal
    internal abstract val projectDirectoryPath: Property<String>

    init {
        description = "Upload ProGuard/R8 mapping files to Pulse backend"
        group = com.pulse.plugins.PulsePlugin.TASK_GROUP
        projectDirectoryPath.set(project.layout.projectDirectory.asFile.absolutePath)
    }

    @TaskAction
    fun upload() {
        val apiUrlValue = validateRequiredString(apiUrl, "API URL", "--api-url=<url>")
        val mappingFileObj = resolveMappingFile()
        val appVersionValue = validateRequiredString(appVersion, "App version", "--app-version=<version>")
        val versionCodeValue = validateAndGetVersionCode()

        val platform = "android"
        val type = "mapping"
        val fileName = mappingFileObj.name

        logger.info("\n📤 Uploading to Pulse backend...")
        logger.info("   File: ${mappingFileObj.name} (${formatFileSize(mappingFileObj.length())})")
        logger.info("   Version: $appVersionValue (code: $versionCodeValue)")

        logger.debug("\n🔍 Debug Info:")
        logger.debug("   API URL: $apiUrlValue")
        logger.debug("   File Path: ${mappingFileObj.absolutePath}")
        logger.debug("   Platform: $platform, Type: $type")

        try {
            uploadFile(
                apiUrl = apiUrlValue,
                file = mappingFileObj,
                appVersion = appVersionValue,
                versionCode = versionCodeValue,
                platform = platform,
                type = type,
                fileName = fileName
            )
            logger.info("✓ Upload successful")
        } catch (e: IllegalArgumentException) {
            handleUploadError("Validation error", e)
        } catch (e: MalformedURLException) {
            handleUploadError("Invalid API URL", e)
        } catch (e: IOException) {
            logger.error("✗ Upload failed: ${e.message}")
            logger.debug("   Exception: ${e.javaClass.simpleName}")
            throw GradleException("Upload failed: ${e.message}", e)
        }
    }

    private fun handleUploadError(message: String, e: Exception) {
        logger.error("✗ $message: ${e.message}")
        throw GradleException("$message: ${e.message}", e)
    }

    private fun validateRequiredString(
        property: Property<String>,
        fieldName: String,
        usage: String
    ): String {
        val value = property.orNull?.trim()
            ?: throw GradleException("$fieldName is required. Use $usage")

        if (value.isBlank()) {
            throw GradleException("$fieldName cannot be blank")
        }

        return value
    }

    private fun validateAndGetVersionCode(): Int {
        val code = versionCode.orNull
            ?: throw GradleException("Version code is required. Use --version-code=<code>")

        if (code <= 0) {
            throw GradleException("Version code must be a positive integer, got: $code")
        }

        return code
    }

    private fun resolveMappingFile(): File {
        val file = mappingFile.asFile.get()

        // Gradle's @InputFile validates file existence, but not that it's a file (not a directory)
        if (!file.isFile) {
            throw GradleException("Path is not a file: ${file.absolutePath}")
        }

        if (file.length() == 0L) {
            throw GradleException("Mapping file is empty: ${file.absolutePath}")
        }

        return file
    }


    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> String.format(Locale.US, "%.2f MB", mb)
            kb >= 1 -> String.format(Locale.US, "%.2f KB", kb)
            else -> "$bytes bytes"
        }
    }

    private fun uploadFile(
        apiUrl: String,
        file: File,
        appVersion: String,
        versionCode: Int,
        platform: String,
        type: String,
        fileName: String
    ) {
        val metadata = buildMetadata(type, appVersion, versionCode, platform, fileName)
        val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"

        val connection = URL(apiUrl).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            connection.outputStream.use { output ->
                writeMultipartFormData(output, boundary, metadata, file, fileName)
            }

            val responseCode = connection.responseCode
            val response = readResponse(connection, responseCode)

            validateResponse(responseCode, response)

        } catch (e: IOException) {
            logger.debug("HTTP request failed: ${e.message}")
            logDebugUrl(apiUrl)
            throw e
        } catch (e: MalformedURLException) {
            logger.debug("Invalid URL: ${e.message}")
            logDebugUrl(apiUrl)
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun writeMultipartFormData(
        output: java.io.OutputStream,
        boundary: String,
        metadata: String,
        file: File,
        fileName: String
    ) {
        val boundaryLine = "--$boundary$CRLF"
        output.apply {
            write(boundaryLine.toByteArray())
            write("""Content-Disposition: form-data; name="metadata"$CRLF""".toByteArray())
            write("""Content-Type: application/json$CRLF$CRLF""".toByteArray())
            write(metadata.toByteArray())
            write(CRLF.toByteArray())

            write(boundaryLine.toByteArray())
            write("""Content-Disposition: form-data; name="fileContent"; filename="$fileName"$CRLF""".toByteArray())
            write("""Content-Type: text/plain$CRLF$CRLF""".toByteArray())
            file.inputStream().use { input ->
                input.copyTo(this)
            }
            write(CRLF.toByteArray())

            write("--$boundary--$CRLF".toByteArray())
        }
    }

    private fun readResponse(connection: HttpURLConnection, responseCode: Int): String {
        return try {
            if (responseCode in 200..299) {
                connection.inputStream.use { input ->
                    input.bufferedReader().readText()
                }
            } else {
                connection.errorStream?.use { error ->
                    error.bufferedReader().readText()
                } ?: "No error message available"
            }
        } catch (e: IOException) {
            "Failed to read response: ${e.message}"
        }
    }

    private fun validateResponse(responseCode: Int, response: String) {
        if (responseCode !in 200..299) {
            logger.error("   HTTP Status: $responseCode")
            logDebugResponse(response)
            throw GradleException("Upload failed with HTTP $responseCode")
        }

        logger.debug("\n📥 Backend Response:")
        logger.debug("   Status: $responseCode")
        logDebugResponse(response)
    }

    private fun logDebugUrl(url: String) {
        logger.debug("$DEBUG_URL_PREFIX$url")
    }

    private fun logDebugResponse(response: String) {
        logger.debug("$DEBUG_RESPONSE_PREFIX$response")
    }

    private fun buildMetadata(
        type: String,
        appVersion: String,
        versionCode: Int,
        platform: String,
        fileName: String
    ): String {
        val metadata = Metadata(
            type = type,
            appVersion = appVersion,
            versionCode = versionCode.toString(),
            platform = platform,
            fileName = fileName
        )
        val metadataList = listOf(metadata)
        return Gson().toJson(metadataList)
    }

    private data class Metadata(
        val type: String,
        val appVersion: String,
        val versionCode: String,
        val platform: String,
        val fileName: String
    )
}

