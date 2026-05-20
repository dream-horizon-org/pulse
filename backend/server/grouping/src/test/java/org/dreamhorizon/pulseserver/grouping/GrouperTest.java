package org.dreamhorizon.pulseserver.grouping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.dreamhorizon.pulseserver.grouping.model.EventMeta;
import org.dreamhorizon.pulseserver.grouping.model.Group;
import org.dreamhorizon.pulseserver.grouping.model.GroupingRules;
import org.dreamhorizon.pulseserver.grouping.model.MaskRule;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;
import org.dreamhorizon.pulseserver.grouping.parser.FramesParser;
import org.junit.jupiter.api.Test;

class GrouperTest {

  private static final EventMeta ANDROID_META = EventMeta.builder()
      .platform("Android")
      .appVersion("1.0.0")
      .appVersionCode("100")
      .bundleId("com.example.myapp")
      .projectId("default-project")
      .build();

  private static final EventMeta FANCODE_META = EventMeta.builder()
      .platform("Android")
      .appVersion("6.1.0-100034")
      .appVersionCode("100034")
      .bundleId("com.dream11sportsguru")
      .projectId("default-project")
      .build();

  /**
   * Realistic Fancode rule bundle, mirrors the seed data planned in the
   * heuristics spec (Part VII Example 1). Tests reuse this so the golden
   * examples below run against production-shaped rules.
   */
  private static GroupingRules fancodeRules() {
    return GroupingRules.builder()
        // IN_APP — app + first-party + key payment/ad SDKs
        .inAppPrefix("com.dream11sportsguru.")
        .inAppPrefix("com.fancode.")
        .inAppPrefix("com.dream11.")
        .inAppPrefix("in.juspay.")
        .inAppPrefix("com.vmax.")
        .inAppPrefix("androidx.media3.")
        // FRAMEWORK denylist
        .frameworkPrefix("android.")
        .frameworkPrefix("androidx.")
        .frameworkPrefix("java.")
        .frameworkPrefix("kotlin.")
        .frameworkPrefix("kotlinx.coroutines.")
        .frameworkPrefix("com.android.")
        // Strip universal-noise frames (Looper/Handler/dispatcher chains)
        .stripPattern(Pattern.compile("^kotlinx\\.coroutines\\.(DispatchedTask|CoroutineScheduler\\$Worker)#run$"))
        .stripPattern(Pattern.compile("^kotlin\\.coroutines\\.jvm\\.internal\\.BaseContinuationImpl#resumeWith$"))
        .stripPattern(Pattern.compile("^android\\.os\\.(Handler|Looper)#.*$"))
        .stripPattern(Pattern.compile("^android\\.app\\.ActivityThread#main$"))
        .stripPattern(Pattern.compile("^java\\.lang\\.reflect\\.Method#invoke$"))
        .stripPattern(Pattern.compile("^com\\.android\\.internal\\.os\\.(RuntimeInit\\$MethodAndArgsCaller|ZygoteInit)#.*$"))
        // Masking rules
        .maskRule(MaskRule.of(":\\d+", ":N"))
        .maskRule(MaskRule.of("0x[0-9a-fA-F]+", "0xADDR"))
        .build();
  }

  // ---------- Legacy 2-arg API (back-compat) ----------

  @Test
  @SuppressWarnings("deprecation")
  void shouldGroupJavaNullPointerExceptionAtCorrectLaneWithLegacyApi() {
    String trace = "java.lang.NullPointerException: Attempt to invoke virtual method\n"
        + "\tat com.example.myapp.HomeActivity.onCreate(HomeActivity.kt:42)\n"
        + "\tat com.example.myapp.HomeActivity.loadData(HomeActivity.kt:87)\n"
        + "\tat android.app.Activity.performCreate(Activity.java:8050)";

    // legacy 2-arg API: every frame ends up FRAMEWORK -> top-N fallback selects first 3
    Group group = Grouper.group(FramesParser.parse(linesOf(trace)), ANDROID_META);

    assertThat(group.getPlatform()).isEqualTo("java");
    assertThat(group.getGroupId()).startsWith("EXC-").hasSize(14);
    assertThat(group.getDisplayName())
        .contains("java.lang.NullPointerException")
        .contains(group.getGroupId());
    assertThat(group.getSignature()).startsWith("v2|platform:java|exc:");
    assertThat(group.getSignature()).contains("|msg:");
    assertThat(group.getFingerprint()).hasSize(40);
  }

  @Test
  @SuppressWarnings("deprecation")
  void shouldProduceSameGroupIdForIdenticalJavaStackTracesViaLegacyApi() {
    String trace = "java.lang.NullPointerException: oops\n"
        + "\tat com.example.myapp.Foo.bar(Foo.java:10)\n"
        + "\tat com.example.myapp.Foo.baz(Foo.java:20)";

    Group first = Grouper.group(FramesParser.parse(linesOf(trace)), ANDROID_META);
    Group second = Grouper.group(FramesParser.parse(linesOf(trace)), ANDROID_META);

    assertThat(first.getGroupId()).isEqualTo(second.getGroupId());
    assertThat(first.getSignature()).isEqualTo(second.getSignature());
    assertThat(first.getFingerprint()).isEqualTo(second.getFingerprint());
  }

