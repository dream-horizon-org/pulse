package org.dreamhorizon.pulseserver.grouping.phase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.dreamhorizon.pulseserver.grouping.model.Frame;
import org.dreamhorizon.pulseserver.grouping.model.FrameCategory;
import org.dreamhorizon.pulseserver.grouping.model.JavaFrame;
import org.dreamhorizon.pulseserver.grouping.model.Lane;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;
import org.junit.jupiter.api.Test;

class FrameSelectorTest {

  private static JavaFrame jf(String cls, String method, int pos, FrameCategory cat, int rulePos) {
    JavaFrame f = JavaFrame.builder()
        .javaClass(cls)
        .javaMethod(method)
        .javaFile(cls + ".java")
        .javaLine(pos)
        .rawLine("at " + cls + "." + method + "(" + cls + ".java:" + pos + ")")
        .originalPosition(pos)
        .build();
    f.setCategory(cat);
    f.setCategoryRulePosition(rulePos);
    return f;
  }

  @Test
  void shouldReturnAllInAppFramesSortedByCategoryRulePositionThenOriginalPosition() {
    ParsedFrames parsed = new ParsedFrames();
    JavaFrame a = jf("com.a.A", "x", 2, FrameCategory.IN_APP, 1);
    JavaFrame b = jf("com.b.B", "y", 1, FrameCategory.IN_APP, 0);
    JavaFrame c = jf("com.c.C", "z", 3, FrameCategory.IN_APP, 0);
    JavaFrame fw1 = jf("android.os.Handler", "dispatchMessage", 4, FrameCategory.FRAMEWORK, 0);
    JavaFrame fw2 = jf("android.app.Activity", "performCreate", 5, FrameCategory.FRAMEWORK, 0);
    parsed.getJavaFrames().add(a);
    parsed.getJavaFrames().add(b);
    parsed.getJavaFrames().add(c);
    parsed.getJavaFrames().add(fw1);
    parsed.getJavaFrames().add(fw2);

    List<Frame> selected = FrameSelector.select(parsed, Lane.JAVA, 3);

    assertThat(selected).hasSize(3);
    // rule pos 0 first, then within that originalPosition asc; rule pos 1 last
    assertThat(selected.get(0)).isSameAs(b); // rulePos 0, origPos 1
    assertThat(selected.get(1)).isSameAs(c); // rulePos 0, origPos 3
    assertThat(selected.get(2)).isSameAs(a); // rulePos 1
  }

  @Test
  void shouldDropStrippedInAppFrames() {
    ParsedFrames parsed = new ParsedFrames();
    JavaFrame a = jf("com.a.A", "x", 0, FrameCategory.IN_APP, 0);
    a.setStripped(true);
    JavaFrame b = jf("com.b.B", "y", 1, FrameCategory.IN_APP, 0);
    parsed.getJavaFrames().add(a);
    parsed.getJavaFrames().add(b);

    List<Frame> selected = FrameSelector.select(parsed, Lane.JAVA, 3);

    assertThat(selected).hasSize(1).containsExactly(b);
  }

  @Test
  void shouldFallBackToThirdPartyWhenNoInAppPresent() {
    ParsedFrames parsed = new ParsedFrames();
    JavaFrame tp1 = jf("com.vmax.Ads", "show", 0, FrameCategory.THIRD_PARTY, 0);
    JavaFrame tp2 = jf("com.vmax.Net", "send", 1, FrameCategory.THIRD_PARTY, 0);
    JavaFrame fw = jf("android.os.Handler", "dispatchMessage", 2, FrameCategory.FRAMEWORK, 0);
    parsed.getJavaFrames().add(tp1);
    parsed.getJavaFrames().add(tp2);
    parsed.getJavaFrames().add(fw);

    List<Frame> selected = FrameSelector.select(parsed, Lane.JAVA, 3);

    assertThat(selected).containsExactly(tp1, tp2);
  }

  @Test
  void shouldFallBackToTopNFrameworkFrames() {
    ParsedFrames parsed = new ParsedFrames();
    for (int i = 0; i < 10; i++) {
      parsed.getJavaFrames().add(jf("android.fw.Fw" + i, "m" + i, i, FrameCategory.FRAMEWORK, 0));
    }

    List<Frame> selected = FrameSelector.select(parsed, Lane.JAVA, 3);

    assertThat(selected).hasSize(3);
    assertThat(selected.get(0).getOriginalPosition()).isZero();
    assertThat(selected.get(1).getOriginalPosition()).isEqualTo(1);
    assertThat(selected.get(2).getOriginalPosition()).isEqualTo(2);
  }

  @Test
  void shouldReturnAllFrameworkWhenSmallerThanTopN() {
    ParsedFrames parsed = new ParsedFrames();
    JavaFrame f1 = jf("android.fw.A", "m", 0, FrameCategory.FRAMEWORK, 0);
    JavaFrame f2 = jf("android.fw.B", "m", 1, FrameCategory.FRAMEWORK, 0);
    parsed.getJavaFrames().add(f1);
    parsed.getJavaFrames().add(f2);

    List<Frame> selected = FrameSelector.select(parsed, Lane.JAVA, 5);

    assertThat(selected).containsExactly(f1, f2);
  }

  @Test
  void wasFallbackShouldBeTrueForFrameworkOnlySelection() {
    JavaFrame f1 = jf("android.fw.A", "m", 0, FrameCategory.FRAMEWORK, 0);
    JavaFrame f2 = jf("android.fw.B", "m", 1, FrameCategory.FRAMEWORK, 0);

    assertThat(FrameSelector.wasFallback(List.of(f1, f2))).isTrue();
  }

  @Test
  void wasFallbackShouldBeFalseWhenInAppPresent() {
    JavaFrame in = jf("com.a.A", "x", 0, FrameCategory.IN_APP, 0);
    JavaFrame fw = jf("android.fw.B", "m", 1, FrameCategory.FRAMEWORK, 0);

    assertThat(FrameSelector.wasFallback(List.of(in, fw))).isFalse();
  }

  @Test
  void wasFallbackShouldBeFalseForEmptyOrNullList() {
    assertThat(FrameSelector.wasFallback(List.of())).isFalse();
    assertThat(FrameSelector.wasFallback(null)).isFalse();
  }

  @Test
  void shouldReturnEmptyListWhenLaneHasNoFrames() {
    ParsedFrames parsed = new ParsedFrames();

    List<Frame> selected = FrameSelector.select(parsed, Lane.JAVA, 3);

    assertThat(selected).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenInputsNull() {
    assertThat(FrameSelector.select(null, Lane.JAVA, 3)).isEmpty();
    assertThat(FrameSelector.select(new ParsedFrames(), null, 3)).isEmpty();
  }
}
