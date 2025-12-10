package com.pulse.upload

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.TaskAction

class PulseUploadSourceMapsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("uploadSourceMaps", UploadSourceMapsTask::class.java) {
            group = "pulse"
            description = "Upload ProGuard/R8 mapping files to Pulse backend"
        }
    }
}

abstract class UploadSourceMapsTask : DefaultTask() {

    @get:Option(option = "api-url", description = "Backend API URL for uploading source maps")
    @get:Input
    abstract val apiUrl: Property<String>

    @get:Option(option = "mapping-file", description = "ProGuard/R8 mapping file path")
    @get:Input
    abstract val mappingFile: Property<String>

    @get:Option(option = "app-version", description = "App version (e.g., 1.0.0). Required.")
    @get:Input
    abstract val appVersion: Property<String>

    @get:Option(option = "version-code", description = "Version code (positive integer, e.g., 1). Required.")
    @get:Input
    abstract val versionCode: Property<String>

    @get:Option(option = "verbose", description = "Show verbose/debug information")
    @get:Input
    abstract val verbose: Property<Boolean>

    init {
        verbose.convention(false)
    }

    @TaskAction
    fun upload() {
        val apiUrlValue = validateAndGetApiUrl()
        val mappingFilePath = mappingFile.orNull
            ?: throw IllegalArgumentException("Mapping file path is required. Use --mapping-file=<path>")

        val debugValue = verbose.getOrElse(false)

        val mappingFileObj = resolveMappingFile(mappingFilePath)

        val appVersionValue = validateAndGetAppVersion()
        val versionCodeValue = validateAndGetVersionCode()

        val platform = "android"
        val type = "mapping"
        val fileName = mappingFileObj.name
        val fileSize = mappingFileObj.length()

        logger.lifecycle("\n📤 Uploading to Pulse backend...")
        logger.lifecycle("   File: ${mappingFileObj.name} (${formatFileSize(mappingFileObj.length())})")
        logger.lifecycle("   Version: $appVersionValue (code: $versionCodeValue)")

        if (debugValue) {
            logger.lifecycle("\n🔍 Debug Info:")
            logger.lifecycle("   API URL: $apiUrlValue")
            logger.lifecycle("   File Path: ${mappingFileObj.absolutePath}")
            logger.lifecycle("   Platform: $platform, Type: $type")
        }

        try {
            val success = uploadFile(
                apiUrl = apiUrlValue,
                file = mappingFileObj,
                appVersion = appVersionValue,
                versionCode = versionCodeValue,
                platform = platform,
                type = type,
                fileName = fileName,
                debug = debugValue
            )

            if (success) {
                logger.lifecycle("✓ Upload successful")
            } else {
                logger.error(" Backend returned HTTP 200 but response contains \"data\": false - backend processing failed")
                if (debugValue) {
                    logger.error("   Check backend logs for details")
                }
                throw RuntimeException("Upload failed: Backend processing failed (response: data=false)")
            }
        } catch (e: IllegalArgumentException) {
            logger.error("✗ Validation error: ${e.message}")
            throw e
        } catch (e: Exception) {
            logger.error("✗ Upload failed: ${e.message}")
            if (debugValue) {
                logger.error("   Exception: ${e.javaClass.simpleName}")
                e.printStackTrace()
            }
            throw RuntimeException("Upload failed: ${e.message}", e)
        }
    }

    private fun validateAndGetApiUrl(): String {
        val url = apiUrl.orNull?.trim()
            ?: throw IllegalArgumentException("API URL is required. Use --api-url=<url>")

        if (url.isBlank()) {
            throw IllegalArgumentException("API URL cannot be blank")
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw IllegalArgumentException("API URL must use http:// or https:// protocol. Got: $url")
        }

        return url
    }

    private fun resolveMappingFile(mappingFilePath: String): File {
        val file = if (File(mappingFilePath).isAbsolute) {
            File(mappingFilePath)
        } else {
            val projectDir = project.layout.projectDirectory.asFile
            File(projectDir, mappingFilePath)
        }

        if (!file.exists()) {
            throw IllegalArgumentException("Mapping file not found: ${file.absolutePath}")
        }

        if (!file.isFile) {
            throw IllegalArgumentException("Path is not a file: ${file.absolutePath}")
        }

        return file
    }

