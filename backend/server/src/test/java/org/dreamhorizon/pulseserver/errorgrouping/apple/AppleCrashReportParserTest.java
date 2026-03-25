package org.dreamhorizon.pulseserver.errorgrouping.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.dreamhorizon.pulseserver.errorgrouping.FramesParser;
import org.dreamhorizon.pulseserver.errorgrouping.IosLlvmSymbolicator;
import org.dreamhorizon.pulseserver.errorgrouping.model.Lane;
import org.junit.jupiter.api.Test;

class AppleCrashReportParserTest {

  private static final String SNIPPET = """
      Process:             PulseIOSExample [98646]
      ...
      Binary Images:
      0x102938000 - 0x10294ffff PulseIOSExample arm64  <b40810f870ce33faa7953d38eb812b63> /path/PulseIOSExample
      """;

  @Test
  void parsesProcessName() {
    assertEquals(Optional.of("PulseIOSExample"), AppleCrashReportParser.parseProcessBinaryName(SNIPPET));
  }

  @Test
  void parseProcessBinaryName_emptyOrNull() {
    assertTrue(AppleCrashReportParser.parseProcessBinaryName(null).isEmpty());
    assertTrue(AppleCrashReportParser.parseProcessBinaryName("").isEmpty());
  }

  @Test
  void parseLoadAddress_nullOrEmptyInputs() {
    assertTrue(AppleCrashReportParser.parseLoadAddressForBinary(null, "x").isEmpty());
    assertTrue(AppleCrashReportParser.parseLoadAddressForBinary("x", null).isEmpty());
    assertTrue(AppleCrashReportParser.parseLoadAddressForBinary("x", "").isEmpty());
  }

  @Test
  void parseUuid_nullOrEmptyInputs() {
    assertTrue(AppleCrashReportParser.parseUuidForBinary(null, "x").isEmpty());
    assertTrue(AppleCrashReportParser.parseUuidForBinary("x", "").isEmpty());
  }

  @Test
  void frameImageMatchesProcess_customDylibSuffix() {
    assertTrue(AppleCrashReportParser.frameImageMatchesProcess("Foo", "Foo.custom.dylib"));
  }

  @Test
  void parsesLoadAndUuidForAppBinary() {
    assertEquals(Optional.of(0x102938000L), AppleCrashReportParser.parseLoadAddressForBinary(SNIPPET, "PulseIOSExample"));
    assertEquals(Optional.of("b40810f870ce33faa7953d38eb812b63"),
        AppleCrashReportParser.parseUuidForBinary(SNIPPET, "PulseIOSExample"));
  }

  @Test
  void parsesCpuTypeForLlvmArchFromBinaryImages() {
    assertEquals(Optional.of("arm64"), AppleCrashReportParser.parseCpuTypeForBinary(SNIPPET, "PulseIOSExample"));
  }

  @Test
  void fileAddressMatchesVmaddrPlusPcMinusLoad() {
    long load = 0x102938000L;
    long pc = 0x102944318L;
    long vmaddr = 0x100000000L;
    long fileAddr = vmaddr + pc - load;
    assertEquals(0x10000c318L, fileAddr);
  }

  @Test
  void parseHexLongAccepts0x() {
    assertEquals(0x102944318L, AppleCrashReportParser.parseHexLong("0x0000000102944318"));
  }

  @Test
  void normalizeUuidFromLlvmOutput() {
    assertEquals("deadbeefdeadbeefdeadbeefdeadbeef",
        IosLlvmSymbolicator.normalizeUuid("DEADBEEF-DEAD-BEEF-DEAD-BEEFDEADBEEF"));
  }

  @Test
  void parseVmaddrAfterTextSegment_matchesHomebrewObjdumpLayout() {
    String llvmDump = """
          segname __TEXT
          vmaddr 0x100000000
        """;
    assertEquals(0x100000000L, IosLlvmSymbolicator.parseVmaddrAfterTextSegment(llvmDump));
  }

