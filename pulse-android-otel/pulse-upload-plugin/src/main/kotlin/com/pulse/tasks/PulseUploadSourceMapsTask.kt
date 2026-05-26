@file:Suppress("ForbiddenImport") // todo replace with Kotlin serialisation

package com.pulse.tasks

import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URI
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
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

enum class PulseSymbolUploadKind {
    MAPPING,
    NDK,
}

abstract class PulseUploadSourceMapsTask : DefaultTask() {

    companion object {
        private const val CRLF = "\r\n"
        private const val DEBUG_URL_PREFIX = "   URL: "
        private const val DEBUG_RESPONSE_PREFIX = "   Response: "
        private const val UNKNOWN_ERROR = "Unknown error"
        private const val API_KEY_HEADER = "X-API-KEY"
        private const val API_KEY_OPTION = "--api-key=<key>"
        private const val ANDROID_PLATFORM = "android"
    }

    @get:Internal
    abstract val uploadKind: Property<PulseSymbolUploadKind>

    @get:Option(option = "api-url", description = "API URL for uploading symbol files")
    @get:Input
    abstract val apiUrl: Property<String>

    @get:Option(option = "mapping-file", description = "ProGuard/R8 mapping file path")
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mappingFile: RegularFileProperty

    @get:Option(option = "obj-file", description = "Unstripped NDK .so (or zip) to upload")
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val objFile: RegularFileProperty

    @get:Option(option = "app-version", description = "App version (e.g., 1.0.0)")
    @get:Input
    abstract val appVersion: Property<String>

    @get:Option(option = "version-code", description = "Version code (positive integer, e.g., 1)")
    @get:Input
    abstract val versionCode: Property<Int>

    @get:Option(option = "api-key", description = "API key for authenticating API requests (sent as X-API-KEY header)")
    @get:Input
    abstract val apiKey: Property<String>

    init {
        description = "Upload symbol files (ProGuard mapping or NDK obj) to Pulse backend"
        group = com.pulse.plugins.PulsePlugin.TASK_GROUP
    }

    @TaskAction
    fun upload() {
        when (requireUploadKind()) {
            PulseSymbolUploadKind.MAPPING -> uploadMapping()
            PulseSymbolUploadKind.NDK -> uploadNdk()
        }
    }

    private fun uploadMapping() {
        val mappingFileObj = resolveMappingFile()
        upload(
            UploadParams(
                extensionHint = "pulse.sourcemaps { ... }",
                file = mappingFileObj,
                type = "mapping",
                contentType = "text/plain",
            )
        )
    }

    private fun uploadNdk() {
        val objFileObj = resolveObjFile()
        upload(
            UploadParams(
                extensionHint = "pulse.symbols { ... }",
                file = objFileObj,
                type = "ndk",
                contentType = contentTypeFor(objFileObj),
            )
        )
    }

    private fun upload(params: UploadParams) {
        val apiUrlValue = validateRequiredString(apiUrl, "API URL", "--api-url=<url>", params.extensionHint)
        val apiKeyValue = validateRequiredString(apiKey, API_KEY_HEADER, API_KEY_OPTION, params.extensionHint)
        val appVersionValue = validateRequiredString(
            appVersion,
            "App version",
            "--app-version=<version>",
            params.extensionHint,
        )
        val versionCodeValue = validateAndGetVersionCode(params.extensionHint)

        logger.lifecycle("Uploading ${params.type} to Pulse backend...")
        logger.info("   File: ${params.file.name} (${formatFileSize(params.file.length())})")
        logger.info("   Version: $appVersionValue (code: $versionCodeValue)")
        logger.info("   API URL: $apiUrlValue")
        logger.debug("   File Path: ${params.file.absolutePath}")

        try {
            uploadFile(
                apiUrl = apiUrlValue,
                apiKey = apiKeyValue,
                file = params.file,
                appVersion = appVersionValue,
                versionCode = versionCodeValue,
                platform = ANDROID_PLATFORM,
                type = params.type,
                fileName = params.file.name,
                contentType = params.contentType,
            )
            logger.lifecycle("Upload successful")
        } catch (e: IllegalArgumentException) {
            handleUploadError("Validation error", e)
        } catch (e: MalformedURLException) {
            handleUploadError("Invalid API URL", e)
        } catch (e: IOException) {
            handleUploadError("Upload failed", e)
        }
    }

    private data class UploadParams(
        val extensionHint: String,
        val file: File,
        val type: String,
        val contentType: String,
    )

    private fun requireUploadKind(): PulseSymbolUploadKind =
        uploadKind.orNull
            ?: throw GradleException("uploadKind is not configured on this task")

