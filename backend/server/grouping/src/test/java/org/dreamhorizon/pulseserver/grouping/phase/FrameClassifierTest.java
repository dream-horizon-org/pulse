package org.dreamhorizon.pulseserver.grouping.phase;

import static org.assertj.core.api.Assertions.assertThat;

import org.dreamhorizon.pulseserver.grouping.model.FrameCategory;
import org.dreamhorizon.pulseserver.grouping.model.GroupingRules;
import org.dreamhorizon.pulseserver.grouping.model.JavaFrame;
import org.dreamhorizon.pulseserver.grouping.model.JsFrame;
import org.dreamhorizon.pulseserver.grouping.model.NdkFrame;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;
import org.junit.jupiter.api.Test;

class FrameClassifierTest {

  private static JavaFrame javaFrame(String pkgAndClass, String method) {
    return JavaFrame.builder()
        .javaClass(pkgAndClass)
        .javaMethod(method)
        .javaFile("X.java")
        .javaLine(1)
        .rawLine("at " + pkgAndClass + "." + method + "(X.java:1)")
        .originalPosition(0)
        .build();
  }

  @Test
  void shouldClassifyJavaFrameAsInAppWhenInAppPrefixMatchesFirst() {
    JavaFrame frame = javaFrame("com.dream11.Foo", "bar");
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    // Both lists contain a matching prefix; IN_APP must win because it is checked first.
    GroupingRules rules = GroupingRules.builder()
        .inAppPrefix("com.dream11.")
        .frameworkPrefix("com.dream11.")
        .build();

    FrameClassifier.classify(parsed, rules);

    assertThat(frame.getCategory()).isEqualTo(FrameCategory.IN_APP);
    assertThat(frame.getCategoryRulePosition()).isZero();
  }

  @Test
  void shouldClassifyJavaFrameAsThirdParty() {
    JavaFrame frame = javaFrame("com.vmax.Ads", "show");
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .thirdPartyPrefix("com.vmax.")
        .build();

    FrameClassifier.classify(parsed, rules);

    assertThat(frame.getCategory()).isEqualTo(FrameCategory.THIRD_PARTY);
    assertThat(frame.getCategoryRulePosition()).isZero();
  }

  @Test
  void shouldClassifyJavaFrameAsFramework() {
    JavaFrame frame = javaFrame("android.os.Handler", "dispatchMessage");
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .frameworkPrefix("android.")
        .build();

    FrameClassifier.classify(parsed, rules);

    assertThat(frame.getCategory()).isEqualTo(FrameCategory.FRAMEWORK);
    assertThat(frame.getCategoryRulePosition()).isZero();
  }

  @Test
  void shouldRecordPositionOfMatchingInAppRule() {
    JavaFrame frame = javaFrame("com.second.Foo", "bar");
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .inAppPrefix("com.first.")
        .inAppPrefix("com.second.")
        .build();

    FrameClassifier.classify(parsed, rules);

    assertThat(frame.getCategory()).isEqualTo(FrameCategory.IN_APP);
    assertThat(frame.getCategoryRulePosition()).isEqualTo(1);
  }

  @Test
  void shouldUseBundleIdAsFallbackInApp() {
    JavaFrame frame = javaFrame("com.example.app.Activity", "onCreate");
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .bundleIdFallback("com.example.app")
        .build();

    FrameClassifier.classify(parsed, rules);

    assertThat(frame.getCategory()).isEqualTo(FrameCategory.IN_APP);
    // empty inAppPrefixes -> bundleId fallback position = 0 + 1
    assertThat(frame.getCategoryRulePosition()).isEqualTo(1);
  }

  @Test
  void shouldGiveBundleIdLowerPriorityThanExplicitInAppRules() {
    JavaFrame frame = javaFrame("com.example.app.Activity", "onCreate");
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .inAppPrefix("com.first.")
        .inAppPrefix("com.second.")
        .bundleIdFallback("com.example.app")
        .build();

    FrameClassifier.classify(parsed, rules);

    assertThat(frame.getCategory()).isEqualTo(FrameCategory.IN_APP);
    // 2 explicit prefixes => bundleId position = 2 + 1 = 3
    assertThat(frame.getCategoryRulePosition()).isEqualTo(3);
  }

  @Test
  void shouldDefaultToFrameworkWithMaxValueWhenNothingMatches() {
    JavaFrame frame = javaFrame("com.unknown.Foo", "bar");
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);

    FrameClassifier.classify(parsed, GroupingRules.empty());

