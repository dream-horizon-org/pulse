package org.dreamhorizon.pulseserver.grouping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.dreamhorizon.pulseserver.grouping.model.EventMeta;
import org.dreamhorizon.pulseserver.grouping.model.Group;
import org.dreamhorizon.pulseserver.grouping.model.Lane;
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

  // ---------- Java ----------

  @Test
  void shouldGroupJavaNullPointerExceptionAtCorrectLane() {
    String trace = "java.lang.NullPointerException: Attempt to invoke virtual method\n"
        + "\tat com.example.myapp.HomeActivity.onCreate(HomeActivity.kt:42)\n"
        + "\tat com.example.myapp.HomeActivity.loadData(HomeActivity.kt:87)\n"
        + "\tat android.app.Activity.performCreate(Activity.java:8050)";

    Group group = Grouper.group(FramesParser.parse(linesOf(trace)), ANDROID_META);

    assertThat(group.getPlatform()).isEqualTo("java");
    assertThat(group.getGroupId()).startsWith("EXC-").hasSize(14);
    assertThat(group.getDisplayName())
        .contains("java.lang.NullPointerException")
        .contains("HomeActivity#onCreate")
        .contains(group.getGroupId());
    assertThat(group.getSignature()).startsWith("v2|platform:java|exc:");
    assertThat(group.getFingerprint()).hasSize(40);
  }

  @Test
  void shouldProduceSameGroupIdForIdenticalJavaStackTraces() {
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

    Group g1 = Grouper.group(FramesParser.parse(linesOf(npe)), ANDROID_META);
    Group g2 = Grouper.group(FramesParser.parse(linesOf(ise)), ANDROID_META);

    assertThat(g1.getGroupId()).isNotEqualTo(g2.getGroupId());
  }

  // ---------- JS ----------

  @Test
  void shouldGroupReactNativeJsStackTrace() {
    String trace = "TypeError: undefined is not an object (evaluating 'props.user.name')\n"
        + "    at HomeScreen (/src/screens/HomeScreen.js:42:10)\n"
        + "    at renderWithHooks (/node_modules/react-native/Libraries/Renderer.js:14506:18)";

    Group group = Grouper.group(FramesParser.parse(linesOf(trace)), ANDROID_META);

    assertThat(group.getPlatform()).isEqualTo("js");
    assertThat(group.getDisplayName()).contains("TypeError").contains(group.getGroupId());
    assertThat(group.getSignature()).contains("|platform:js|");
  }

  // ---------- NDK ----------

  @Test
  void shouldGroupNdkSignalTrace() {
    String trace = "*** *** *** *** *** *** *** *** *** *** *** *** ***\n"
        + "signal 11 (SIGSEGV), code 1 (SEGV_MAPERR)\n"
        + "  #00 pc 000000000000abcd  /data/app/com.example.myapp/lib/arm64/libnative.so (Java_com_example_native+24)\n"
        + "  #01 pc 0000000000001234  /data/app/com.example.myapp/lib/arm64/libnative.so (callee+8)";

    Group group = Grouper.group(FramesParser.parse(linesOf(trace)), ANDROID_META);

    assertThat(group.getPlatform()).isEqualTo("android-ndk");
    assertThat(group.getDisplayName()).contains("SIGSEGV").contains(group.getGroupId());
  }

  // ---------- Edge cases ----------

  @Test
  void shouldReturnUnknownLaneForEmptyTrace() {
    Group group = Grouper.group(new ParsedFrames(), ANDROID_META);

    assertThat(group.getPlatform()).isEqualTo("unknown");
    assertThat(group.getDisplayName()).startsWith("Error");
  }

  @Test
  void groupIdShouldBe14CharsAndStartWithExc() {
    String trace = "java.lang.RuntimeException: x\n\tat a.b.C.d(C.java:1)";

    Group group = Grouper.group(FramesParser.parse(linesOf(trace)), ANDROID_META);

    assertThat(group.getGroupId()).hasSize(14).startsWith("EXC-");
    assertThat(group.getGroupId().substring(4)).matches("[0-9A-F]{10}");
  }

  // ---------- buildSignature direct ----------

  @Test
  void buildSignatureShouldEncodeVersionPlatformExcAndFrames() {
    String sig = Grouper.buildSignature("java", List.of("NPE"), List.of("Foo#bar", "Foo#baz"));

    assertThat(sig).isEqualTo("v2|platform:java|exc:NPE|frames:Foo#bar>Foo#baz");
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

  // ---------- Helpers ----------

  private static List<String> linesOf(String trace) {
    return Arrays.asList(trace.split("\\R"));
  }
}
