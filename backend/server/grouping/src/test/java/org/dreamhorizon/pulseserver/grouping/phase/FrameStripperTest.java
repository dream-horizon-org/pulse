package org.dreamhorizon.pulseserver.grouping.phase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.dreamhorizon.pulseserver.grouping.model.GroupingRules;
import org.dreamhorizon.pulseserver.grouping.model.JavaFrame;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;
import org.junit.jupiter.api.Test;

class FrameStripperTest {

  private static JavaFrame frameWithToken(String token, int pos) {
    JavaFrame frame = JavaFrame.builder()
        .javaClass("X")
        .javaMethod("y")
        .javaFile("X.java")
        .javaLine(1)
        .rawLine("at X.y(X.java:1)")
        .originalPosition(pos)
        .build();
    frame.setToken(token);
    return frame;
  }

  @Test
  void shouldTagFrameMatchingStripPattern() {
    JavaFrame frame = frameWithToken("kotlinx.coroutines.DispatchedTask#run", 0);
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .stripPattern(Pattern.compile("^kotlinx\\.coroutines\\.(DispatchedTask|CoroutineScheduler\\$Worker)#run$"))
        .build();

    FrameStripper.stripFrames(parsed, rules);

    assertThat(frame.isStripped()).isTrue();
  }

  @Test
  void shouldNotTagNonMatchingFrame() {
    JavaFrame frame = frameWithToken("com.dream11.MyClass#foo", 0);
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .stripPattern(Pattern.compile("^kotlinx\\.coroutines\\..*$"))
        .build();

    FrameStripper.stripFrames(parsed, rules);

    assertThat(frame.isStripped()).isFalse();
  }

  @Test
  void shouldMatchOnFirstPatternHit() {
    JavaFrame frame = frameWithToken("android.os.Looper#loop", 0);
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .stripPattern(Pattern.compile("^never\\.matches$"))
        .stripPattern(Pattern.compile("^android\\.os\\.(Handler|Looper)#.*$"))
        .stripPattern(Pattern.compile("^also\\.never$"))
        .build();

    FrameStripper.stripFrames(parsed, rules);

    assertThat(frame.isStripped()).isTrue();
  }

  @Test
  void shouldNoOpWhenStripPatternsEmpty() {
    JavaFrame frame = frameWithToken("kotlinx.coroutines.DispatchedTask#run", 0);
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);

    FrameStripper.stripFrames(parsed, GroupingRules.empty());

    assertThat(frame.isStripped()).isFalse();
  }

  @Test
  void shouldHandleNullArgumentsGracefully() {
    GroupingRules rules = GroupingRules.builder()
        .stripPattern(Pattern.compile(".*"))
        .build();

    FrameStripper.stripFrames(null, rules);
    FrameStripper.stripFrames(new ParsedFrames(), null);
    // no exception expected
  }

  @Test
  void shouldSkipFramesWithNullToken() {
    JavaFrame frame = frameWithToken("normal#token", 0);
    frame.setToken(null);
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .stripPattern(Pattern.compile(".*"))
        .build();

    FrameStripper.stripFrames(parsed, rules);

    assertThat(frame.isStripped()).isFalse();
  }

  @Test
  void shouldBeIdempotent() {
    JavaFrame frame = frameWithToken("kotlinx.coroutines.DispatchedTask#run", 0);
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .stripPattern(Pattern.compile("^kotlinx\\.coroutines\\..*$"))
        .build();

    FrameStripper.stripFrames(parsed, rules);
    FrameStripper.stripFrames(parsed, rules);

    assertThat(frame.isStripped()).isTrue();
  }
}
