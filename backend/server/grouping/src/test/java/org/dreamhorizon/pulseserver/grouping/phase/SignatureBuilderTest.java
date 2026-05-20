package org.dreamhorizon.pulseserver.grouping.phase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SignatureBuilderTest {

  @Test
  void shouldBuildSignatureWithAllFourSegments() {
    String sig = SignatureBuilder.build("java", List.of("NPE"), List.of("Foo#bar"), "");

    assertThat(sig).isEqualTo("v2|platform:java|exc:NPE|frames:Foo#bar|msg:");
  }

  @Test
  void shouldIncludeMaskedMessageInLastSegment() {
    String sig = SignatureBuilder.build("java", List.of("NPE"), List.of("Foo#bar"), "boom");

    assertThat(sig).isEqualTo("v2|platform:java|exc:NPE|frames:Foo#bar|msg:boom");
  }

  @Test
  void shouldEmitEmptySegmentsWhenExceptionTypesEmpty() {
    String sig = SignatureBuilder.build("java", List.of(), List.of("Foo#bar"), "");

    assertThat(sig).isEqualTo("v2|platform:java|exc:|frames:Foo#bar|msg:");
  }

  @Test
  void shouldEmitEmptySegmentWhenFrameTokensEmpty() {
    String sig = SignatureBuilder.build("java", List.of("NPE"), List.of(), "");

    assertThat(sig).isEqualTo("v2|platform:java|exc:NPE|frames:|msg:");
  }

  @Test
  void shouldHandleAllNullsGracefully() {
    String sig = SignatureBuilder.build(null, null, null, null);

    assertThat(sig).isEqualTo("v2|platform:|exc:|frames:|msg:");
  }

  @Test
  void shouldJoinExceptionTypesByGreaterThan() {
    String sig = SignatureBuilder.build("java", List.of("A", "B", "C"), List.of("f1"), "");

    assertThat(sig).contains("|exc:A>B>C|");
  }

  @Test
  void shouldJoinFrameTokensByGreaterThan() {
    String sig = SignatureBuilder.build("java", List.of("NPE"), List.of("f1", "f2"), "");

    assertThat(sig).contains("|frames:f1>f2|");
  }

  @Test
  void sigVersionConstantShouldBeV2() {
    assertThat(SignatureBuilder.SIG_VERSION).isEqualTo("v2");
  }

  @Test
  void shouldStartWithSigVersion() {
    String sig = SignatureBuilder.build("js", List.of("TypeError"), List.of("/src/foo.js#bar"), "boom");

    assertThat(sig).startsWith("v2|");
  }

  @Test
  void shouldSkipNullEntriesInLists() {
    String sig = SignatureBuilder.build("java", Arrays.asList("A", null, "B"),
        Arrays.asList(null, "f1"), "");

    // null entries collapse to empty strings, separator still emitted
    assertThat(sig).isEqualTo("v2|platform:java|exc:A>>B|frames:>f1|msg:");
  }
}
