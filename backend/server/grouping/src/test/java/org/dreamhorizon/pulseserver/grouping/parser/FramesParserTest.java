package org.dreamhorizon.pulseserver.grouping.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.dreamhorizon.pulseserver.grouping.model.Lane;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;
import org.junit.jupiter.api.Test;

class FramesParserTest {

  @Test
  void shouldParseJavaStackTraceIntoJavaFrames() {
    String trace = "java.lang.NullPointerException: oops\n"
        + "\tat com.example.myapp.HomeActivity.onCreate(HomeActivity.kt:42)\n"
        + "\tat com.example.myapp.HomeActivity.loadData(HomeActivity.kt:87)";

    ParsedFrames frames = FramesParser.parse(linesOf(trace));

    assertThat(frames.getJavaFrames()).hasSize(2);
    assertThat(frames.getJavaTypes()).contains("java.lang.NullPointerException");
    assertThat(frames.getPrimaryExceptionLane()).isEqualTo(Lane.JAVA);
    assertThat(frames.getJavaFrames().get(0).getJavaClass()).isEqualTo("com.example.myapp.HomeActivity");
    assertThat(frames.getJavaFrames().get(0).getJavaMethod()).isEqualTo("onCreate");
    assertThat(frames.getJavaFrames().get(0).getJavaLine()).isEqualTo(42);
  }

  @Test
  void shouldParseStandardJsStackTrace() {
    String trace = "TypeError: foo is not a function\n"
        + "    at parseInput (/src/app/parser.js:10:5)\n"
        + "    at /src/app/index.js:20:3";

    ParsedFrames frames = FramesParser.parse(linesOf(trace));

    assertThat(frames.getJsFrames()).hasSize(2);
    assertThat(frames.getJsTypes()).contains("TypeError");
    assertThat(frames.getPrimaryExceptionLane()).isEqualTo(Lane.JS);
    assertThat(frames.getJsFrames().get(0).getJsFunction()).isEqualTo("parseInput");
    assertThat(frames.getJsFrames().get(0).getJsLine()).isEqualTo(10);
    assertThat(frames.getJsFrames().get(0).getJsColumn()).isEqualTo(5);
  }

  @Test
  void shouldParseNdkSignalAndFrames() {
    String trace = "signal 11 (SIGSEGV), code 1 (SEGV_MAPERR)\n"
        + "  #00 pc 000000000000abcd  /system/lib64/libc.so (memcpy+24)\n"
        + "  #01 pc 0000000000001234  /data/app/lib/libnative.so (Java_com_example_native+8)";

    ParsedFrames frames = FramesParser.parse(linesOf(trace));

    assertThat(frames.getNdkFrames()).hasSize(2);
    assertThat(frames.getNdkTypes()).contains("SIGSEGV");
    assertThat(frames.getNdkFrames().get(0).getNdkLib()).isEqualTo("libc.so");
    assertThat(frames.getNdkFrames().get(0).getNdkSymbol()).isEqualTo("memcpy");
  }

  @Test
  void shouldFlagReactNativeJavascriptException() {
    String trace = "com.facebook.react.common.JavascriptException: Error: boom\n"
        + "    at HomeScreen (/src/screens/HomeScreen.js:42:10)";

    ParsedFrames frames = FramesParser.parse(linesOf(trace));

    assertThat(frames.isReactNativeJsException()).isTrue();
  }

  @Test
  void shouldReturnEmptyParsedFramesForBlankInput() {
    ParsedFrames frames = FramesParser.parse(List.of("", "  ", "\t"));

    assertThat(frames.getJavaFrames()).isEmpty();
    assertThat(frames.getJsFrames()).isEmpty();
    assertThat(frames.getNdkFrames()).isEmpty();
    assertThat(frames.getIosNativeFrames()).isEmpty();
    assertThat(frames.getPrimaryExceptionLane()).isNull();
  }

  private static List<String> linesOf(String trace) {
    return Arrays.asList(trace.split("\\R"));
  }
}
