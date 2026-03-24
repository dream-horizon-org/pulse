package org.dreamhorizon.pulseserver.errorgrouping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.dreamhorizon.pulseserver.errorgrouping.model.NdkFrame;
import org.dreamhorizon.pulseserver.errorgrouping.model.Lane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Extra coverage for {@link IosLlvmSymbolicator} without requiring LLVM on PATH. */
class IosLlvmSymbolicatorTest {

  private final IosLlvmSymbolicator sym = new IosLlvmSymbolicator();

  @Test
  void symbolicateFrames_emptyFrames_returnsEmpty() {
    assertTrue(sym.symbolicateFrames(List.of(), "raw", new byte[] {1}).isEmpty());
  }

  @Test
  void symbolicateFrames_nullDsym_returnsRawLines() {
    NdkFrame f = NdkFrame.builder()
        .lane(Lane.IOS_NATIVE)
        .ndkLib("App")
        .ndkPc("0x1000")
        .ndkSymbol("x")
        .rawLine("raw line")
        .originalPosition(0)
        .build();
    List<String> out = sym.symbolicateFrames(List.of(f), "Process: App [1]\n", null);
    assertEquals(1, out.size());
    assertEquals("raw line", out.get(0));
  }

  @Test
  void symbolicateFrames_emptyDsymBytes_returnsRawLines() {
    NdkFrame f = NdkFrame.builder()
        .lane(Lane.IOS_NATIVE)
        .ndkLib("App")
        .ndkPc("0x1000")
        .ndkSymbol("x")
        .rawLine("raw line")
        .originalPosition(0)
        .build();
    List<String> out = sym.symbolicateFrames(List.of(f), "Process: App [1]\n", new byte[0]);
    assertEquals("raw line", out.get(0));
  }

  @Test
  void parseVmaddrAfterTextSegment_usesRegexFallback() {
    String out = """
        random header
        segname __TEXT
        vmaddr 0xdeadbeef0000
        """;
    assertEquals(0xdeadbeef0000L, IosLlvmSymbolicator.parseVmaddrAfterTextSegment(out));
  }

  @Test
  void parseSymbolizerOutput_symbolOnlyAtEnd() {
    List<String> lines = IosLlvmSymbolicator.parseSymbolizerOutput("onlySymbol\n");
    assertEquals(1, lines.size());
    assertEquals("onlySymbol", lines.get(0));
  }

  @Test
  void symbolicateFrames_corruptZipBytes_returnsRawLines() {
    NdkFrame f = NdkFrame.builder()
        .lane(Lane.IOS_NATIVE)
        .ndkLib("App")
        .ndkPc("0x1000")
        .ndkSymbol("x")
        .rawLine("raw line")
        .originalPosition(0)
        .build();
    byte[] garbage = new byte[] {0x00, 0x01, 0x02};
    List<String> out = sym.symbolicateFrames(List.of(f), "Process: App [1]\n", garbage);
    assertEquals(1, out.size());
    assertEquals("raw line", out.get(0));
  }

  @Test
  void symbolicateFrames_zipWithoutDwarf_returnsRawLines() throws Exception {
    NdkFrame f = NdkFrame.builder()
        .lane(Lane.IOS_NATIVE)
        .ndkLib("App")
        .ndkPc("0x1000")
        .ndkSymbol("x")
        .rawLine("raw line")
        .originalPosition(0)
        .build();
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(bos)) {
      zos.putNextEntry(new ZipEntry("readme.txt"));
      zos.write("hi".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    List<String> out = sym.symbolicateFrames(List.of(f), "Process: App [1]\n", bos.toByteArray());
    assertEquals(1, out.size());
    assertEquals("raw line", out.get(0));
  }

  @Test
  void extractDsymAndFindDwarf_findsFirstDwarfWhenNoPreferredUuid(@TempDir Path tmp) throws Exception {
    byte[] zip = minimalDsymZip("PulseIOSExample", "dummy");
    Path dwarf = IosLlvmSymbolicator.extractDsymAndFindDwarf(zip, tmp, null);
    assertNotNull(dwarf);
    assertTrue(Files.isRegularFile(dwarf));
    assertTrue(dwarf.toString().replace('\\', '/').contains(".dSYM/Contents/Resources/DWARF/"));
  }

  @Test
  void deleteRecursively_removesTree(@TempDir Path tmp) throws IOException {
    Path a = tmp.resolve("a");
    Files.createDirectories(a);
    Files.writeString(a.resolve("f.txt"), "x");
    IosLlvmSymbolicator.deleteRecursively(a);
    assertFalse(Files.exists(a));
  }

  @Test
  void parseVmaddr_lineWithPaddingThenHex_usesTokenScan() {
    String out = """
        segname __TEXT
        vmaddr pad 0xfeedface
        """;
    assertEquals(0xfeedfaceL, IosLlvmSymbolicator.parseVmaddrAfterTextSegment(out));
  }

  @Test
  void parseUuidFromObjdumpOutput_32HexOnUuidLine() {
    String s = "some uuid line DEADBEEF0123456789ABCDEF01234567 trailing\n";
    assertEquals(
        "deadbeef0123456789abcdef01234567",
        IosLlvmSymbolicator.parseUuidFromObjdumpOutput(s));
  }

  @Test
  void parseSymbolizerOutput_twoAddressBlocks() {
    String raw = "sym1\nfile1.swift:1:1\n\nsym2\nfile2.swift:2:2\n";
    List<String> lines = IosLlvmSymbolicator.parseSymbolizerOutput(raw);
    assertEquals(2, lines.size());
  }

  @Test
  void parseVmaddrAfterTextSegment_nullBlank() {
    assertNull(IosLlvmSymbolicator.parseVmaddrAfterTextSegment(null));
    assertNull(IosLlvmSymbolicator.parseVmaddrAfterTextSegment("   "));
  }

  private static byte[] minimalDsymZip(String appName, String dwarfContent) throws IOException {
    String path = appName + ".app.dSYM/Contents/Resources/DWARF/" + appName;
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(bos)) {
      zos.putNextEntry(new ZipEntry(path));
      zos.write(dwarfContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    return bos.toByteArray();
  }
}
