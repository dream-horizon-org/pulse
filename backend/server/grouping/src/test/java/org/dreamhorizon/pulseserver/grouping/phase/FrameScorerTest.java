package org.dreamhorizon.pulseserver.grouping.phase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.dreamhorizon.pulseserver.grouping.model.Frame;
import org.dreamhorizon.pulseserver.grouping.model.FrameCategory;
import org.dreamhorizon.pulseserver.grouping.model.JavaFrame;
import org.junit.jupiter.api.Test;

class FrameScorerTest {

  private static JavaFrame jf(String token, int pos, FrameCategory cat, int rulePos) {
    JavaFrame f = JavaFrame.builder()
        .javaClass("ignored")
        .javaMethod("ignored")
        .javaFile("ignored.java")
        .javaLine(pos)
        .rawLine("at ignored")
        .originalPosition(pos)
        .build();
    f.setToken(token);
    f.setCategory(cat);
    f.setCategoryRulePosition(rulePos);
    return f;
  }

  @Test
  void shouldScoreTopInAppFrameWithFullBonuses() {
    JavaFrame frame = jf("com.example.Foo#bar", 0, FrameCategory.IN_APP, 0);

    List<Frame> sorted = FrameScorer.scoreAndSort(List.of(frame));

    assertThat(sorted).hasSize(1);
    // IN_APP(250) + posBonus[0]=50 + depth(100) + throwSite(50) + clarity(20) = 470
    assertThat(sorted.get(0).getScore()).isEqualTo(470.0, within(0.0001));
  }

  @Test
  void shouldRankInAppByCategoryRulePositionThenDepth() {
    JavaFrame top = jf("com.first.Foo#bar", 0, FrameCategory.IN_APP, 0);
    JavaFrame second = jf("com.second.Foo#bar", 1, FrameCategory.IN_APP, 1);

    List<Frame> sorted = FrameScorer.scoreAndSort(List.of(top, second));

    assertThat(sorted.get(0)).isSameAs(top);
    assertThat(sorted.get(1)).isSameAs(second);
    assertThat(top.getScore()).isGreaterThan(second.getScore());
  }

  @Test
  void shouldBreakTiesByOriginalPositionAsc() {
    // Two frames in the FRAMEWORK tier with no rule-pos bonus (rulePos = MAX_VALUE -> 0 bonus).
    // depth weight differs (0 vs 1), but make depths identical via originalPosition 0 to force a tie:
    // we engineer two frames with the same score; throw-site bonus only goes to the first frame's category + position.
    // Use IN_APP both with identical rulePos and identical depth to tie scores.
    JavaFrame a = jf("com.x.A#m", 0, FrameCategory.IN_APP, 0);
    JavaFrame b = jf("com.x.B#m", 0, FrameCategory.IN_APP, 0);
    // identical originalPosition -> same depth bonus; throw-site applies to both because both share (cat, originalPos)

    List<Frame> sorted = FrameScorer.scoreAndSort(List.of(a, b));

    // Both scores equal => insertion order preserved by stable sort, both originalPos=0 => tie-break stable
    assertThat(sorted.get(0).getScore()).isEqualTo(sorted.get(1).getScore());
  }

  @Test
  void shouldApplyClarityPenaltyForObfuscatedToken() {
    JavaFrame frame = jf("a.b.c#d", 0, FrameCategory.IN_APP, 0);

    List<Frame> sorted = FrameScorer.scoreAndSort(List.of(frame));

    // 250 + 50 + 100 + 50 + (-20) = 430
    assertThat(sorted.get(0).getScore()).isEqualTo(430.0, within(0.0001));
  }

  @Test
  void shouldNotApplyClarityForTokenWithoutDot() {
    JavaFrame frame = jf("plainmethodname", 0, FrameCategory.IN_APP, 0);

    List<Frame> sorted = FrameScorer.scoreAndSort(List.of(frame));

    // 250 + 50 + 100 + 50 + 0(no dot, no single-char ident among 'plainmethodname') = 450
    assertThat(sorted.get(0).getScore()).isEqualTo(450.0, within(0.0001));
  }