    private fun validateAndGetAppVersion(): String {
        val version = appVersion.orNull?.trim()
            ?: throw IllegalArgumentException("App version is required. Use --app-version=<version>")

        if (version.isBlank()) {
            throw IllegalArgumentException("App version cannot be blank")
        }

        val versionPattern = Regex("^\\d+(\\.\\d+)*$")
        if (!versionPattern.matches(version)) {
            throw IllegalArgumentException("Invalid app version format: \"$version\". Expected format: X.Y.Z (e.g., 1.0.0)")
        }

        return version
    }

    private fun validateAndGetVersionCode(): String {
        val versionCodeStr = versionCode.orNull?.trim()
            ?: throw IllegalArgumentException("Version code is required. Use --version-code=<code>")

        if (versionCodeStr.isBlank()) {
            throw IllegalArgumentException("Version code cannot be blank")
        }

        val code = versionCodeStr.toIntOrNull()
            ?: throw IllegalArgumentException("Invalid version code: \"$versionCodeStr\". Must be a positive integer.")

        if (code <= 0) {
            throw IllegalArgumentException("Version code must be a positive integer, got: $code")
        }

        return code.toString()
    }

    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$bytes bytes"
        }
    }

    private fun uploadFile(
        apiUrl: String,
        file: File,
        appVersion: String,
        versionCode: String,
        platform: String,
        type: String,
        fileName: String,
        debug: Boolean
    ): Boolean {
        val metadata = buildMetadataJson(type, appVersion, versionCode, platform, fileName)
        val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
        val connection = try {
            URL(apiUrl).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid API URL: $apiUrl", e)
        }

        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.doOutput = true
            connection.connectTimeout = 30000 // 30 seconds
            connection.readTimeout = 60000 // 60 seconds

            connection.outputStream.use { output ->
                writeMultipartFormData(output, boundary, metadata, file, fileName)
            }

            val responseCode = connection.responseCode
            val response = readResponse(connection, responseCode)

            validateResponse(responseCode, response, debug)

        } catch (e: Exception) {
            if (debug) {
                logger.error("HTTP request failed: ${e.message}")
                logger.error("   URL: $apiUrl")
            }
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
        output.write("--$boundary\r\n".toByteArray())
        output.write("Content-Disposition: form-data; name=\"metadata\"\r\n".toByteArray())
        output.write("Content-Type: application/json\r\n\r\n".toByteArray())
        output.write(metadata.toByteArray())
        output.write("\r\n".toByteArray())

        output.write("--$boundary\r\n".toByteArray())
        output.write("Content-Disposition: form-data; name=\"fileContent\"; filename=\"$fileName\"\r\n".toByteArray())
        output.write("Content-Type: text/plain\r\n\r\n".toByteArray())
        file.inputStream().use { input ->
            input.copyTo(output)
        }
        output.write("\r\n".toByteArray())

        output.write("--$boundary--\r\n".toByteArray())
    }

    private fun readResponse(connection: HttpURLConnection, responseCode: Int): String {
        return try {
            if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText() ?: "No error message available"
            }
        } catch (e: Exception) {
            "Failed to read response: ${e.message}"
        }
    }

    private fun validateResponse(responseCode: Int, response: String, debug: Boolean): Boolean {
        if (responseCode !in 200..299) {
            logger.error("   HTTP Status: $responseCode")
            if (debug) {
                logger.error("   Response: $response")
            }
            throw RuntimeException("Upload failed with HTTP $responseCode")
        }
        
        val success = response.contains("\"data\":true")
        if (debug) {
            logger.lifecycle("\n📥 Backend Response:")
            logger.lifecycle("   Status: $responseCode")
            logger.lifecycle("   Response: $response")
            logger.lifecycle("   Success: $success")
        }

        return success
    }

    private fun buildMetadataJson(
        type: String,
        appVersion: String,
        versionCode: String,
        platform: String,
        fileName: String
    ): String {
        val metadata = mapOf(
            "type" to type,
            "appVersion" to appVersion,
            "versionCode" to versionCode,
            "platform" to platform,
            "fileName" to fileName
        )
        return metadata.entries.joinToString(",", "[{", "}]") { (key, value) ->
            "\"$key\":\"${escapeJsonString(value)}\""
        }
    }

    private fun escapeJsonString(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

}