    private fun contentTypeFor(file: File): String =
        when (file.extension.lowercase(Locale.US)) {
            "zip" -> "application/zip"
            "so" -> "application/octet-stream"
            else -> "application/octet-stream"
        }

    private fun handleUploadError(message: String, e: Exception) {
        val errorMessage = e.message ?: UNKNOWN_ERROR
        logger.error("✗ $message: $errorMessage")
        throw GradleException("$message: $errorMessage", e)
    }

    private fun validateRequiredString(
        property: Property<String>,
        fieldName: String,
        usage: String,
        extensionHint: String,
    ): String {
        val value = property.orNull?.trim()
            ?: throw GradleException("$fieldName is required. Use $usage or $extensionHint")

        if (value.isBlank()) {
            throw GradleException("$fieldName cannot be blank")
        }

        return value
    }

    private fun validateAndGetVersionCode(extensionHint: String): Int {
        val code = versionCode.orNull
            ?: throw GradleException("Version code is required. Use --version-code=<code> or $extensionHint")

        if (code <= 0) {
            throw GradleException("Version code must be a positive integer, got: $code")
        }

        return code
    }

    private fun resolveMappingFile(): File {
        val file = mappingFile.orNull?.asFile
            ?: throw GradleException(
                "Mapping file is required. Use --mapping-file=<path> or pulse.sourcemaps { mappingFile.set(...) }"
            )

        if (file.length() == 0L) {
            throw GradleException("Mapping file is empty: ${file.absolutePath}")
        }

        return file
    }

    private fun resolveObjFile(): File {
        val file = objFile.orNull?.asFile
            ?: throw GradleException(
                "Obj file is required. Use --obj-file=<path> or pulse.symbols { objFile.set(...) }"
            )

        if (!file.isFile) {
            throw GradleException("Obj file not found: ${file.absolutePath}")
        }

        if (file.length() == 0L) {
            throw GradleException("Obj file is empty: ${file.absolutePath}")
        }

        val ext = file.extension.lowercase(Locale.US)
        if (ext != "so" && ext != "zip") {
            throw GradleException("Obj file must be a .so or .zip file, got: ${file.name}")
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
        apiKey: String,
        file: File,
        appVersion: String,
        versionCode: Int,
        platform: String,
        type: String,
        fileName: String,
        contentType: String,
    ) {
        val metadata = buildMetadata(type, appVersion, versionCode, platform, fileName)
        val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"

        val connection = URI.create(apiUrl).toURL().openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty(API_KEY_HEADER, apiKey)
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            connection.outputStream.use { output ->
                writeMultipartFormData(output, boundary, metadata, file, fileName, contentType)
            }

            val responseCode = connection.responseCode
            val response = readResponse(connection, responseCode)
            validateResponse(responseCode, response)
        } catch (e: IOException) {
            logger.debug("HTTP request failed: ${e.message ?: UNKNOWN_ERROR}")
            logger.debug("$DEBUG_URL_PREFIX$apiUrl")
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
        fileName: String,
        contentType: String,
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
            write("""Content-Type: $contentType$CRLF$CRLF""".toByteArray())
            file.inputStream().use { input -> input.copyTo(this) }
            write(CRLF.toByteArray())

            write("--$boundary--$CRLF".toByteArray())
        }
    }

    private fun readResponse(connection: HttpURLConnection, responseCode: Int): String {
        return try {
            if (responseCode in 200..299) {
                connection.inputStream.use { input -> input.bufferedReader().readText() }
            } else {
                connection.errorStream?.use { error -> error.bufferedReader().readText() }
                    ?: "No error message available"
            }
        } catch (e: IOException) {
            "Failed to read response: ${e.message ?: UNKNOWN_ERROR}"
        }
    }

    private fun validateResponse(responseCode: Int, response: String) {
        if (responseCode !in 200..299) {
            logger.error("   HTTP Status: $responseCode")
            logger.debug("$DEBUG_RESPONSE_PREFIX$response")
            throw GradleException("Upload failed with HTTP $responseCode")
        }

        logger.debug("\n📥 Backend Response:")
        logger.debug("   Status: $responseCode")
        logger.debug("$DEBUG_RESPONSE_PREFIX$response")
    }

    private fun buildMetadata(
        type: String,
        appVersion: String,
        versionCode: Int,
        platform: String,
        fileName: String,
    ): String {
        val metadata = Metadata(
            type = type,
            appVersion = appVersion,
            versionCode = versionCode.toString(),
            platform = platform,
            fileName = fileName,
        )
        return Gson().toJson(listOf(metadata))
    }

    private data class Metadata(
        val type: String,
        val appVersion: String,
        val versionCode: String,
        val platform: String,
        val fileName: String,
    )
}
