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
