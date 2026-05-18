package org.dreamhorizon.pulseserver.errorgrouping.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dreamhorizon.pulseserver.grouping.model.JavaFrame;
import org.dreamhorizon.pulseserver.grouping.model.JsFrame;
import org.dreamhorizon.pulseserver.grouping.model.Lane;
import org.dreamhorizon.pulseserver.grouping.model.NdkFrame;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;
import org.junit.jupiter.api.Test;

class CompleteSymbolicationTest {

  @Test
  void reconstructStackTrace_usesExceptionHeaderWhenPresent() {
    ParsedFrames pf = new ParsedFrames();
    pf.setExceptionHeaderLine("TypeError: boom");
    pf.setPrimaryExceptionLane(Lane.JS);
    CompleteSymbolication cs = new CompleteSymbolication(
        pf, List.of(), List.of(), List.of(), List.of());
    String out = cs.reconstructStackTrace();
    assertTrue(out.startsWith("TypeError: boom\n"));
  }

  @Test
  void reconstructStackTrace_fallbackHeaderFromJsTypes() {
    ParsedFrames pf = new ParsedFrames();
    pf.getJsTypes().add("ReferenceError");
    JsFrame jf = JsFrame.builder()
        .jsFile("f.js")
        .jsFunction("g")
        .jsLine(1)
        .jsColumn(1)
        .rawLine("x")
        .originalPosition(0)
        .build();
    pf.getJsFrames().add(jf);
    CompleteSymbolication cs = new CompleteSymbolication(
        pf, List.of("sym"), List.of(), List.of(), List.of());
    String out = cs.reconstructStackTrace();
    assertTrue(out.contains("ReferenceError"));
    assertTrue(out.contains("at sym"));
  }

  @Test
  void reconstructStackTrace_fallbackHeaderFromIosNativeTypes() {
    ParsedFrames pf = new ParsedFrames();
    pf.getIosNativeTypes().add("EXC_CRASH");
    NdkFrame nf = NdkFrame.builder()
        .lane(Lane.IOS_NATIVE)
        .ndkLib("App")
        .ndkPc("0x1")
        .ndkSymbol("s")
        .rawLine("raw")
        .originalPosition(0)
        .build();
    pf.getIosNativeFrames().add(nf);
    CompleteSymbolication cs = new CompleteSymbolication(
        pf, List.of(), List.of(), List.of(), List.of("ios line"));
    String out = cs.reconstructStackTrace();
    assertTrue(out.contains("EXC_CRASH"));
    assertTrue(out.contains("ios line"));
  }

  @Test
  void reconstructStackTrace_javaFallbackRawWhenNoSymbolication() {
    ParsedFrames pf = new ParsedFrames();
    JavaFrame jf = JavaFrame.builder()
        .javaClass("a.b")
        .javaMethod("m")
        .rawLine("at a.b.m(X:1)")
        .originalPosition(1)
        .build();
    pf.getJavaFrames().add(jf);
    CompleteSymbolication cs = new CompleteSymbolication(
        pf, List.of(), List.of(), List.of(), List.of());
    String out = cs.reconstructStackTrace();
    assertTrue(out.contains("at a.b.m(X:1)"));
  }

  @Test
  void reconstructStackTrace_javaGroupedWhenExpanded() {
    ParsedFrames pf = new ParsedFrames();
    JavaFrame jf = JavaFrame.builder()
        .javaClass("a.b")
        .javaMethod("m")
        .rawLine("at a.b.m(X:1)")
        .originalPosition(2)
        .build();
    pf.getJavaFrames().add(jf);
    CompleteSymbolication cs = new CompleteSymbolication(
        pf, List.of(), List.of("retraced1", "retraced2"), List.of(), List.of());
    String out = cs.reconstructStackTrace();
    assertTrue(out.contains("retraced1"));
    assertTrue(out.contains("retraced2"));
  }

  @Test
  void reconstructStackTrace_fallbackJavaTypeWhenNoHeader() {
    ParsedFrames pf = new ParsedFrames();
    pf.getJavaTypes().add("IllegalArgumentException");
    CompleteSymbolication cs = new CompleteSymbolication(
        pf, List.of(), List.of(), List.of(), List.of());
    String out = cs.reconstructStackTrace();
    assertTrue(out.startsWith("IllegalArgumentException"));
  }

  @Test
  void reconstructStackTrace_fallbackNdkTypeHeader() {
    ParsedFrames pf = new ParsedFrames();
    pf.getNdkTypes().add("SIGSEGV");
    NdkFrame nf = NdkFrame.builder()
        .ndkLib("lib.so")
        .ndkPc("1")
        .ndkSymbol("f")
        .rawLine("r")
        .originalPosition(0)
        .build();
    pf.getNdkFrames().add(nf);
    CompleteSymbolication cs = new CompleteSymbolication(
        pf, List.of(), List.of(), List.of("ndk sym"), List.of());
    String out = cs.reconstructStackTrace();
    assertTrue(out.contains("SIGSEGV"));
    assertTrue(out.contains("ndk sym"));
  }
}
