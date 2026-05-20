package org.dreamhorizon.pulseserver.grouping.phase;

import static org.assertj.core.api.Assertions.assertThat;

import org.dreamhorizon.pulseserver.grouping.model.JavaFrame;
import org.dreamhorizon.pulseserver.grouping.model.JsFrame;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;
import org.junit.jupiter.api.Test;

class FrameUnifierTest {

  @Test
  void shouldStripAnonymousClassDigitsFromJavaClassName() {
    JavaFrame frame = JavaFrame.builder()
        .javaClass("com.example.Foo$1")
        .javaMethod("run")
        .javaFile("Foo.java")
        .javaLine(42)
        .rawLine("at com.example.Foo$1.run(Foo.java:42)")
        .originalPosition(0)
        .build();

    FrameUnifier.unifyJava(frame);

    assertThat(frame.getJavaClass()).isEqualTo("com.example.Foo");
    assertThat(frame.getToken()).isEqualTo("com.example.Foo#run");
  }

  @Test
  void shouldStripMultipleAnonymousClassDigits() {
    JavaFrame frame = JavaFrame.builder()
        .javaClass("com.example.Outer$1$2")
        .javaMethod("call")
        .javaFile("Outer.java")
        .javaLine(7)
        .rawLine("at com.example.Outer$1$2.call(Outer.java:7)")
        .originalPosition(0)
        .build();

    FrameUnifier.unifyJava(frame);

    assertThat(frame.getJavaClass()).isEqualTo("com.example.Outer");
    assertThat(frame.getToken()).isEqualTo("com.example.Outer#call");
  }

  @Test
  void shouldCollapseLambdaCounterIntoCanonicalLambdaMarker() {
    JavaFrame frame = JavaFrame.builder()
        .javaClass("com.example.Foo")
        .javaMethod("lambda$onClick$3")
        .javaFile("Foo.java")
        .javaLine(99)
        .rawLine("at com.example.Foo.lambda$onClick$3(Foo.java:99)")
        .originalPosition(0)
        .build();

    FrameUnifier.unifyJava(frame);

    assertThat(frame.getJavaMethod()).isEqualTo("lambda");
    assertThat(frame.getToken()).isEqualTo("com.example.Foo#lambda");
  }

  @Test
  void shouldBeIdempotentForJavaFrames() {
    JavaFrame frame = JavaFrame.builder()
        .javaClass("com.example.Foo$1")
        .javaMethod("lambda$onClick$3")
        .javaFile("Foo.java")
        .javaLine(99)
        .rawLine("at com.example.Foo$1.lambda$onClick$3(Foo.java:99)")
        .originalPosition(0)
        .build();

    FrameUnifier.unifyJava(frame);
    String tokenAfterFirstPass = frame.getToken();
    String classAfterFirstPass = frame.getJavaClass();
    String methodAfterFirstPass = frame.getJavaMethod();

    FrameUnifier.unifyJava(frame);

    assertThat(frame.getToken()).isEqualTo(tokenAfterFirstPass);
    assertThat(frame.getJavaClass()).isEqualTo(classAfterFirstPass);
    assertThat(frame.getJavaMethod()).isEqualTo(methodAfterFirstPass);
  }

  @Test
  void shouldLeaveJavaFrameWithoutDigitsOrLambdaUnchanged() {
    JavaFrame frame = JavaFrame.builder()
        .javaClass("com.example.Foo")
        .javaMethod("bar")
        .javaFile("Foo.java")
        .javaLine(10)
        .rawLine("at com.example.Foo.bar(Foo.java:10)")
        .originalPosition(0)
        .build();
    String originalToken = frame.getToken();

    FrameUnifier.unifyJava(frame);

    assertThat(frame.getToken()).isEqualTo(originalToken);
    assertThat(frame.getJavaClass()).isEqualTo("com.example.Foo");
    assertThat(frame.getJavaMethod()).isEqualTo("bar");
  }

  @Test
  void shouldRunAcrossAllJavaFramesInParsedFrames() {
    ParsedFrames frames = new ParsedFrames();
    frames.getJavaFrames().add(JavaFrame.builder()
        .javaClass("com.example.A$1")
        .javaMethod("lambda$run$0")
        .javaFile("A.java")
        .javaLine(1)
        .rawLine("at com.example.A$1.lambda$run$0(A.java:1)")
        .originalPosition(0)
        .build());
    frames.getJavaFrames().add(JavaFrame.builder()
        .javaClass("com.example.B$2")
        .javaMethod("foo")
        .javaFile("B.java")
        .javaLine(2)
        .rawLine("at com.example.B$2.foo(B.java:2)")
        .originalPosition(1)
        .build());

    FrameUnifier.unifyAll(frames);

    assertThat(frames.getJavaFrames().get(0).getJavaClass()).isEqualTo("com.example.A");
    assertThat(frames.getJavaFrames().get(0).getJavaMethod()).isEqualTo("lambda");
    assertThat(frames.getJavaFrames().get(0).getToken()).isEqualTo("com.example.A#lambda");
    assertThat(frames.getJavaFrames().get(1).getJavaClass()).isEqualTo("com.example.B");
    assertThat(frames.getJavaFrames().get(1).getJavaMethod()).isEqualTo("foo");
    assertThat(frames.getJavaFrames().get(1).getToken()).isEqualTo("com.example.B#foo");
  }

  @Test
  void shouldHandleNullParsedFramesGracefully() {
    FrameUnifier.unifyAll(null);
    // no exception expected
  }

  @Test
  void shouldHandleNullJavaFrameGracefully() {
    FrameUnifier.unifyJava(null);
    // no exception expected
  }

  @Test
  void shouldHandleEmptyParsedFrames() {
    ParsedFrames frames = new ParsedFrames();

    FrameUnifier.unifyAll(frames);

    assertThat(frames.getJavaFrames()).isEmpty();
  }

  @Test
  void shouldLeaveJsFramesAlone() {
    ParsedFrames frames = new ParsedFrames();
    JsFrame js = JsFrame.builder()
        .jsFile("/src/foo.js")
        .jsFunction("doSomething")
        .jsLine(1)
        .jsColumn(2)
        .rawLine("at doSomething (/src/foo.js:1:2)")
        .originalPosition(0)
        .build();
    String originalToken = js.getToken();
    frames.getJsFrames().add(js);

    FrameUnifier.unifyAll(frames);

    assertThat(js.getToken()).isEqualTo(originalToken);
  }

  @Test
  void shouldNormalizeClassMethodHelpersDirectly() {
    assertThat(FrameUnifier.normalizeJavaClass("com.example.Foo$1")).isEqualTo("com.example.Foo");
    assertThat(FrameUnifier.normalizeJavaClass(null)).isNull();
    assertThat(FrameUnifier.normalizeJavaClass("")).isEqualTo("");
    assertThat(FrameUnifier.normalizeJavaMethod("lambda$go$3")).isEqualTo("lambda");
    assertThat(FrameUnifier.normalizeJavaMethod("plainMethod")).isEqualTo("plainMethod");
    assertThat(FrameUnifier.normalizeJavaMethod(null)).isNull();
    assertThat(FrameUnifier.normalizeJavaMethod("")).isEqualTo("");
  }
}
