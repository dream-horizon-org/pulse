package org.dreamhorizon.pulses3archiver.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectBucketNamesTest {

  @Test
  void shouldMapDefaultProjectSlug() {
    assertThat(ProjectBucketNames.toBucket("Default-Project")).isEqualTo("pulse-otel-default-project");
  }

  @Test
  void shouldUseUnknownBucketForBlankProject() {
    assertThat(ProjectBucketNames.toBucket(null)).isEqualTo(ProjectBucketNames.UNKNOWN_PROJECT_BUCKET);
    assertThat(ProjectBucketNames.toBucket("")).isEqualTo(ProjectBucketNames.UNKNOWN_PROJECT_BUCKET);
    assertThat(ProjectBucketNames.toBucket("   ")).isEqualTo(ProjectBucketNames.UNKNOWN_PROJECT_BUCKET);
  }

  @Test
  void shouldNormalizeUnderscoreAndDots() {
    assertThat(ProjectBucketNames.toBucket("my_org.v2")).isEqualTo("pulse-otel-my-org-v2");
  }
}
