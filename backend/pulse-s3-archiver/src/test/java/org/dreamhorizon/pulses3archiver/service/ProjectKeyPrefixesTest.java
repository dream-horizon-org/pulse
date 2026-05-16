package org.dreamhorizon.pulses3archiver.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectKeyPrefixesTest {

  @Test
  void shouldMapDefaultProjectSlug() {
    assertThat(ProjectKeyPrefixes.toPrefix("Default-Project")).isEqualTo("default-project");
  }

  @Test
  void shouldUseUnknownPrefixForBlankProject() {
    assertThat(ProjectKeyPrefixes.toPrefix(null)).isEqualTo(ProjectKeyPrefixes.UNKNOWN_PROJECT_PREFIX);
    assertThat(ProjectKeyPrefixes.toPrefix("")).isEqualTo(ProjectKeyPrefixes.UNKNOWN_PROJECT_PREFIX);
    assertThat(ProjectKeyPrefixes.toPrefix("   ")).isEqualTo(ProjectKeyPrefixes.UNKNOWN_PROJECT_PREFIX);
  }

  @Test
  void shouldNormalizeUnderscoreAndDots() {
    assertThat(ProjectKeyPrefixes.toPrefix("my_org.v2")).isEqualTo("my-org-v2");
  }

  @Test
  void shouldStripLeadingTrailingDashes() {
    assertThat(ProjectKeyPrefixes.toPrefix("---foo---")).isEqualTo("foo");
  }

  @Test
  void shouldDropInvalidCharsButKeepSegmentReadable() {
    assertThat(ProjectKeyPrefixes.toPrefix("Project@!#%-X")).isEqualTo("project-x");
  }
}
