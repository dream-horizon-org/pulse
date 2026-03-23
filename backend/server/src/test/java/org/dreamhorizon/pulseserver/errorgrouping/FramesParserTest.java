package org.dreamhorizon.pulseserver.errorgrouping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.dreamhorizon.pulseserver.errorgrouping.model.ParsedFrames;
import org.junit.jupiter.api.Test;

class FramesParserTest {

  @Test
  void shouldParseRealAppleCrashFromSampleFile() throws IOException {
    List<String> lines = Files.readAllLines(Path.of("scripts/ios/sample_pulse_crash.txt"));

    ParsedFrames parsed = FramesParser.parse(lines);

    // Real sample has 31 frames under "Thread 0 Crashed:" and then "Thread 1:".
    assertEquals(31, parsed.getIosNativeFrames().size());
    assertEquals(0, parsed.getNdkFrames().size());
    assertEquals("CoreFoundation", parsed.getIosNativeFrames().get(0).getNdkLib());
    assertEquals("0x000000010294248c", parsed.getIosNativeFrames().get(3).getNdkPc());
    assertEquals(
        "$s15PulseIOSExample18MainViewControllerC22crashNSExceptionTapped33_0C0091EDD96CE7CF640B1457C7517B58LLyyFyycfU_",
        parsed.getIosNativeFrames().get(3).getNdkSymbol());
    // Ensure parser stops at crashed-thread boundary and does not include Thread 1+ frames.
    assertEquals("(null)", parsed.getIosNativeFrames().get(30).getNdkLib());
    assertFalse(parsed.getIosNativeFrames().stream()
        .anyMatch(frame -> "0x00000001029b2680".equals(frame.getNdkPc())));
  }

  @Test
  void shouldParseAppleNullSymbolAsAddressFrame() {
    List<String> lines = List.of(
        "Thread 0 Crashed:",
        "29  (null) 0x0000000102c813d8 0x0 + 4341634008",
        "Thread 1:"
    );

    ParsedFrames parsed = FramesParser.parse(lines);

    assertEquals(1, parsed.getIosNativeFrames().size());
    assertEquals("(null)", parsed.getIosNativeFrames().get(0).getNdkLib());
    assertEquals("0x0000000102c813d8", parsed.getIosNativeFrames().get(0).getNdkPc());
    assertNull(parsed.getIosNativeFrames().get(0).getNdkSymbol());
  }

  @Test
  void shouldStillParseAndroidNdkFrames() {
    List<String> lines = List.of(
        "#00 pc 0000000000012345 /data/app/libnative.so (nativeFunc+12)"
    );

    ParsedFrames parsed = FramesParser.parse(lines);

    assertEquals(1, parsed.getNdkFrames().size());
    assertEquals("libnative.so", parsed.getNdkFrames().get(0).getNdkLib());
    assertEquals("0000000000012345", parsed.getNdkFrames().get(0).getNdkPc());
    assertEquals("nativeFunc", parsed.getNdkFrames().get(0).getNdkSymbol());
  }

  @Test
  void shouldPreferAndroidNdkPatternWhenBothAppleAndAndroidLikeDataExist() {
    List<String> lines = List.of(
        "#00 pc 0000000000012345 /data/app/libnative.so (nativeFunc+12)",
        "Thread 0 Crashed:",
        "0   PulseIOSExample                0x000000010294248c swift_symbol_one + 164",
        "Thread 1:"
    );

    ParsedFrames parsed = FramesParser.parse(lines);

    assertEquals(1, parsed.getNdkFrames().size());
    assertEquals(1, parsed.getIosNativeFrames().size());
    assertEquals("libnative.so", parsed.getNdkFrames().get(0).getNdkLib());
    assertEquals("PulseIOSExample", parsed.getIosNativeFrames().get(0).getNdkLib());
  }

  @Test
  void shouldNotAffectJsParsingWhenAppleRulesAreAdded() {
    List<String> lines = List.of(
        "TypeError: undefined is not an object",
        "    at render (index.android.bundle:123:45)",
        "    at anonymous (index.android.bundle:456:78)"
    );

    ParsedFrames parsed = FramesParser.parse(lines);

    assertEquals(2, parsed.getJsFrames().size());
    assertEquals(0, parsed.getNdkFrames().size());
    assertEquals(0, parsed.getIosNativeFrames().size());
    assertEquals("render", parsed.getJsFrames().get(0).getJsFunction());
    assertEquals("index.android.bundle", parsed.getJsFrames().get(0).getJsFile());
  }

  @Test
  void shouldParseJsAndAndroidNdkTogetherWithoutInterference() {
    List<String> lines = List.of(
        "TypeError: undefined is not an object",
        "    at render (index.android.bundle:123:45)",
        "#00 pc 0000000000012345 /data/app/libnative.so (nativeFunc+12)"
    );

    ParsedFrames parsed = FramesParser.parse(lines);

    assertEquals(1, parsed.getJsFrames().size());
    assertEquals(1, parsed.getNdkFrames().size());
    assertEquals("render", parsed.getJsFrames().get(0).getJsFunction());
    assertEquals("libnative.so", parsed.getNdkFrames().get(0).getNdkLib());
  }
}
