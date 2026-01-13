package org.dreamhorizon.pulseserver.service.interaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.dreamhorizon.pulseserver.resources.interaction.models.InteractionConfig;
import org.dreamhorizon.pulseserver.service.interaction.models.Event;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionDetails;
import org.dreamhorizon.pulseserver.service.interaction.models.InteractionStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UploadInteractionMapperTest {

  private final UploadInteractionMapper mapper = UploadInteractionMapper.INSTANCE;

  @Nested
  class TestToInteractionConfig {

    @Test
    void shouldMapInteractionDetailsListToInteractionConfigList() {
      // Given
      List<Event> events = List.of(
          Event.builder()
              .name("TestEvent1")
              .props(List.of(
                  Event.Prop.builder()
                      .name("propName")
                      .value("propValue")
                      .build()
              ))
              .build(),
          Event.builder()
              .name("TestEvent2")
              .build()
      );

      List<Event> globalBlacklistedEvents = List.of(
          Event.builder()
              .name("BlacklistedEvent")
              .build()
      );

      InteractionDetails interaction1 = InteractionDetails.builder()
          .id(1L)
          .name("Interaction1")
          .description("First interaction")
          .status(InteractionStatus.RUNNING)
          .thresholdInMs(1000)
          .uptimeLowerLimitInMs(100)
          .uptimeMidLimitInMs(500)
          .uptimeUpperLimitInMs(1000)
          .events(events)
          .globalBlacklistedEvents(globalBlacklistedEvents)
          .createdBy("user1@example.com")
          .updatedBy("user1@example.com")
          .createdAt(Timestamp.valueOf(LocalDateTime.now()))
          .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
          .build();

      InteractionDetails interaction2 = InteractionDetails.builder()
          .id(2L)
          .name("Interaction2")
          .description("Second interaction")
          .status(InteractionStatus.STOPPED)
          .thresholdInMs(2000)
          .uptimeLowerLimitInMs(200)
          .uptimeMidLimitInMs(600)
          .uptimeUpperLimitInMs(1200)
          .events(List.of())
          .globalBlacklistedEvents(List.of())
          .createdBy("user2@example.com")
          .updatedBy("user2@example.com")
          .createdAt(Timestamp.valueOf(LocalDateTime.now()))
          .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
          .build();

      List<InteractionDetails> detailsList = List.of(interaction1, interaction2);

      // When
      List<InteractionConfig> result = mapper.toInteractionConfig(detailsList);

      // Then
      assertThat(result).hasSize(2);

      InteractionConfig config1 = result.get(0);
      assertThat(config1.getId()).isEqualTo(1L);
      assertThat(config1.getName()).isEqualTo("Interaction1");
      assertThat(config1.getDescription()).isEqualTo("First interaction");
      assertThat(config1.getThresholdInMs()).isEqualTo(1000);
      assertThat(config1.getUptimeLowerLimitInMs()).isEqualTo(100);
      assertThat(config1.getUptimeMidLimitInMs()).isEqualTo(500);
      assertThat(config1.getUptimeUpperLimitInMs()).isEqualTo(1000);
      assertThat(config1.getEvents()).hasSize(2);
      assertThat(config1.getGlobalBlacklistedEvents()).hasSize(1);

      InteractionConfig config2 = result.get(1);
      assertThat(config2.getId()).isEqualTo(2L);
      assertThat(config2.getName()).isEqualTo("Interaction2");
      assertThat(config2.getDescription()).isEqualTo("Second interaction");
      assertThat(config2.getThresholdInMs()).isEqualTo(2000);
    }

    @Test
    void shouldMapEmptyListToEmptyList() {
      // Given
      List<InteractionDetails> emptyList = List.of();

      // When
      List<InteractionConfig> result = mapper.toInteractionConfig(emptyList);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldMapSingleInteractionDetails() {
      // Given
      InteractionDetails interaction = InteractionDetails.builder()
          .id(1L)
          .name("SingleInteraction")
          .description("Single interaction test")
          .status(InteractionStatus.RUNNING)
          .thresholdInMs(500)
          .uptimeLowerLimitInMs(50)
          .uptimeMidLimitInMs(250)
          .uptimeUpperLimitInMs(500)
          .events(List.of())
          .globalBlacklistedEvents(List.of())
          .createdBy("test@example.com")
          .updatedBy("test@example.com")
          .createdAt(Timestamp.valueOf(LocalDateTime.now()))
          .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
          .build();

      List<InteractionDetails> detailsList = List.of(interaction);

      // When
      List<InteractionConfig> result = mapper.toInteractionConfig(detailsList);

      // Then
      assertThat(result).hasSize(1);
      InteractionConfig config = result.get(0);
      assertThat(config.getId()).isEqualTo(1L);
      assertThat(config.getName()).isEqualTo("SingleInteraction");
      assertThat(config.getDescription()).isEqualTo("Single interaction test");
    }

    @Test
    void shouldHandleNullFieldsInInteractionDetails() {
      // Given
      InteractionDetails interaction = InteractionDetails.builder()
          .id(1L)
          .name("InteractionWithNulls")
          .description(null)
          .status(InteractionStatus.RUNNING)
          .thresholdInMs(null)
          .uptimeLowerLimitInMs(null)
          .uptimeMidLimitInMs(null)
          .uptimeUpperLimitInMs(null)
          .events(null)
          .globalBlacklistedEvents(null)
          .build();

      List<InteractionDetails> detailsList = List.of(interaction);

      // When
      List<InteractionConfig> result = mapper.toInteractionConfig(detailsList);

      // Then
      assertThat(result).hasSize(1);
      InteractionConfig config = result.get(0);
      assertThat(config.getId()).isEqualTo(1L);
      assertThat(config.getName()).isEqualTo("InteractionWithNulls");
      assertThat(config.getDescription()).isNull();
      assertThat(config.getThresholdInMs()).isNull();
      assertThat(config.getEvents()).isNull();
      assertThat(config.getGlobalBlacklistedEvents()).isNull();
    }
  }

  @Nested
  class TestMapperInstance {

    @Test
    void shouldReturnNonNullMapperInstance() {
      // When & Then
      assertThat(UploadInteractionMapper.INSTANCE).isNotNull();
    }

    @Test
    void shouldReturnSameInstanceOnMultipleCalls() {
      // When
      UploadInteractionMapper instance1 = UploadInteractionMapper.INSTANCE;
      UploadInteractionMapper instance2 = UploadInteractionMapper.INSTANCE;

      // Then
      assertThat(instance1).isSameAs(instance2);
    }
  }
}