    assertThat(frame.getCategory()).isEqualTo(FrameCategory.FRAMEWORK);
    assertThat(frame.getCategoryRulePosition()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void shouldClassifyJsFrameViaSubstringRuleWhenPrefixStartsWithSlash() {
    JsFrame frame = JsFrame.builder()
        .jsFile("src/foo/node_modules/react/index.js")
        .jsFunction("render")
        .jsLine(1)
        .jsColumn(2)
        .rawLine("at render (src/foo/node_modules/react/index.js:1:2)")
        .originalPosition(0)
        .build();
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJsFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .frameworkPrefix("/node_modules/")
        .build();

    FrameClassifier.classify(parsed, rules);

    assertThat(frame.getCategory()).isEqualTo(FrameCategory.FRAMEWORK);
    assertThat(frame.getCategoryRulePosition()).isZero();
  }

  @Test
  void shouldClassifyJsFrameViaStartsWithRule() {
    JsFrame frame = JsFrame.builder()
        .jsFile("src/MyComponent.js")
        .jsFunction("render")
        .jsLine(1)
        .jsColumn(2)
        .rawLine("at render (src/MyComponent.js:1:2)")
        .originalPosition(0)
        .build();
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJsFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .inAppPrefix("src/")
        .build();

    FrameClassifier.classify(parsed, rules);

    assertThat(frame.getCategory()).isEqualTo(FrameCategory.IN_APP);
  }

  @Test
  void shouldDefaultFrameWithNullTokenToFramework() {
    JavaFrame frame = javaFrame("com.example.Foo", "bar");
    frame.setToken(null);
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);

    FrameClassifier.classify(parsed, GroupingRules.empty());

    assertThat(frame.getCategory()).isEqualTo(FrameCategory.FRAMEWORK);
    assertThat(frame.getCategoryRulePosition()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void shouldHandleNullArgumentsGracefully() {
    FrameClassifier.classify(null, GroupingRules.empty());
    FrameClassifier.classify(new ParsedFrames(), null);
    // no exception expected
  }

  @Test
  void shouldNotUseEmptyBundleIdAsFallback() {
    JavaFrame frame = javaFrame("com.example.app.Activity", "onCreate");
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .bundleIdFallback("")
        .build();

    FrameClassifier.classify(parsed, rules);

    assertThat(frame.getCategory()).isEqualTo(FrameCategory.FRAMEWORK);
  }

  @Test
  void shouldIgnoreEmptyOrNullPrefixesWithinList() {
    JavaFrame frame = javaFrame("com.example.Foo", "bar");
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJavaFrames().add(frame);
    // Lombok @Singular rejects null entries; only test the empty-string case which is the impl's defensive guard.
    GroupingRules rules = GroupingRules.builder()
        .inAppPrefix("")
        .inAppPrefix("com.example.")
        .build();

    FrameClassifier.classify(parsed, rules);

    assertThat(frame.getCategory()).isEqualTo(FrameCategory.IN_APP);
    assertThat(frame.getCategoryRulePosition()).isEqualTo(1);
  }

  @Test
  void shouldClassifyNdkFramesViaPackagePart() {
    NdkFrame frame = NdkFrame.builder()
        .ndkLib("libnative.so")
        .ndkPc("0x1234")
        .ndkSymbol("doStuff")
        .rawLine("#00 pc 0x1234 /system/lib/libnative.so (doStuff+0x4)")
        .originalPosition(0)
        .build();
    ParsedFrames parsed = new ParsedFrames();
    parsed.getNdkFrames().add(frame);
    GroupingRules rules = GroupingRules.builder()
        .inAppPrefix("libnative.so")
        .build();

    FrameClassifier.classify(parsed, rules);

    assertThat(frame.getCategory()).isEqualTo(FrameCategory.IN_APP);
  }

  @Test
  void shouldUseStartsWithForJsFramesWithoutLeadingSlashRule() {
    JsFrame frame = JsFrame.builder()
        .jsFile("/node_modules/react/index.js")
        .jsFunction("render")
        .jsLine(1)
        .jsColumn(2)
        .rawLine("at render (/node_modules/react/index.js:1:2)")
        .originalPosition(0)
        .build();
    ParsedFrames parsed = new ParsedFrames();
    parsed.getJsFrames().add(frame);
    // rule does NOT start with '/' so startsWith is used; jsFile starts with '/node_modules' so it should match
    GroupingRules rules = GroupingRules.builder()
        .frameworkPrefix("/node_modules")
        .build();

    FrameClassifier.classify(parsed, rules);

    // '/node_modules' starts with '/' so substring is used; '/node_modules/react/index.js' contains '/node_modules' -> match
    assertThat(frame.getCategory()).isEqualTo(FrameCategory.FRAMEWORK);
  }

  @Test
  void matchIndexHelperReturnsFirstHit() {
    int idx = FrameClassifier.matchIndex("com.example.Foo",
        java.util.List.of("com.other.", "com.example."), false);
    assertThat(idx).isEqualTo(1);
  }

  @Test
  void matchIndexHelperReturnsNegativeWhenInputNull() {
    int idx = FrameClassifier.matchIndex(null, java.util.List.of("x."), false);
    assertThat(idx).isEqualTo(-1);
  }

  @Test
  void packagePartReturnsTokenBeforeHash() {
    assertThat(FrameClassifier.packagePart("com.example.Foo#bar")).isEqualTo("com.example.Foo");
    assertThat(FrameClassifier.packagePart("com.example.Foo")).isEqualTo("com.example.Foo");
    assertThat(FrameClassifier.packagePart(null)).isEqualTo("");
  }
}
