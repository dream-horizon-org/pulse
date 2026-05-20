package org.dreamhorizon.pulseserver.grouping.phase;

import static org.assertj.core.api.Assertions.assertThat;

import org.dreamhorizon.pulseserver.grouping.model.GroupingRules;
import org.dreamhorizon.pulseserver.grouping.model.JavaFrame;
import org.dreamhorizon.pulseserver.grouping.model.MaskRule;
import org.dreamhorizon.pulseserver.grouping.model.NdkFrame;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;
import org.junit.jupiter.api.Test;

class FrameMaskerTest {

  private static JavaFrame javaFrameWithToken(String token) {
    JavaFrame frame = JavaFrame.builder()
        .javaClass("X")
        .javaMethod("y")
        .javaFile("X.java")
        .javaLine(1)
        .rawLine("at X.y(X.java:1)")
        .originalPosition(0)
        .build();
    frame.setToken(token);
    return frame;
  }

  @Test
  void shouldMaskLineNumbersInsideTokens() {
    JavaFrame frame = javaFrameWithToken("com.example.Foo#bar:142");
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .maskRule(MaskRule.of(":\\d+", ":N"))
        .build();

    FrameMasker.maskFrames(parsed, rules);

    assertThat(frame.getToken()).isEqualTo("com.example.Foo#bar:N");
  }

  @Test
  void shouldMaskHexAddressesInsideTokens() {
    NdkFrame frame = NdkFrame.builder()
        .ndkLib("libfoo.so")
        .ndkPc("00000000abcd")
        .ndkSymbol("sym+0x1234")
        .rawLine("#00 pc 00000000abcd /system/lib/libfoo.so (sym+0x1234)")
        .originalPosition(0)
        .build();
    ParsedFrames parsed = new ParsedFrames();
    parsed.getNdkFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .maskRule(MaskRule.of("0x[0-9a-fA-F]+", "0xADDR"))
        .build();

    FrameMasker.maskFrames(parsed, rules);

    assertThat(frame.getToken()).isEqualTo("libfoo.so#sym+0xADDR");
  }

  @Test
  void shouldMaskUuidInsideTokens() {
    JavaFrame frame = javaFrameWithToken("com.example.Foo#bar-aabbccdd-1122-3344-5566-778899aabbcc");
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .maskRule(MaskRule.of("\\b[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\b", "<UUID>"))
        .build();

    FrameMasker.maskFrames(parsed, rules);

    assertThat(frame.getToken()).isEqualTo("com.example.Foo#bar-<UUID>");
  }

  @Test
  void shouldBeIdempotent() {
    JavaFrame frame = javaFrameWithToken("com.example.Foo#bar:142");
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .maskRule(MaskRule.of(":\\d+", ":N"))
        .build();

    FrameMasker.maskFrames(parsed, rules);
    String tokenAfterFirstPass = frame.getToken();
    FrameMasker.maskFrames(parsed, rules);

    assertThat(frame.getToken()).isEqualTo(tokenAfterFirstPass);
  }

  @Test
  void shouldApplyMaskRulesInOrder() {
    JavaFrame frame = javaFrameWithToken("Foo#bar:42");
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    // first rule replaces digits with X, then second masks line numbers
    GroupingRules rules = GroupingRules.builder()
        .maskRule(MaskRule.of(":\\d+", ":N"))
        .maskRule(MaskRule.of("N", "M"))
        .build();

    FrameMasker.maskFrames(parsed, rules);

    assertThat(frame.getToken()).isEqualTo("Foo#bar:M");
  }

  @Test
  void shouldNoOpWhenMaskRulesEmpty() {
    JavaFrame frame = javaFrameWithToken("com.example.Foo#bar:142");
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    GroupingRules rules = GroupingRules.empty();

    FrameMasker.maskFrames(parsed, rules);

    assertThat(frame.getToken()).isEqualTo("com.example.Foo#bar:142");
  }

  @Test
  void shouldHandleNullParsedFramesAndRulesGracefully() {
    GroupingRules rules = GroupingRules.builder().maskRule(MaskRule.of(":\\d+", ":N")).build();

    FrameMasker.maskFrames(null, rules);
    FrameMasker.maskFrames(new ParsedFrames(), null);
    // no exception expected
  }

  @Test
  void shouldReturnEmptyStringForNullMessage() {
    GroupingRules rules = GroupingRules.builder().maskRule(MaskRule.of(":\\d+", ":N")).build();

    assertThat(FrameMasker.maskMessage(null, rules)).isEmpty();
  }

  @Test
  void shouldMaskMessage() {
    GroupingRules rules = GroupingRules.builder()
        .maskRule(MaskRule.of("line \\d+", "line N"))
        .build();

    assertThat(FrameMasker.maskMessage("Failed at line 142", rules))
        .isEqualTo("Failed at line N");
  }

  @Test
  void shouldReturnMessageUnchangedWhenRulesNullOrEmpty() {
    assertThat(FrameMasker.maskMessage("hello", null)).isEqualTo("hello");
    assertThat(FrameMasker.maskMessage("hello", GroupingRules.empty())).isEqualTo("hello");
  }

  @Test
  void shouldSkipFrameWithNullToken() {
    JavaFrame frame = javaFrameWithToken("ok#token");
    frame.setToken(null);
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .maskRule(MaskRule.of(":\\d+", ":N"))
        .build();

    FrameMasker.maskFrames(parsed, rules);

    assertThat(frame.getToken()).isNull();
  }
}
