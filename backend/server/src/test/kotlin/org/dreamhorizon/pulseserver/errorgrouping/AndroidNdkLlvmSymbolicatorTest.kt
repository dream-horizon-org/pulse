package org.dreamhorizon.pulseserver.errorgrouping

import org.dreamhorizon.pulseserver.errorgrouping.model.Lane
import org.dreamhorizon.pulseserver.errorgrouping.model.NdkFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AndroidNdkLlvmSymbolicatorTest {

    private val sym = AndroidNdkLlvmSymbolicator()

    @Test
    fun symbolicateFrames_emptyFrames_returnsEmpty() {
        assertTrue(sym.symbolicateFrames(emptyList(), byteArrayOf(1), "arm64-v8a").isEmpty())
    }

    @Test
    fun symbolicateFrames_nullZip_returnsRawLines() {
        val frame = ndkFrame("  #00 pc 0000000000001234  libdemo.so (trigger+12)")
        val out = sym.symbolicateFrames(listOf(frame), null, "arm64-v8a")
        assertEquals(1, out.size)
        assertEquals(frame.rawLine, out[0])
    }

    @Test
    fun symbolicateFrames_zipWithoutSo_returnsRawLines() {
        val frame = ndkFrame("  #00 pc 0000000000001234  libdemo.so (trigger+12)")
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(ZipEntry("readme.txt"))
            zos.write("hi".toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
        val out = sym.symbolicateFrames(listOf(frame), bos.toByteArray(), "arm64-v8a")
        assertEquals(frame.rawLine, out[0])
    }

    @Test
    fun extractAndIndexSoFiles_indexesByBasenameAndAbi(@TempDir tmp: Path) {
        val zip = zipWithEntries(
            mapOf(
                "arm64-v8a/libdemo.so" to "ELF64",
                "armeabi-v7a/libdemo.so" to "ELF32",
            ),
        )
        val index = AndroidNdkLlvmSymbolicator.extractAndIndexSoFiles(zip, tmp)
        assertTrue(index.containsKey("libdemo.so"))
        assertEquals(2, index["libdemo.so"]!!.size)
        assertTrue(
            index["libdemo.so"]!!.any { it.abi == "arm64-v8a" && Files.isRegularFile(it.path) },
        )
    }

    @Test
    fun selectSoCandidates_prefersMatchingAbi() {
        val arm64 = AndroidNdkLlvmSymbolicator.IndexedSo(Path.of("arm64.so"), "arm64-v8a")
        val arm32 = AndroidNdkLlvmSymbolicator.IndexedSo(Path.of("arm32.so"), "armeabi-v7a")
        val index = mapOf("libdemo.so" to listOf(arm64, arm32))

        val selected = AndroidNdkLlvmSymbolicator.selectSoCandidates(index, "libdemo.so", "arm64-v8a")
        assertEquals(1, selected.size)
        assertEquals("arm64-v8a", selected[0].abi)
    }

    @Test
    fun selectSoCandidates_fallsBackWhenAbiUnknown() {
        val arm64 = AndroidNdkLlvmSymbolicator.IndexedSo(Path.of("arm64.so"), "arm64-v8a")
        val index = mapOf("libdemo.so" to listOf(arm64))

        val selected = AndroidNdkLlvmSymbolicator.selectSoCandidates(index, "libdemo.so", null)
        assertEquals(1, selected.size)
    }

    @Test
    fun abiFromZipEntry_parsesAgpLayout() {
        assertEquals("arm64-v8a", AndroidNdkLlvmConfig.abiFromZipEntry("arm64-v8a/libdemo.so"))
        assertEquals("x86_64", AndroidNdkLlvmConfig.abiFromZipEntry("build/intermediates/cxx/Debug/abc/obj/x86_64/libdemo.so"))
    }

    @Test
    fun llvmDefaultArch_mapsAndroidAbi() {
        assertEquals("aarch64", AndroidNdkLlvmConfig.llvmDefaultArch("arm64-v8a"))
        assertEquals("arm", AndroidNdkLlvmConfig.llvmDefaultArch("armeabi-v7a"))
        assertEquals("i386", AndroidNdkLlvmConfig.llvmDefaultArch("x86"))
        assertEquals("x86_64", AndroidNdkLlvmConfig.llvmDefaultArch("x86_64"))
    }

    @Test
    fun indexRawSoFile_indexesByFrameLibName(@TempDir tmp: Path) {
        val frame = ndkFrame("  #00 pc 0000000000001234  libdemo.so (trigger+12)")
        val index = AndroidNdkLlvmSymbolicator.indexRawSoFile(
            "ELF".toByteArray(),
            tmp,
            listOf(frame),
            "arm64-v8a",
        )
        assertTrue(index.containsKey("libdemo.so"))
        assertEquals(1, index["libdemo.so"]!!.size)
        assertEquals("arm64-v8a", index["libdemo.so"]!![0].abi)
        assertTrue(Files.isRegularFile(index["libdemo.so"]!![0].path))
    }

    @Test
    fun isZip_detectsZipMagic() {
        val zip = zipWithEntries(mapOf("libdemo.so" to "ELF"))
        assertTrue(AndroidNdkLlvmSymbolicator.isZip(zip))
        assertEquals(false, AndroidNdkLlvmSymbolicator.isZip("ELF".toByteArray()))
    }

    @Test
    fun formatSymbolicatedFrame_replacesParenContent() {
        val frame = ndkFrame("  #00 pc 0000000000001234  libdemo.so (trigger+12)")
        val formatted = AndroidNdkLlvmSymbolicator.formatSymbolicatedFrame(
            frame,
            "triggerNativeCrash demo.cpp:42:0",
        )
        assertEquals("  #00 pc 0000000000001234  libdemo.so (triggerNativeCrash demo.cpp:42:0)", formatted)
    }

    @Test
    fun normalizeHexForLlvm_acceptsWithOrWithoutPrefix() {
        assertEquals("0x1234", AndroidNdkLlvmSymbolicator.normalizeHexForLlvm("0x1234"))
        assertEquals("0x1234", AndroidNdkLlvmSymbolicator.normalizeHexForLlvm("1234"))
        assertEquals("0x0000000000001234", AndroidNdkLlvmSymbolicator.normalizeHexForLlvm("0000000000001234"))
    }

    @Test
    fun config_resolvesExplicitSymbolizerPath() {
        val config = AndroidNdkLlvmConfig(llvmSymbolizerPath = "/custom/llvm-symbolizer")
        assertEquals("/custom/llvm-symbolizer", config.llvmSymbolizerPath)
    }

    private fun ndkFrame(rawLine: String): NdkFrame =
        NdkFrame.builder()
            .lane(Lane.NDK)
            .ndkLib("libdemo.so")
            .ndkPc("0x1234")
            .ndkSymbol("trigger")
            .rawLine(rawLine)
            .originalPosition(0)
            .build()

    private fun zipWithEntries(entries: Map<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            entries.forEach { (path, content) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}