  @Test
  void shouldProduceDifferentGroupIdsForDifferentExceptions() {
    String npe = "java.lang.NullPointerException: x\n\tat com.example.A.f(A.java:1)";
    String ise = "java.lang.IllegalStateException: y\n\tat com.example.A.f(A.java:1)";

    Group g1 = Grouper.group(FramesParser.parse(linesOf(npe)), ANDROID_META, GroupingRules.empty());
    Group g2 = Grouper.group(FramesParser.parse(linesOf(ise)), ANDROID_META, GroupingRules.empty());

    assertThat(g1.getGroupId()).isNotEqualTo(g2.getGroupId());
  }

  // ---------- JS ----------

  @Test
  void shouldGroupReactNativeJsStackTrace() {
    String trace = "TypeError: undefined is not an object (evaluating 'props.user.name')\n"
        + "    at HomeScreen (/src/screens/HomeScreen.js:42:10)\n"
        + "    at renderWithHooks (/node_modules/react-native/Libraries/Renderer.js:14506:18)";

    Group group = Grouper.group(FramesParser.parse(linesOf(trace)), ANDROID_META, GroupingRules.empty());

    assertThat(group.getPlatform()).isEqualTo("js");
    assertThat(group.getDisplayName()).contains("TypeError").contains(group.getGroupId());
    assertThat(group.getSignature()).contains("|platform:js|");
    assertThat(group.getSignature()).contains("|msg:");
  }

  // ---------- NDK ----------

  @Test
  void shouldGroupNdkSignalTrace() {
    String trace = "*** *** *** *** *** *** *** *** *** *** *** *** ***\n"
        + "signal 11 (SIGSEGV), code 1 (SEGV_MAPERR)\n"
        + "  #00 pc 000000000000abcd  /data/app/com.example.myapp/lib/arm64/libnative.so (Java_com_example_native+24)\n"
        + "  #01 pc 0000000000001234  /data/app/com.example.myapp/lib/arm64/libnative.so (callee+8)";

    Group group = Grouper.group(FramesParser.parse(linesOf(trace)), ANDROID_META, GroupingRules.empty());

    assertThat(group.getPlatform()).isEqualTo("android-ndk");
    assertThat(group.getDisplayName()).contains("SIGSEGV").contains(group.getGroupId());
  }

  // ---------- Edge cases ----------

  @Test
  void shouldReturnUnknownLaneForEmptyTrace() {
    Group group = Grouper.group(new ParsedFrames(), ANDROID_META, GroupingRules.empty());

    assertThat(group.getPlatform()).isEqualTo("unknown");
    assertThat(group.getDisplayName()).startsWith("Error");
  }

  @Test
  void shouldHandleBlankLinesInTraceGracefully() {
    ParsedFrames parsed = FramesParser.parse(List.of("", "  ", "\t"));

    Group group = Grouper.group(parsed, ANDROID_META, GroupingRules.empty());

    assertThat(group.getPlatform()).isEqualTo("unknown");
    assertThat(group.getGroupId()).hasSize(14).startsWith("EXC-");
  }

  @Test
  void groupIdShouldBe14CharsAndStartWithExc() {
    String trace = "java.lang.RuntimeException: x\n\tat a.b.C.d(C.java:1)";

    Group group = Grouper.group(FramesParser.parse(linesOf(trace)), ANDROID_META, GroupingRules.empty());

    assertThat(group.getGroupId()).hasSize(14).startsWith("EXC-");
    assertThat(group.getGroupId().substring(4)).matches("[0-9A-F]{10}");
  }

  // ---------- Legacy buildSignature direct ----------

  @Test
  void buildSignatureShouldEmitV2ShapeWithEmptyMessage() {
    // Legacy helper omits the message; signature builder still appends an empty msg segment.
    String sig = Grouper.buildSignature("java", List.of("NPE"), List.of("Foo#bar", "Foo#baz"));

    assertThat(sig).isEqualTo("v2|platform:java|exc:NPE|frames:Foo#bar>Foo#baz|msg:");
  }

