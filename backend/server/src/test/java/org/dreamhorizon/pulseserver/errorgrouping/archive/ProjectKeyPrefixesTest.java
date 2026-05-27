package org.dreamhorizon.pulseserver.errorgrouping.archive;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProjectKeyPrefixesTest {

  @Nested
  class ToPrefix {

    @Test
    void shouldLowercaseProjectId() {
      assertThat(ProjectKeyPrefixes.toPrefix("Default-Project")).isEqualTo("default-project");
    }

    @Test
    void shouldUseUnknownPrefixForNullBlankOrWhitespace() {
      assertThat(ProjectKeyPrefixes.toPrefix(null)).isEqualTo(ProjectKeyPrefixes.UNKNOWN_PROJECT_PREFIX);
      assertThat(ProjectKeyPrefixes.toPrefix("")).isEqualTo(ProjectKeyPrefixes.UNKNOWN_PROJECT_PREFIX);
      assertThat(ProjectKeyPrefixes.toPrefix("   ")).isEqualTo(ProjectKeyPrefixes.UNKNOWN_PROJECT_PREFIX);
    }

    @Test
    void shouldNormalizeUnderscoresAndDots() {
      assertThat(ProjectKeyPrefixes.toPrefix("my_org.v2")).isEqualTo("my-org-v2");
    }

    @Test
    void shouldStripLeadingAndTrailingDashes() {
      assertThat(ProjectKeyPrefixes.toPrefix("---foo---")).isEqualTo("foo");
    }

    @Test
    void shouldDropInvalidCharsButKeepReadableSegments() {
      assertThat(ProjectKeyPrefixes.toPrefix("Project@!#%-X")).isEqualTo("project-x");
    }

    @Test
    void shouldTruncateToMaxPrefixLength() {
      String longId = "a".repeat(200);
      String prefix = ProjectKeyPrefixes.toPrefix(longId);
      assertThat(prefix).hasSize(128);
      assertThat(prefix).isEqualTo("a".repeat(128));
    }

    @Test
    void shouldCollapseConsecutiveDashes() {
      assertThat(ProjectKeyPrefixes.toPrefix("foo---bar")).isEqualTo("foo-bar");
    }
  }
}
