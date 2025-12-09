package pulse.upload

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input
import org.gradle.api.provider.Property

/**
 * Pulse Source Maps Upload Plugin
 * 
 * Usage in build.gradle.kts:
 *   plugins {
 *       id("pulse.upload-sourcemaps")
 *   }
 * 
 * Or via command line:
 *   ./gradlew uploadSourceMaps \
 *     -Ppulse.apiUrl=http://localhost:8080/v1/symbolicate/file/upload \
 *     -Ppulse.mappingFile=app/build/outputs/mapping/release/mapping.txt \
 *     -Ppulse.appVersion=1.0.0 \
 *     -Ppulse.versionCode=1 \
 *     -Ppulse.debug=true
 */

class PulseUploadSourceMapsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("uploadSourceMaps", UploadSourceMapsTask::class.java) {
            group = "pulse"
            description = "Upload ProGuard/R8 mapping files to Pulse backend"
            notCompatibleWithConfigurationCache("Accesses project properties at execution time")
        }
    }
}

open class UploadSourceMapsTask : DefaultTask() {

    @TaskAction
    fun upload() {
        val apiUrlValue = project.findProperty("pulse.apiUrl") as? String
            ?: throw IllegalArgumentException("API URL is required. Use -Ppulse.apiUrl=<url>")

        val mappingFilePath = project.findProperty("pulse.mappingFile") as? String
            ?: throw IllegalArgumentException("Mapping file path is required. Use -Ppulse.mappingFile=<path>")

        val debugValue = project.findProperty("pulse.debug") as? String == "true"

        val mappingFile = if (File(mappingFilePath).isAbsolute) {
            File(mappingFilePath)
        } else {
            File(project.projectDir, mappingFilePath)
        }

        if (!mappingFile.exists()) {
            throw IllegalArgumentException("Mapping file not found: ${mappingFile.absolutePath}")
        }

        val android = project.extensions.findByName("android")
        val appVersionValue = project.findProperty("pulse.appVersion") as? String
            ?: (android?.let { getVersionName(it) })
            ?: throw IllegalArgumentException("App version is required. Use -Ppulse.appVersion=<version> or ensure versionName in build.gradle")

        val versionCodeValue = (project.findProperty("pulse.versionCode") as? String)?.toIntOrNull()
            ?: (android?.let { getVersionCode(it) })
            ?: throw IllegalArgumentException("Version code is required. Use -Ppulse.versionCode=<code> or ensure versionCode in build.gradle")

        val platform = "android"
        val type = "mapping"
        val fileName = mappingFile.name

        val fileSize = mappingFile.length()
        val fileSizeKB = fileSize / 1024.0
        val fileSizeMB = fileSizeKB / 1024.0
        val fileSizeStr = if (fileSizeMB >= 1) {
            String.format("%.2f MB", fileSizeMB)
        } else {
            String.format("%.2f KB", fileSizeKB)
        }

        logger.lifecycle("\n📋 Upload Configuration:")
        logger.lifecycle("   API URL: $apiUrlValue")
        logger.lifecycle("   File: ${mappingFile.absolutePath}")
        logger.lifecycle("   File Size: $fileSizeStr ($fileSize bytes)")
        logger.lifecycle("   App Version: $appVersionValue")
        logger.lifecycle("   Version Code: $versionCodeValue")
        logger.lifecycle("   Platform: $platform")
        logger.lifecycle("   Type: $type")
        logger.lifecycle("   File Name: $fileName")

        if (debugValue) {
            logger.lifecycle("\n🔍 Debug Info:")
            logger.lifecycle("   Project Dir: ${project.projectDir}")
            logger.lifecycle("   Mapping File Path (input): $mappingFilePath")
            logger.lifecycle("   Mapping File (resolved): ${mappingFile.absolutePath}")
        }

        logger.lifecycle("\n📤 Uploading mapping file to Pulse backend...")

        try {
            val success = uploadFile(
                apiUrlValue,
                mappingFile,
                appVersionValue,
                versionCodeValue.toString(),
                platform,
                type,
                fileName,
                debugValue
            )
            if (success) {
                logger.lifecycle("✓ Mapping file uploaded successfully")
            } else {
                logger.error("✗ Upload failed")
                throw RuntimeException("Upload failed")
            }
        } catch (e: Exception) {
            logger.error("✗ Error: ${e.message}")
            throw e
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
        val connection = URL(apiUrl).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.doOutput = true

            connection.outputStream.use { output ->
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

            val responseCode = connection.responseCode
            val response = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText() ?: "No error message"
            }

            if (responseCode in 200..299) {
                val success = response.contains("\"data\":true") || response.contains("\"status\":200")
                if (debug) {
                    logger.lifecycle("\n📥 Backend Response:")
                    logger.lifecycle("   Status Code: $responseCode")
                    logger.lifecycle("   Response: $response")
                    logger.lifecycle("   Success: $success")
                }
                return success
            } else {
                logger.error("\n❌ Upload failed:")
                logger.error("   Status Code: $responseCode")
                logger.error("   Response: $response")
                throw RuntimeException("Upload failed with status $responseCode: $response")
            }
        } finally {
            connection.disconnect()
        }
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
            "\"$key\":\"${value.replace("\"", "\\\"")}\""
        }
    }

    private fun getVersionName(android: Any): String? {
        return try {
            val defaultConfig = android.javaClass.getMethod("getDefaultConfig").invoke(android)
            defaultConfig.javaClass.getMethod("getVersionName").invoke(defaultConfig)?.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun getVersionCode(android: Any): Int? {
        return try {
            val defaultConfig = android.javaClass.getMethod("getDefaultConfig").invoke(android)
            val versionCode = defaultConfig.javaClass.getMethod("getVersionCode").invoke(defaultConfig)
            when (versionCode) {
                is Int -> versionCode
                is String -> versionCode.toIntOrNull()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