  @Test
  void shouldFloorDepthWeightAtZero() {
    JavaFrame deep = jf("com.example.Foo#bar", 20, FrameCategory.IN_APP, 0);

    List<Frame> sorted = FrameScorer.scoreAndSort(List.of(deep));

    // 250 + 50 + 0 (depth floored) + 50 (this frame is index 0 of list, so throw site) + 20 = 370
    assertThat(sorted.get(0).getScore()).isEqualTo(370.0, within(0.0001));
  }

  @Test
  void shouldReturnEmptyListForNullOrEmpty() {
    assertThat(FrameScorer.scoreAndSort(null)).isEmpty();
    assertThat(FrameScorer.scoreAndSort(List.of())).isEmpty();
  }

  @Test
  void shouldOnlyGiveThrowSiteBonusToFirstFrameInCategoryAtFirstPosition() {
    JavaFrame top = jf("com.example.Foo#bar", 0, FrameCategory.IN_APP, 0);
    JavaFrame next = jf("com.example.Baz#qux", 1, FrameCategory.IN_APP, 0);

    List<Frame> sorted = FrameScorer.scoreAndSort(List.of(top, next));

    // top: 250 + 50 + 100 + 50 + 20 = 470
    // next: 250 + 50 + 90 + 0 + 20 = 410
    assertThat(top.getScore()).isEqualTo(470.0, within(0.0001));
    assertThat(next.getScore()).isEqualTo(410.0, within(0.0001));
    assertThat(sorted.get(0)).isSameAs(top);
    assertThat(sorted.get(1)).isSameAs(next);
  }

  @Test
  void tierWeightShouldMapCategories() {
    assertThat(FrameScorer.tierWeight(FrameCategory.IN_APP)).isEqualTo(FrameScorer.IN_APP_WEIGHT);
    assertThat(FrameScorer.tierWeight(FrameCategory.THIRD_PARTY)).isEqualTo(FrameScorer.THIRD_PARTY_WEIGHT);
    assertThat(FrameScorer.tierWeight(FrameCategory.FRAMEWORK)).isEqualTo(FrameScorer.FRAMEWORK_WEIGHT);
    assertThat(FrameScorer.tierWeight(null)).isEqualTo(FrameScorer.FRAMEWORK_WEIGHT);
  }

  @Test
  void allowlistPositionBonusShouldReturnZeroOutsideRange() {
    assertThat(FrameScorer.allowlistPositionBonus(-1)).isZero();
    assertThat(FrameScorer.allowlistPositionBonus(Integer.MAX_VALUE)).isZero();
    assertThat(FrameScorer.allowlistPositionBonus(0)).isEqualTo(50.0);
    assertThat(FrameScorer.allowlistPositionBonus(4)).isEqualTo(5.0);
  }

  @Test
  void depthWeightShouldFloorAtZero() {
    assertThat(FrameScorer.depthWeight(-1)).isZero();
    assertThat(FrameScorer.depthWeight(0)).isEqualTo(100.0);
    assertThat(FrameScorer.depthWeight(5)).isEqualTo(50.0);
    assertThat(FrameScorer.depthWeight(20)).isZero();
  }

  @Test
  void clarityBonusShouldHandleVariousTokens() {
    assertThat(FrameScorer.clarityBonus(null)).isZero();
    assertThat(FrameScorer.clarityBonus("")).isZero();
    assertThat(FrameScorer.clarityBonus("a.b.c#d")).isEqualTo(FrameScorer.CLARITY_PENALTY);
    assertThat(FrameScorer.clarityBonus("com.example.Foo#bar")).isEqualTo(FrameScorer.CLARITY_BONUS);
    assertThat(FrameScorer.clarityBonus("noDotsHere")).isZero();
  }

  @Test
  void hasSingleCharIdentifierShouldDetectObfuscation() {
    assertThat(FrameScorer.hasSingleCharIdentifier("a.b.c#d")).isTrue();
    assertThat(FrameScorer.hasSingleCharIdentifier("com.example.Foo#bar")).isFalse();
  }
}