  @Test
  void computeGroupIdShouldRejectShortFingerprint() {
    try {
      Grouper.computeGroupId("abc");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).contains("SHA-1");
      return;
    }
    throw new AssertionError("expected IllegalArgumentException");
  }

  // ---------- Golden 1: Fancode IllegalStateException splash crash ----------

  @Test
  void shouldAnchorFancodeSplashCrashOnInAppFrame() {
    // Trace adapted from heuristics/step-by-step-walkthrough.md Example 1 (FcSplashScreen IllegalStateException).
    // Frames already in canonical Class#method shape — same as what's stored in ClickHouse.
    String trace = "java.lang.IllegalStateException: The specified child already has a parent."
        + " You must call removeView() on the child's parent first.\n"
        + "  at android.view.ViewGroup.addViewInner(ViewGroup.java:5042)\n"
        + "  at android.view.ViewGroup.addView(ViewGroup.java:4872)\n"
        + "  at android.view.ViewGroup.addView(ViewGroup.java:4807)\n"
        + "  at androidx.appcompat.app.AppCompatDelegateImpl.e(AppCompatDelegateImpl.java:1)\n"
        + "  at androidx.appcompat.app.AppCompatActivity.addContentView(AppCompatActivity.java:1)\n"
        + "  at com.dream11sportsguru.feature.splashscreen.FcSplashScreen$Companion$addDefaultSplashLayout"
        + ".invokeSuspend(FcSplashScreen.kt:38)\n"
        + "  at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)\n"
        + "  at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:101)\n"
        + "  at android.os.Handler.handleCallback(Handler.java:938)\n"
        + "  at android.os.Handler.dispatchMessage(Handler.java:99)\n"
        + "  at android.os.Looper.loop(Looper.java:223)\n"
        + "  at android.app.ActivityThread.main(ActivityThread.java:7656)\n"
        + "  at java.lang.reflect.Method.invoke(Method.java:1)\n"
        + "  at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:592)\n"
        + "  at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:947)";

    Group group = Grouper.group(FramesParser.parse(linesOf(trace)), FANCODE_META, fancodeRules());

    assertThat(group.getPlatform()).isEqualTo("java");
    assertThat(group.getGroupId()).startsWith("EXC-").hasSize(14);
    assertThat(group.getSignature())
        .contains("|platform:java|")
        .contains("|exc:java.lang.IllegalStateException")
        .contains("com.dream11sportsguru.feature.splashscreen.FcSplashScreen")
        .contains("|msg:");
    // bug anchors on the in-app frame, not framework noise
    assertThat(group.getDisplayName()).contains("IllegalStateException");
    // Line numbers (e.g. :5042) are masked out of the signature
    assertThat(group.getSignature()).doesNotContain(":5042");
  }

  // ---------- Golden 2: Java caused-by chain → root cause wins ----------

  @Test
  void shouldEncodeCausedByChainAndAnchorOnInAppFrame() {
    String trace = "java.lang.RuntimeException: receiver wrapper\n"
        + "\tat com.dream11.MyReceiver.onReceive(MyReceiver.kt:42)\n"
        + "\tat android.app.LoadedApk$ReceiverDispatcher$Args.run(LoadedApk.java:1)\n"
        + "Caused by: java.lang.NullPointerException: inner cause\n"
        + "\tat com.dream11.MyReceiver.onReceive(MyReceiver.kt:42)";

    Group group = Grouper.group(FramesParser.parse(linesOf(trace)), FANCODE_META, fancodeRules());

    // signature must include both exception types joined by '>': RuntimeException>NullPointerException
    assertThat(group.getSignature())
        .contains("|exc:java.lang.RuntimeException>java.lang.NullPointerException|");
    // anchors on the in-app frame
    assertThat(group.getSignature()).contains("com.dream11.MyReceiver");
    // displayName shows "caused by" for chained exceptions
    assertThat(group.getDisplayName()).contains("caused by");
  }

  // ---------- Golden 3: Determinism + ID format ----------

  @Test
  void shouldProduceStableOutputAcrossRunsForSameInput() {
    String trace = "java.lang.IllegalArgumentException: missing argument\n"
        + "\tat com.dream11sportsguru.feature.checkout.CheckoutScreen.validate(CheckoutScreen.kt:99)\n"
        + "\tat android.os.Handler.dispatchMessage(Handler.java:99)";
    GroupingRules rules = fancodeRules();

    Group first = Grouper.group(FramesParser.parse(linesOf(trace)), FANCODE_META, rules);
    Group second = Grouper.group(FramesParser.parse(linesOf(trace)), FANCODE_META, rules);

    assertThat(first.getGroupId()).isEqualTo(second.getGroupId());
    assertThat(first.getSignature()).isEqualTo(second.getSignature());
    assertThat(first.getFingerprint()).isEqualTo(second.getFingerprint());
    assertThat(first.getDisplayName()).isEqualTo(second.getDisplayName());
    assertThat(first.getGroupId()).matches("EXC-[0-9A-F]{10}");
  }

  @Test
  void shouldProduceDifferentGroupIdsForDifferentInputs() {
    String trace1 = "java.lang.IllegalArgumentException: a\n"
        + "\tat com.dream11sportsguru.feature.checkout.CheckoutScreen.validate(CheckoutScreen.kt:99)";
    String trace2 = "java.lang.IllegalArgumentException: a\n"
        + "\tat com.dream11sportsguru.feature.checkout.OtherScreen.validate(OtherScreen.kt:99)";
    GroupingRules rules = fancodeRules();

    Group g1 = Grouper.group(FramesParser.parse(linesOf(trace1)), FANCODE_META, rules);
    Group g2 = Grouper.group(FramesParser.parse(linesOf(trace2)), FANCODE_META, rules);

    assertThat(g1.getGroupId()).isNotEqualTo(g2.getGroupId());
  }

  // ---------- Helpers ----------

  private static List<String> linesOf(String trace) {
    return Arrays.asList(trace.split("\\R"));
  }
}
