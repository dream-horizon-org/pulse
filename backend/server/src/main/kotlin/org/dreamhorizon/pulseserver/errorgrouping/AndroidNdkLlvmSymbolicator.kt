package org.dreamhorizon.pulseserver.errorgrouping

import com.google.inject.Inject
import org.dreamhorizon.pulseserver.errorgrouping.model.NdkFrame
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Symbolicates Android NDK tombstone frames with NDK `llvm-symbolizer`, selecting unstripped
 * `.so` files by ABI the same way [ndk-stack -sym obj/&lt;abi&gt;](https://developer.android.com/ndk/guides/ndk-stack) does.
 */
class AndroidNdkLlvmSymbolicator(
    private val config: AndroidNdkLlvmConfig,
) {

    @Inject
    constructor() : this(AndroidNdkLlvmConfig())

    fun symbolicateFrames(frames: List<NdkFrame>, ndkZipBytes: ByteArray?, binaryArch: String?): List<String> {
        if (frames.isEmpty()) {
            log.error("No frames to symbolicate.")
            return emptyList()
        }
        if (ndkZipBytes == null || ndkZipBytes.isEmpty()) {
            log.error("{} no NDK symbol bytes; returning raw frames count={}", LOG_PREFIX, frames.size)
            return rawLines(frames)
        }

        var tempRoot: Path? = null
        try {
            tempRoot = Files.createTempDirectory("pulse-ndk-symbols")
            val soIndex = if (isZip(ndkZipBytes)) {
                extractAndIndexSoFiles(ndkZipBytes, tempRoot)
            } else {
                indexRawSoFile(ndkZipBytes, tempRoot, frames, binaryArch)
            }
            if (soIndex.isEmpty()) {
                log.error("{} no .so files found in NDK symbol payload", LOG_PREFIX)
                return rawLines(frames)
            }
            val llvmArch = AndroidNdkLlvmConfig.llvmDefaultArch(binaryArch)
            log.info(
                "{} indexed {} distinct .so names binaryArch={} llvmArch={} symbolizer={}",
                LOG_PREFIX,
                soIndex.size,
                binaryArch,
                llvmArch,
                config.llvmSymbolizerPath,
            )
            return frames.map { symbolizeFrame(it, soIndex, binaryArch, llvmArch) }
        } catch (e: Exception) {
            log.error("{} symbolication failed: {}", LOG_PREFIX, e.message, e)
            return rawLines(frames)
        } finally {
            tempRoot?.toFile()?.deleteRecursively()
        }
    }

    private fun symbolizeFrame(
        frame: NdkFrame,
        soIndex: Map<String, List<IndexedSo>>,
        binaryArch: String?,
        llvmArch: String,
    ): String {
        val lib = frame.ndkLib
        val pc = frame.ndkPc
        if (lib.isNullOrBlank() || pc.isNullOrBlank()) {
            return frame.rawLine ?: ""
        }
        val candidates = selectSoCandidates(soIndex, basename(lib), binaryArch)
        if (candidates.isEmpty()) {
            return frame.rawLine ?: ""
        }
        val relPcHex = normalizeHexForLlvm(pc)
        if (relPcHex == null) {
            log.warn("{} bad PC for frame lib={} pc={}", LOG_PREFIX, lib, pc)
            return frame.rawLine ?: ""
        }

        for (candidate in candidates) {
            val pretty = runLlvmSymbolizer(candidate.path, relPcHex, llvmArch)
            if (pretty.isEmpty || IosLlvmSymbolicator.isUnhelpfulLlvmSymbolication(pretty.get())) {
                continue
            }
            return formatSymbolicatedFrame(frame, pretty.get())
        }
        return frame.rawLine ?: ""
    }

    private fun runLlvmSymbolizer(soPath: Path, relPcHex: String, llvmArch: String): Optional<String> {
        return try {
            val proc = ProcessBuilder(
                config.llvmSymbolizerPath,
                "--obj=${soPath.toAbsolutePath()}",
                "--default-arch=$llvmArch",
                "--demangle",
            )
                .redirectErrorStream(true)
                .start()
            proc.outputStream.use { it.write("$relPcHex\n".toByteArray(StandardCharsets.UTF_8)) }
            val out = readLimited(proc.inputStream, config.maxCaptureChars)
            val finished = proc.waitFor(50, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                log.warn("{} llvm-symbolizer timed out obj={}", LOG_PREFIX, soPath.fileName)
                return Optional.empty()
            }
            if (proc.exitValue() != 0) {
                log.warn("{} llvm-symbolizer exit={} obj={}", LOG_PREFIX, proc.exitValue(), soPath.fileName)
            }
            val parsed = IosLlvmSymbolicator.parseSymbolizerOutput(out)
            if (parsed.isEmpty()) {
                Optional.empty()
            } else {
                Optional.of(parsed[0])
            }
        } catch (e: Exception) {
            log.error("{} llvm-symbolizer failed obj={} msg={}", LOG_PREFIX, soPath.fileName, e.message)
            Optional.empty()
        }
    }

    companion object {
        const val LOG_PREFIX = "[PULSE NDK SYMBOLICATOR]"

        private val log = LoggerFactory.getLogger(AndroidNdkLlvmSymbolicator::class.java)

        private val NDK_FRAME =
            Pattern.compile("^\\s*#(\\d+)\\s+pc\\s+(\\S+)\\s+(\\S+)(?:\\s+\\(([^)]*)\\))?.*$")

        data class IndexedSo(val path: Path, val abi: String?)

        @JvmStatic
        fun normalizeHexForLlvm(hex: String?): String? {
            if (hex.isNullOrBlank()) {
                return null
            }
            val trimmed = hex.trim()
            val withoutPrefix =
                if (trimmed.startsWith("0x", ignoreCase = true)) {
                    trimmed.substring(2)
                } else {
                    trimmed
                }
            if (withoutPrefix.isEmpty() ||
                !withoutPrefix.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            ) {
                return null
            }
            return "0x${withoutPrefix.lowercase()}"
        }

        @JvmStatic
        fun formatSymbolicatedFrame(frame: NdkFrame, llvmPretty: String): String {
            val raw = frame.rawLine ?: return llvmPretty
            val matcher = NDK_FRAME.matcher(raw)
            if (!matcher.matches()) {
                return "$raw ($llvmPretty)"
            }
            val frameNum = matcher.group(1)
            val pc = matcher.group(2)
            val lib = matcher.group(3)
            return String.format(Locale.ROOT, "  #%s pc %s  %s (%s)", frameNum, pc, lib, llvmPretty)
        }

        /**
         * Prefer unstripped `.so` under {@code obj/<abi>/} matching crash {@code binaryArch}
         * (ndk-stack {@code -sym} directory selection).
         */
        @JvmStatic
        fun selectSoCandidates(
            soIndex: Map<String, List<IndexedSo>>,
            libBasename: String,
            binaryArch: String?,
        ): List<IndexedSo> {
            val all = soIndex[libBasename].orEmpty()
            if (all.isEmpty() || binaryArch.isNullOrBlank()) {
                return all
            }
            val normalizedArch = binaryArch.trim().lowercase(Locale.ROOT)
            val matched = all.filter { entry ->
                entry.abi?.equals(normalizedArch, ignoreCase = true) == true
            }
            return if (matched.isNotEmpty()) matched else all
        }

        @JvmStatic
        @Throws(IOException::class)
        fun extractAndIndexSoFiles(zipBytes: ByteArray, destDir: Path): Map<String, List<IndexedSo>> {
            val zipFile = destDir.resolve("upload.zip")
            Files.write(zipFile, zipBytes)
            val index = linkedMapOf<String, MutableList<IndexedSo>>()
            ZipInputStream(Files.newInputStream(zipFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".so")) {
                        val fileName = basename(entry.name)
                        val abi = AndroidNdkLlvmConfig.abiFromZipEntry(entry.name)
                        val out = destDir.resolve(entry.name.replace('/', '_'))
                        Files.createDirectories(out.parent)
                        Files.copy(zis, out)
                        index.computeIfAbsent(fileName) { mutableListOf() }
                            .add(IndexedSo(out, abi))
                    }
                    entry = zis.nextEntry
                }
            }
            return index
        }

        @JvmStatic
        @Throws(IOException::class)
        fun indexRawSoFile(
            soBytes: ByteArray,
            destDir: Path,
            frames: List<NdkFrame>,
            binaryArch: String?,
        ): Map<String, List<IndexedSo>> {
            val soPath = destDir.resolve("upload.so")
            Files.write(soPath, soBytes)
            val index = linkedMapOf<String, MutableList<IndexedSo>>()
            val indexed = IndexedSo(soPath, binaryArch?.trim()?.takeIf { it.isNotEmpty() })
            val libNames = frames.mapNotNull { frame -> frame.ndkLib?.let(::basename) }.filter { it.isNotBlank() }.distinct()
            if (libNames.isEmpty()) {
                index.computeIfAbsent(soPath.fileName.toString()) { mutableListOf() }.add(indexed)
            } else {
                libNames.forEach { lib ->
                    index.computeIfAbsent(lib) { mutableListOf() }.add(indexed)
                }
            }
            return index
        }

        @JvmStatic
        fun isZip(bytes: ByteArray): Boolean =
            bytes.size >= 4 &&
                bytes[0] == 'P'.code.toByte() &&
                bytes[1] == 'K'.code.toByte()

        private fun basename(path: String?): String {
            if (path.isNullOrBlank()) {
                return ""
            }
            var name = path.trim()
            val slash = name.lastIndexOf('/')
            if (slash >= 0) {
                name = name.substring(slash + 1)
            }
            return name
        }

        private fun rawLines(frames: List<NdkFrame>): List<String> =
            frames.map { it.rawLine ?: "" }

        private fun readLimited(input: java.io.InputStream, maxChars: Int): String {
            val sb = StringBuilder()
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    if (sb.length + line!!.length > maxChars) {
                        break
                    }
                    sb.append(line).append('\n')
                }
            }
            return sb.toString()
        }
    }
}
