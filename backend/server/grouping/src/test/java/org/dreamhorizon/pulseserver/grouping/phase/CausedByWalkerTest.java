package org.dreamhorizon.pulseserver.grouping.phase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.dreamhorizon.pulseserver.grouping.model.JavaFrame;
import org.dreamhorizon.pulseserver.grouping.model.JsFrame;
import org.dreamhorizon.pulseserver.grouping.model.Lane;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;
import org.junit.jupiter.api.Test;

class CausedByWalkerTest {

  @Test
  void shouldReturnFirstTypeAsRootCauseWhenNoChain() {
    ParsedFrames frames = new ParsedFrames();
    frames.getJavaTypes().add("java.lang.NullPointerException");
    frames.getJavaFrames().add(JavaFrame.builder()
        .javaClass("com.example.Foo")
        .javaMethod("bar")
        .rawLine("at com.example.Foo.bar(Foo.java:1)")
        .originalPosition(0)
        .build());

    CausedByWalker.RootCauseInfo info = CausedByWalker.walk(frames, Lane.JAVA);

    assertThat(info.getRootCauseType()).isEqualTo("java.lang.NullPointerException");
    assertThat(info.getWrapperTypes()).isEmpty();
    assertThat(info.getRootCauseFrames()).hasSize(1);
    assertThat(info.getAllTypesForSignature()).containsExactly("java.lang.NullPointerException");
  }

  @Test
  void shouldIdentifyRootCauseInTwoLevelChain() {
    ParsedFrames frames = new ParsedFrames();
    frames.getJavaTypes().add("java.lang.RuntimeException");
    frames.getJavaTypes().add("java.lang.NullPointerException");

    CausedByWalker.RootCauseInfo info = CausedByWalker.walk(frames, Lane.JAVA);

    assertThat(info.getRootCauseType()).isEqualTo("java.lang.NullPointerException");
    assertThat(info.getWrapperTypes()).containsExactly("java.lang.RuntimeException");
    assertThat(info.getAllTypesForSignature())
        .containsExactly("java.lang.RuntimeException", "java.lang.NullPointerException");
  }

  @Test
  void shouldIdentifyRootCauseInThreeLevelChain() {
    ParsedFrames frames = new ParsedFrames();
    frames.getJavaTypes().add("A");
    frames.getJavaTypes().add("B");
    frames.getJavaTypes().add("C");

    CausedByWalker.RootCauseInfo info = CausedByWalker.walk(frames, Lane.JAVA);

    assertThat(info.getRootCauseType()).isEqualTo("C");
    assertThat(info.getWrapperTypes()).containsExactly("A", "B");
    assertThat(info.getAllTypesForSignature()).containsExactly("A", "B", "C");
  }

  @Test
  void shouldNotChainWalkForJsLane() {
    ParsedFrames frames = new ParsedFrames();
    frames.getJsTypes().add("TypeError");
    frames.getJsTypes().add("RangeError");
    frames.getJsFrames().add(JsFrame.builder()
        .jsFile("/src/foo.js")
        .jsFunction("doIt")
        .jsLine(1)
        .jsColumn(2)
        .rawLine("at doIt (/src/foo.js:1:2)")
        .originalPosition(0)
        .build());

    CausedByWalker.RootCauseInfo info = CausedByWalker.walk(frames, Lane.JS);

    assertThat(info.getRootCauseType()).isEqualTo("TypeError");
    assertThat(info.getWrapperTypes()).isEmpty();
    assertThat(info.getRootCauseFrames()).hasSize(1);
  }

  @Test
  void shouldReturnFirstTypeForNdkLane() {
    ParsedFrames frames = new ParsedFrames();
    frames.getNdkTypes().add("SIGSEGV");

    CausedByWalker.RootCauseInfo info = CausedByWalker.walk(frames, Lane.NDK);

    assertThat(info.getRootCauseType()).isEqualTo("SIGSEGV");
    assertThat(info.getWrapperTypes()).isEmpty();
  }

  @Test
  void shouldReturnFirstTypeForIosNativeLane() {
    ParsedFrames frames = new ParsedFrames();
    frames.getIosNativeTypes().add("EXC_BAD_ACCESS");

    CausedByWalker.RootCauseInfo info = CausedByWalker.walk(frames, Lane.IOS_NATIVE);

    assertThat(info.getRootCauseType()).isEqualTo("EXC_BAD_ACCESS");
    assertThat(info.getWrapperTypes()).isEmpty();
  }

  @Test
  void shouldHandleJavaWithEmptyTypes() {
    ParsedFrames frames = new ParsedFrames();
    frames.setJavaTypes(new ArrayList<>());

    CausedByWalker.RootCauseInfo info = CausedByWalker.walk(frames, Lane.JAVA);

    assertThat(info.getRootCauseType()).isNull();
    assertThat(info.getWrapperTypes()).isEmpty();
    assertThat(info.getAllTypesForSignature()).isEmpty();
  }

  @Test
  void shouldHandleJsWithEmptyTypes() {
    ParsedFrames frames = new ParsedFrames();

    CausedByWalker.RootCauseInfo info = CausedByWalker.walk(frames, Lane.JS);

    assertThat(info.getRootCauseType()).isNull();
    assertThat(info.getWrapperTypes()).isEmpty();
  }

  @Test
  void shouldReturnEmptyForNullFramesOrLane() {
    CausedByWalker.RootCauseInfo first = CausedByWalker.walk(null, Lane.JAVA);
    CausedByWalker.RootCauseInfo second = CausedByWalker.walk(new ParsedFrames(), null);

    assertThat(first.getRootCauseType()).isNull();
    assertThat(first.getWrapperTypes()).isEmpty();
    assertThat(first.getRootCauseFrames()).isEmpty();
    assertThat(second.getRootCauseType()).isNull();
    assertThat(second.getWrapperTypes()).isEmpty();
    assertThat(second.getRootCauseFrames()).isEmpty();
  }

  @Test
  void shouldReturnFramesForJavaWhenNoTypes() {
    ParsedFrames frames = new ParsedFrames();
    List<JavaFrame> javaFrames = new ArrayList<>();
    javaFrames.add(JavaFrame.builder()
        .javaClass("com.example.Foo")
        .javaMethod("bar")
        .rawLine("at com.example.Foo.bar(Foo.java:1)")
        .originalPosition(0)
        .build());
    frames.setJavaFrames(javaFrames);

    CausedByWalker.RootCauseInfo info = CausedByWalker.walk(frames, Lane.JAVA);

    assertThat(info.getRootCauseType()).isNull();
    assertThat(info.getRootCauseFrames()).hasSize(1);
  }
}
