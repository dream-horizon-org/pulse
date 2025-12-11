package com.pulse.tasks

import com.google.gson.Gson
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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
    internal abstract val projectDirectory: Property<String>

    init {
        description = "Upload ProGuard/R8 mapping files to Pulse backend"
        projectDirectory.set(project.layout.projectDirectory.asFile.absolutePath)
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
            val success = uploadFile(
                apiUrl = apiUrlValue,
                file = mappingFileObj,
                appVersion = appVersionValue,
                versionCode = versionCodeValue,
                platform = platform,
                type = type,
                fileName = fileName
            )

            if (success) {
                logger.info("✓ Upload successful")
            } else {
                logger.error("✗ Upload failed: Backend returned data: false")
                logger.debug("   Check backend logs for details")
                throw GradleException("Upload failed: Backend processing failed (response: data=false)")
            }
        } catch (e: IllegalArgumentException) {
            logger.error("✗ Validation error: ${e.message}")
            throw GradleException("Validation error: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("✗ Upload failed: ${e.message}")
            logger.debug("   Exception: ${e.javaClass.simpleName}")
            e.printStackTrace()
            throw GradleException("Upload failed: ${e.message}", e)
        }
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

        if (!file.exists()) {
            throw GradleException("Mapping file not found: ${file.absolutePath}")
        }

        if (!file.isFile) {
            throw GradleException("Path is not a file: ${file.absolutePath}")
        }

        return file
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
        versionCode: Int,
        platform: String,
        type: String,
        fileName: String
    ): Boolean {
        val metadata = buildMetadata(type, appVersion, versionCode, platform, fileName)
        val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
        
        val connection = try {
            URL(apiUrl).openConnection() as HttpURLConnection
        } catch (e: java.net.MalformedURLException) {
            throw GradleException("Invalid API URL format: $apiUrl. URL must include protocol (http:// or https://)", e)
        }

        return try {
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

        } catch (e: Exception) {
            logger.debug("HTTP request failed: ${e.message}")
            logger.debug("   URL: $apiUrl")
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
        output.apply {
            write("--$boundary\r\n".toByteArray())
            write("Content-Disposition: form-data; name=\"metadata\"\r\n".toByteArray())
            write("Content-Type: application/json\r\n\r\n".toByteArray())
            write(metadata.toByteArray())
            write("\r\n".toByteArray())

            write("--$boundary\r\n".toByteArray())
            write("Content-Disposition: form-data; name=\"fileContent\"; filename=\"$fileName\"\r\n".toByteArray())
            write("Content-Type: text/plain\r\n\r\n".toByteArray())
            file.inputStream().use { input ->
                input.copyTo(this)
            }
            write("\r\n".toByteArray())

            write("--$boundary--\r\n".toByteArray())
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
        } catch (e: Exception) {
            "Failed to read response: ${e.message}"
        }
    }

    private fun validateResponse(responseCode: Int, response: String): Boolean {
        if (responseCode !in 200..299) {
            logger.error("   HTTP Status: $responseCode")
            logger.debug("   Response: $response")
            throw GradleException("Upload failed with HTTP $responseCode")
        }
        
        val success = response.contains("\"data\":true")
        logger.debug("\n📥 Backend Response:")
        logger.debug("   Status: $responseCode")
        logger.debug("   Response: $response")
        logger.debug("   Success: $success")

        return success
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

