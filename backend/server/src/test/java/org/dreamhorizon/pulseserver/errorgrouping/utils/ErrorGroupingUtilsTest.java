package org.dreamhorizon.pulseserver.errorgrouping.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dreamhorizon.pulseserver.errorgrouping.model.Lane;
import org.junit.jupiter.api.Test;

class ErrorGroupingUtilsTest {

  @Test
  void platformTag_coversAllLanes() {
    assertEquals("js", ErrorGroupingUtils.platformTag(Lane.JS));
    assertEquals("java", ErrorGroupingUtils.platformTag(Lane.JAVA));
    assertEquals("android-ndk", ErrorGroupingUtils.platformTag(Lane.NDK));
    assertEquals("ios-native", ErrorGroupingUtils.platformTag(Lane.IOS_NATIVE));
    assertEquals("unknown", ErrorGroupingUtils.platformTag(Lane.UNKNOWN));
  }

  @Test
  void safeInt_invalidReturnsMinusOne() {
    assertEquals(-1, ErrorGroupingUtils.safeInt("x"));
    assertEquals(-1, ErrorGroupingUtils.safeInt(null));
  }

  @Test
  void isJsInApp_nodeModulesFalse() {
    assertFalse(ErrorGroupingUtils.isJsInApp("node_modules/foo.js"));
  }

  @Test
  void shortenJava_andShortenJs_paths() {
    assertEquals("a.b#m", ErrorGroupingUtils.shortenJava("com.example.a.b#m"));
    assertEquals("screens/App.tsx#render", ErrorGroupingUtils.shortenJs("src/screens/App.tsx#render"));
  }

  @Test
  void shortenJava_blankReturnsEmpty() {
    assertEquals("", ErrorGroupingUtils.shortenJava("  "));
  }

  @Test
  void shortenJs_blankReturnsEmpty() {
    assertEquals("", ErrorGroupingUtils.shortenJs(null));
  }

  @Test
  void isJavaInApp_emptyPrefixMatchesAll() {
    assertTrue(ErrorGroupingUtils.isJavaInApp("anything"));
  }

  @Test
  void normalizeJsFunction_boundAndAnonymous() {
    assertEquals("anonymous", ErrorGroupingUtils.normalizeJsFunction(null));
    assertEquals("foo", ErrorGroupingUtils.normalizeJsFunction("bound foo"));
    assertEquals("anonymous", ErrorGroupingUtils.normalizeJsFunction("<anonymous>"));
  }

  @Test
  void sanitizeJsFile_stripsQueryAndHash() {
    assertEquals("unknown", ErrorGroupingUtils.sanitizeJsFile(null));
    assertEquals("a.js", ErrorGroupingUtils.sanitizeJsFile("a.js?v=1#frag"));
  }

  @Test
  void sha1Hex_roundTrip() {
    assertEquals(40, ErrorGroupingUtils.sha1Hex("pulse").length());
  }

  @Test
  void toHex_formatsBytes() {
    assertEquals("00ff", ErrorGroupingUtils.toHex(new byte[] {0, (byte) 0xff}));
  }
}