  @Test
  void parseUuidFromObjdumpOutput_acceptsSeveralFormats() {
    assertEquals("deadbeefdeadbeefdeadbeefdeadbeef",
        IosLlvmSymbolicator.parseUuidFromObjdumpOutput("UUID: DEADBEEF-DEAD-BEEF-DEAD-BEEFDEADBEEF (arm64)\n"));
    assertEquals("deadbeefdeadbeefdeadbeefdeadbeef",
        IosLlvmSymbolicator.parseUuidFromObjdumpOutput("UUID = <DEADBEEF-DEAD-BEEF-DEAD-BEEFDEADBEEF>\n"));
    assertEquals("deadbeefdeadbeefdeadbeefdeadbeef",
        IosLlvmSymbolicator.parseUuidFromObjdumpOutput("line uuid deadbeefdeadbeefdeadbeefdeadbeef end\n"));
    assertEquals("deadbeefdeadbeefdeadbeefdeadbeef",
        IosLlvmSymbolicator.parseUuidFromObjdumpOutput(
            "      cmd LC_UUID\n  cmdsize 24\n   uuid DEADBEEF-DEAD-BEEF-DEAD-BEEFDEADBEEF\n"));
    assertNull(IosLlvmSymbolicator.parseUuidFromObjdumpOutput(""));
  }

  @Test
  void frameImageMatchesProcess_acceptsDebugDylibAndBasename() {
    assertTrue(AppleCrashReportParser.frameImageMatchesProcess("PulseIOSExample", "PulseIOSExample.debug.dylib"));
    assertTrue(AppleCrashReportParser.frameImageMatchesProcess("PulseIOSExample", "PulseIOSExample.abc.dylib"));
    assertTrue(AppleCrashReportParser.frameImageMatchesProcess("Foo", "/var/containers/Foo"));
    assertFalse(AppleCrashReportParser.frameImageMatchesProcess("Foo", "Bar"));
    assertFalse(AppleCrashReportParser.frameImageMatchesProcess("Foo", "(null)"));
  }

  @Test
  void unhelpfulLlvmSymbolication_detectsUnknownPlaceholder() {
    assertTrue(IosLlvmSymbolicator.isUnhelpfulLlvmSymbolication("?? ??:0:0"));
    assertTrue(IosLlvmSymbolicator.isUnhelpfulLlvmSymbolication("??"));
    assertTrue(IosLlvmSymbolicator.isUnhelpfulLlvmSymbolication("  ??:0:0  "));
    assertTrue(IosLlvmSymbolicator.isUnhelpfulLlvmSymbolication(""));
    assertFalse(IosLlvmSymbolicator.isUnhelpfulLlvmSymbolication("confirmCrash + 12 (App.swift:10:1)"));
  }

  @Test
  void parseSymbolizerOutputMergesSymbolAndLocation() {
    String raw = "main\n" + "file.swift:10:5\n" + "\n" + "foo\n" + "other:1:1\n";
    var lines = IosLlvmSymbolicator.parseSymbolizerOutput(raw);
    assertEquals(2, lines.size());
    assertTrue(lines.get(0).contains("main"));
    assertTrue(lines.get(0).contains("file.swift:10:5"));
  }

  @Test
  void framesParserExtractsIosNativeFramesWithDebugDylibImage() {
    String crash = """
        Process:             PulseIOSExample [1]
        Exception Type:  EXC_CRASH (SIGABRT)
        Thread 0 Crashed:
        0   CoreFoundation                	0x00000001804c88b4 __exceptionPreprocess + 160
        1   PulseIOSExample.debug.dylib   	0x0000000102944318 confirmCrash + 212
        Thread 1:
        0   libsystem_kernel.dylib        	0x00000001029b2680 __workq_kernreturn + 8
        """;
    var parsed = FramesParser.parse(crash.lines().toList());
    assertEquals(2, parsed.getIosNativeFrames().size());
    var appFrame = parsed.getIosNativeFrames().get(1);
    assertEquals("PulseIOSExample.debug.dylib", appFrame.getNdkLib());
    assertTrue(appFrame.isInApp());
  }

  @Test
  void framesParserExtractsCrashedThreadIosNativeFrames() {
    String crash = """
        Process:             PulseIOSExample [1]
        Exception Type:  EXC_CRASH (SIGABRT)
        Thread 0 Crashed:
        0   CoreFoundation                	0x00000001804c88b4 __exceptionPreprocess + 160
        1   PulseIOSExample               	0x0000000102944318 confirmCrash + 212
        Thread 1:
        0   libsystem_kernel.dylib        	0x00000001029b2680 __workq_kernreturn + 8
        """;
    var parsed = FramesParser.parse(crash.lines().toList());
    assertEquals(2, parsed.getIosNativeFrames().size());
    assertEquals(Lane.IOS_NATIVE, parsed.getPrimaryExceptionLane());
    assertEquals("PulseIOSExample", parsed.getIosNativeFrames().get(1).getNdkLib());
    assertEquals("0x0000000102944318", parsed.getIosNativeFrames().get(1).getNdkPc());
    assertTrue(parsed.getIosNativeFrames().get(1).isInApp());
  }
}
