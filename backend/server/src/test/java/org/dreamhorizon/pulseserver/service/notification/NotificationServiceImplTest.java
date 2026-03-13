package org.dreamhorizon.pulseserver.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.sqlclient.Row;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.dreamhorizon.pulseserver.constant.NotificationConstants;
import org.dreamhorizon.pulseserver.dao.notification.ChannelEventMappingDao;
import org.dreamhorizon.pulseserver.dao.notification.EmailSuppressionDao;
import org.dreamhorizon.pulseserver.resources.notification.models.ChannelEventMappingDto;
import org.dreamhorizon.pulseserver.resources.notification.models.CreateMappingRequestDto;
import org.dreamhorizon.pulseserver.resources.notification.models.RecipientsDto;
import org.dreamhorizon.pulseserver.resources.notification.models.SendNotificationRequestDto;
import org.dreamhorizon.pulseserver.resources.notification.models.UpdateMappingRequestDto;
import org.dreamhorizon.pulseserver.dao.notification.NotificationChannelDao;
import org.dreamhorizon.pulseserver.dao.notification.NotificationLogDao;
import org.dreamhorizon.pulseserver.dao.notification.NotificationTemplateDao;
import org.dreamhorizon.pulseserver.resources.notification.models.CreateChannelRequestDto;
import org.dreamhorizon.pulseserver.resources.notification.models.CreateTemplateRequestDto;
import org.dreamhorizon.pulseserver.resources.notification.models.UpdateChannelRequestDto;
import org.dreamhorizon.pulseserver.resources.notification.models.UpdateTemplateRequestDto;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelEventMapping;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.EmailChannelConfig;
import org.dreamhorizon.pulseserver.service.notification.models.EmailTemplateBody;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationChannel;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationLog;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationResult;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationStatus;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationTemplate;
import org.dreamhorizon.pulseserver.service.notification.models.SlackChannelConfig;
import org.dreamhorizon.pulseserver.service.notification.models.SlackTemplateBody;
import org.dreamhorizon.pulseserver.service.notification.models.TeamsChannelConfig;
import org.dreamhorizon.pulseserver.service.notification.models.TeamsTemplateBody;
import org.dreamhorizon.pulseserver.service.notification.provider.NotificationProvider;
import org.dreamhorizon.pulseserver.service.notification.provider.NotificationProviderFactory;
import org.dreamhorizon.pulseserver.service.notification.queue.SqsNotificationQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceImplTest {

  private static final Long CHANNEL_ID = 1L;
  private static final Long TEMPLATE_ID = 1L;
  private static final Long LOG_ID = 100L;
  private static final String PROJECT_ID = "proj-123";

  @Mock NotificationChannelDao channelDao;
  @Mock NotificationTemplateDao templateDao;
  @Mock NotificationLogDao logDao;
  @Mock EmailSuppressionDao suppressionDao;
  @Mock ChannelEventMappingDao mappingDao;
  @Mock NotificationProviderFactory providerFactory;
  @Mock SqsNotificationQueue notificationQueue;

  ObjectMapper objectMapper = new ObjectMapper();

  NotificationServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new NotificationServiceImpl(
        channelDao, templateDao, logDao, suppressionDao,
        mappingDao, providerFactory, notificationQueue, objectMapper);
  }

  private NotificationChannel emailChannel() {
    return NotificationChannel.builder()
        .id(CHANNEL_ID)
        .projectId(null)
        .channelType(ChannelType.EMAIL)
        .name("Test Email Channel")
        .config(EmailChannelConfig.builder().fromAddress("noreply@test.com").build())
        .isActive(true)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }

  private NotificationChannel slackChannel() {
    return NotificationChannel.builder()
        .id(CHANNEL_ID)
        .projectId(PROJECT_ID)
        .channelType(ChannelType.SLACK)
        .name("Test Slack Channel")
        .config(SlackChannelConfig.builder().accessToken("xoxb-token").build())
        .isActive(true)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }

  private NotificationTemplate emailTemplate() {
    return NotificationTemplate.builder()
        .id(TEMPLATE_ID)
        .eventName("alert")
        .channelType(ChannelType.EMAIL)
        .version(1)
        .body(EmailTemplateBody.builder().subject("Alert").html("<p>Hello</p>").build())
        .isActive(true)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }

  private NotificationTemplate slackTemplate() {
    return NotificationTemplate.builder()
        .id(TEMPLATE_ID)
        .eventName("alert")
        .channelType(ChannelType.SLACK)
        .version(1)
        .body(SlackTemplateBody.builder().text("Hello").build())
        .isActive(true)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }

  private NotificationLog notificationLog() {
    return NotificationLog.builder()
        .id(LOG_ID)
        .projectId(PROJECT_ID)
        .idempotencyKey("key")
        .channelType(ChannelType.EMAIL)
        .channelId(CHANNEL_ID)
        .templateId(TEMPLATE_ID)
        .recipient("user@test.com")
        .subject("Subject")
        .status(NotificationStatus.PROCESSING)
        .attemptCount(1)
        .createdAt(Instant.now())
        .build();
  }

  @Nested
  class ChannelOperations {

    @Test
    void shouldGetChannelsWithProjectAndType() {
      NotificationChannel ch = emailChannel();
      when(channelDao.getChannelsAccessibleByProjectAndType(eq(PROJECT_ID), eq(ChannelType.EMAIL)))
          .thenReturn(Single.just(List.of(ch)));

      var result = service.getChannels(PROJECT_ID, ChannelType.EMAIL).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getChannelType()).isEqualTo(ChannelType.EMAIL);
      assertThat(result.get(0).getName()).isEqualTo("Test Email Channel");
    }

    @Test
    void shouldGetChannelsWithProjectOnly() {
      NotificationChannel ch = emailChannel();
      when(channelDao.getChannelsAccessibleByProject(eq(PROJECT_ID)))
          .thenReturn(Single.just(List.of(ch)));

      var result = service.getChannels(PROJECT_ID, null).blockingGet();

      assertThat(result).hasSize(1);
    }

    @Test
    void shouldGetSharedChannels() {
      NotificationChannel ch = emailChannel();
      when(channelDao.getSharedChannels()).thenReturn(Single.just(List.of(ch)));

      var result = service.getChannels(null, null).blockingGet();

      assertThat(result).hasSize(1);
    }

    @Test
    void shouldGetChannelById() {
      NotificationChannel ch = slackChannel();
      when(channelDao.getChannelById(eq(CHANNEL_ID))).thenReturn(Maybe.just(ch));

      var result = service.getChannel(CHANNEL_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(CHANNEL_ID);
    }

    @Test
    void shouldReturnEmptyWhenChannelNotFound() {
      when(channelDao.getChannelById(eq(999L))).thenReturn(Maybe.empty());

      service.getChannel(999L).test().assertNoValues().assertComplete();
    }

    @Test
    void shouldCreateChannelSuccessfully() {
      EmailChannelConfig config = EmailChannelConfig.builder()
          .fromAddress("noreply@test.com").build();
      CreateChannelRequestDto request = CreateChannelRequestDto.builder()
          .channelType(ChannelType.EMAIL)
          .name("New Channel")
          .config(config)
          .build();

      when(channelDao.getActiveChannelByProjectAndType(eq(""), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.empty());
      NotificationChannel saved = emailChannel();
      when(channelDao.createChannel(any())).thenReturn(Single.just(CHANNEL_ID));
      when(channelDao.getChannelById(eq(CHANNEL_ID))).thenReturn(Maybe.just(saved));

      var result = service.createChannel(request).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getChannelType()).isEqualTo(ChannelType.EMAIL);
    }

    @Test
    void shouldThrowWhenCreatingDuplicateChannelType() {
      EmailChannelConfig config = EmailChannelConfig.builder()
          .fromAddress("x").build();
      CreateChannelRequestDto request = CreateChannelRequestDto.builder()
          .channelType(ChannelType.EMAIL)
          .name("New Channel")
          .config(config)
          .build();

      when(channelDao.getActiveChannelByProjectAndType(eq(""), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(emailChannel()));

      service.createChannel(request)
          .test()
          .assertError(e -> e.getMessage().contains("already exists"));
    }

    @Test
    void shouldUpdateChannelSuccessfully() {
      UpdateChannelRequestDto request = UpdateChannelRequestDto.builder()
          .name("Updated Name")
          .build();

      NotificationChannel existing = emailChannel();
      NotificationChannel updated = emailChannel();
      updated.setName("Updated Name");

      when(channelDao.getChannelById(eq(CHANNEL_ID)))
          .thenReturn(Maybe.just(existing))
          .thenReturn(Maybe.just(updated));
      when(channelDao.updateChannel(eq(CHANNEL_ID), any())).thenReturn(Single.just(1));

      var result = service.updateChannel(CHANNEL_ID, request).blockingGet();

      assertThat(result.getName()).isEqualTo("Updated Name");
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentChannel() {
      UpdateChannelRequestDto request = UpdateChannelRequestDto.builder().name("X").build();
      when(channelDao.getChannelById(eq(999L))).thenReturn(Maybe.empty());

      service.updateChannel(999L, request)
          .test()
          .assertError(e -> e.getMessage().contains("Channel not found"));
    }

    @Test
    void shouldUpdateChannelWithConfigAndIsActive() {
      EmailChannelConfig newConfig = EmailChannelConfig.builder()
          .fromAddress("new@test.com").build();
      UpdateChannelRequestDto request = UpdateChannelRequestDto.builder()
          .name("Updated")
          .config(newConfig)
          .isActive(false)
          .build();

      NotificationChannel existing = emailChannel();
      NotificationChannel updated = emailChannel();
      updated.setName("Updated");
      updated.setIsActive(false);

      when(channelDao.getChannelById(eq(CHANNEL_ID)))
          .thenReturn(Maybe.just(existing))
          .thenReturn(Maybe.just(updated));
      when(channelDao.updateChannel(eq(CHANNEL_ID), any())).thenReturn(Single.just(1));

      var result = service.updateChannel(CHANNEL_ID, request).blockingGet();

      assertThat(result.getName()).isEqualTo("Updated");
      assertThat(result.getIsActive()).isFalse();
    }

    @Test
    void shouldDeleteChannel() {
      when(channelDao.deleteChannel(eq(CHANNEL_ID))).thenReturn(Single.just(1));

      var result = service.deleteChannel(CHANNEL_ID).blockingGet();

      assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenDeleteAffectsNoRows() {
      when(channelDao.deleteChannel(eq(CHANNEL_ID))).thenReturn(Single.just(0));

      var result = service.deleteChannel(CHANNEL_ID).blockingGet();

      assertThat(result).isFalse();
    }
  }

  @Nested
  class TemplateOperations {

    @Test
    void shouldGetTemplatesByChannelType() {
      NotificationTemplate t = emailTemplate();
      when(templateDao.getTemplatesByChannelType(eq(ChannelType.EMAIL)))
          .thenReturn(Single.just(List.of(t)));

      var result = service.getTemplates(ChannelType.EMAIL).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getEventName()).isEqualTo("alert");
    }

    @Test
    void shouldGetAllTemplatesWhenChannelTypeNull() {
      NotificationTemplate t = emailTemplate();
      when(templateDao.getAllTemplates()).thenReturn(Single.just(List.of(t)));

      var result = service.getTemplates(null).blockingGet();

      assertThat(result).hasSize(1);
    }

    @Test
    void shouldGetTemplateById() {
      NotificationTemplate t = emailTemplate();
      when(templateDao.getTemplateById(eq(TEMPLATE_ID))).thenReturn(Maybe.just(t));

      var result = service.getTemplate(TEMPLATE_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(TEMPLATE_ID);
    }

    @Test
    void shouldCreateTemplateSuccessfully() {
      EmailTemplateBody body = EmailTemplateBody.builder()
          .subject("Hello").html("<p>Hi</p>").build();
      CreateTemplateRequestDto request = CreateTemplateRequestDto.builder()
          .eventName("alert")
          .channelType(ChannelType.EMAIL)
          .body(body)
          .build();

      when(templateDao.getLatestVersion(eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Single.just(0));
      when(templateDao.createTemplate(any())).thenReturn(Single.just(TEMPLATE_ID));
      when(templateDao.getTemplateById(eq(TEMPLATE_ID))).thenReturn(Maybe.just(emailTemplate()));

      var result = service.createTemplate(request).blockingGet();

      assertThat(result).isNotNull();
    }

    @Test
    void shouldUpdateTemplateSuccessfully() {
      UpdateTemplateRequestDto request = UpdateTemplateRequestDto.builder()
          .eventName("updated")
          .build();

      NotificationTemplate existing = emailTemplate();
      NotificationTemplate updated = emailTemplate();
      updated.setEventName("updated");

      when(templateDao.getTemplateById(eq(TEMPLATE_ID)))
          .thenReturn(Maybe.just(existing))
          .thenReturn(Maybe.just(updated));
      when(templateDao.updateTemplate(eq(TEMPLATE_ID), any())).thenReturn(Single.just(1));

      var result = service.updateTemplate(TEMPLATE_ID, request).blockingGet();

      assertThat(result.getEventName()).isEqualTo("updated");
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentTemplate() {
      UpdateTemplateRequestDto request = UpdateTemplateRequestDto.builder()
          .eventName("x").build();
      when(templateDao.getTemplateById(eq(999L))).thenReturn(Maybe.empty());

      service.updateTemplate(999L, request)
          .test()
          .assertError(e -> e.getMessage().contains("Template not found"));
    }

    @Test
    void shouldUpdateTemplateWithBodyAndIsActive() {
      EmailTemplateBody newBody = EmailTemplateBody.builder()
          .subject("New Subject").html("<p>Updated</p>").build();
      UpdateTemplateRequestDto request = UpdateTemplateRequestDto.builder()
          .body(newBody)
          .isActive(false)
          .build();

      NotificationTemplate existing = emailTemplate();
      NotificationTemplate updated = emailTemplate();
      updated.setBody(newBody);
      updated.setIsActive(false);

      when(templateDao.getTemplateById(eq(TEMPLATE_ID)))
          .thenReturn(Maybe.just(existing))
          .thenReturn(Maybe.just(updated));
      when(templateDao.updateTemplate(eq(TEMPLATE_ID), any())).thenReturn(Single.just(1));

      var result = service.updateTemplate(TEMPLATE_ID, request).blockingGet();

      assertThat(result.getIsActive()).isFalse();
    }

    @Test
    void shouldDeleteTemplate() {
      when(templateDao.deleteTemplate(eq(TEMPLATE_ID))).thenReturn(Single.just(1));

      var result = service.deleteTemplate(TEMPLATE_ID).blockingGet();

      assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenDeleteTemplateAffectsNoRows() {
      when(templateDao.deleteTemplate(eq(TEMPLATE_ID))).thenReturn(Single.just(0));

      var result = service.deleteTemplate(TEMPLATE_ID).blockingGet();

      assertThat(result).isFalse();
    }
  }

  @Nested
  class PlatformMappingOperations {

    @Test
    void shouldCreateDefaultPlatformMappingsForAllEvents() {
      NotificationChannel platform = emailChannel();
      platform.setId(NotificationConstants.Platform.DEFAULT_CHANNEL_ID);
      platform.setProjectId("default-project");

      NotificationTemplate template = emailTemplate();

      when(channelDao.getChannelById(eq(NotificationConstants.Platform.DEFAULT_CHANNEL_ID)))
          .thenReturn(Maybe.just(platform));
      when(templateDao.getTemplateByEventNameAndChannel(any(), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(template));

      long[] idCounter = {1};
      when(mappingDao.createMapping(any(ChannelEventMapping.class)))
          .thenAnswer(inv -> Single.just(idCounter[0]++));
      when(mappingDao.getMappingById(anyLong()))
          .thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Maybe.just(ChannelEventMapping.builder()
                .id(id)
                .projectId(PROJECT_ID)
                .channelId(NotificationConstants.Platform.DEFAULT_CHANNEL_ID)
                .eventName("event")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
          });

      List<ChannelEventMappingDto> result =
          service.createDefaultPlatformMappings(PROJECT_ID).blockingGet();

      assertThat(result).hasSize(6);
      verify(mappingDao, org.mockito.Mockito.times(6)).createMapping(any(ChannelEventMapping.class));
    }
  }

  @Nested
  class LogOperations {

    @Test
    void shouldGetLogsWithAllFields() {
      NotificationLog log = notificationLog();
      log.setSentAt(Instant.now());
      log.setLatencyMs(50);
      log.setErrorMessage(null);
      log.setExternalId("ext-123");
      when(logDao.getLogsByProject(eq(PROJECT_ID), eq(50), eq(0)))
          .thenReturn(Single.just(List.of(log)));

      var result = service.getLogs(PROJECT_ID, 50, 0).blockingGet();

      assertThat(result.getLogs()).hasSize(1);
      assertThat(result.getLogs().get(0).getRecipient()).isEqualTo("user@test.com");
      assertThat(result.getLogs().get(0).getLatencyMs()).isEqualTo(50);
      assertThat(result.getLogs().get(0).getStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void shouldGetLogs() {
      NotificationLog log = notificationLog();
      when(logDao.getLogsByProject(eq(PROJECT_ID), eq(50), eq(0)))
          .thenReturn(Single.just(List.of(log)));

      var result = service.getLogs(PROJECT_ID, 50, 0).blockingGet();

      assertThat(result.getLogs()).hasSize(1);
      assertThat(result.getLogs().get(0).getRecipient()).isEqualTo("user@test.com");
    }

    @Test
    void shouldGetLogsByIdempotencyKey() {
      NotificationLog log = notificationLog();
      when(logDao.getLogsByIdempotencyKey(eq(PROJECT_ID), eq("idem-key")))
          .thenReturn(Single.just(List.of(log)));

      var result = service.getLogsByIdempotencyKey(PROJECT_ID, "idem-key").blockingGet();

      assertThat(result.getLogs()).hasSize(1);
      assertThat(result.getLogs().get(0).getIdempotencyKey()).isEqualTo("key");
    }
  }

  @Nested
  class MappingOperations {

    @Test
    void shouldGetMappingsEnrichedWithChannelInfo() {
      ChannelEventMapping mapping = ChannelEventMapping.builder()
          .id(10L)
          .projectId(PROJECT_ID)
          .channelId(CHANNEL_ID)
          .eventName("alert")
          .recipient("user@test.com")
          .isActive(true)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      when(mappingDao.getMappingsByProject(eq(PROJECT_ID)))
          .thenReturn(Single.just(List.of(mapping)));
      when(channelDao.getChannelById(eq(CHANNEL_ID)))
          .thenReturn(Maybe.just(emailChannel()));

      var result = service.getMappings(PROJECT_ID).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getChannelType()).isEqualTo(ChannelType.EMAIL);
      assertThat(result.get(0).getChannelName()).isEqualTo("Test Email Channel");
      assertThat(result.get(0).getRecipient()).isEqualTo("user@test.com");
    }

    @Test
    void shouldGetMappingsWithoutChannelWhenChannelNotFound() {
      ChannelEventMapping mapping = ChannelEventMapping.builder()
          .id(10L)
          .projectId(PROJECT_ID)
          .channelId(999L)
          .eventName("alert")
          .isActive(true)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      when(mappingDao.getMappingsByProject(eq(PROJECT_ID)))
          .thenReturn(Single.just(List.of(mapping)));
      when(channelDao.getChannelById(eq(999L)))
          .thenReturn(Maybe.empty());

      var result = service.getMappings(PROJECT_ID).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getChannelType()).isNull();
    }

    @Test
    void shouldCreateMappingSuccessfully() {
      CreateMappingRequestDto request = CreateMappingRequestDto.builder()
          .channelId(CHANNEL_ID)
          .eventName("alert")
          .recipient("user@test.com")
          .build();

      when(channelDao.getChannelById(eq(CHANNEL_ID)))
          .thenReturn(Maybe.just(emailChannel()));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(emailTemplate()));
      when(mappingDao.createMapping(any(ChannelEventMapping.class)))
          .thenReturn(Single.just(10L));
      when(mappingDao.getMappingById(eq(10L)))
          .thenReturn(Maybe.just(ChannelEventMapping.builder()
              .id(10L)
              .projectId(PROJECT_ID)
              .channelId(CHANNEL_ID)
              .eventName("alert")
              .recipient("user@test.com")
              .isActive(true)
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build()));

      var result = service.createMapping(PROJECT_ID, request).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(10L);
      assertThat(result.getEventName()).isEqualTo("alert");
    }

    @Test
    void shouldThrowWhenCreatingMappingWithNoTemplate() {
      CreateMappingRequestDto request = CreateMappingRequestDto.builder()
          .channelId(CHANNEL_ID)
          .eventName("unknown_event")
          .build();

      when(channelDao.getChannelById(eq(CHANNEL_ID)))
          .thenReturn(Maybe.just(emailChannel()));
      when(templateDao.getTemplateByEventNameAndChannel(eq("unknown_event"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.empty());

      service.createMapping(PROJECT_ID, request)
          .test()
          .assertError(e -> e.getMessage().contains("No template found"));
    }

    @Test
    void shouldThrowWhenCreatingMappingWithNonExistentChannel() {
      CreateMappingRequestDto request = CreateMappingRequestDto.builder()
          .channelId(999L)
          .eventName("alert")
          .build();

      when(channelDao.getChannelById(eq(999L)))
          .thenReturn(Maybe.empty());

      service.createMapping(PROJECT_ID, request)
          .test()
          .assertError(e -> e.getMessage().contains("Channel not found"));
    }

    @Test
    void shouldUpdateMappingSuccessfully() {
      UpdateMappingRequestDto request = UpdateMappingRequestDto.builder()
          .recipient("new@test.com")
          .isActive(false)
          .build();

      ChannelEventMapping existing = ChannelEventMapping.builder()
          .id(10L)
          .projectId(PROJECT_ID)
          .channelId(CHANNEL_ID)
          .eventName("alert")
          .recipient("old@test.com")
          .isActive(true)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      ChannelEventMapping updated = ChannelEventMapping.builder()
          .id(10L)
          .projectId(PROJECT_ID)
          .channelId(CHANNEL_ID)
          .eventName("alert")
          .recipient("new@test.com")
          .isActive(false)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      when(mappingDao.getMappingById(eq(10L)))
          .thenReturn(Maybe.just(existing))
          .thenReturn(Maybe.just(updated));
      when(mappingDao.updateMapping(eq(10L), any()))
          .thenReturn(Single.just(1));
      when(channelDao.getChannelById(eq(CHANNEL_ID)))
          .thenReturn(Maybe.just(emailChannel()));

      var result = service.updateMapping(10L, request).blockingGet();

      assertThat(result.getRecipient()).isEqualTo("new@test.com");
      assertThat(result.getIsActive()).isFalse();
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentMapping() {
      UpdateMappingRequestDto request = UpdateMappingRequestDto.builder()
          .recipient("x").build();
      when(mappingDao.getMappingById(eq(999L))).thenReturn(Maybe.empty());

      service.updateMapping(999L, request)
          .test()
          .assertError(e -> e.getMessage().contains("Mapping not found"));
    }

    @Test
    void shouldDeleteMapping() {
      when(mappingDao.deleteMapping(eq(10L))).thenReturn(Single.just(1));

      var result = service.deleteMapping(10L).blockingGet();

      assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenDeleteMappingAffectsNoRows() {
      when(mappingDao.deleteMapping(eq(10L))).thenReturn(Single.just(0));

      var result = service.deleteMapping(10L).blockingGet();

      assertThat(result).isFalse();
    }

    @Test
    void shouldPreserveExistingFieldsWhenUpdateMappingHasNulls() {
      UpdateMappingRequestDto request = UpdateMappingRequestDto.builder()
          .recipient("new@test.com")
          .build();

      ChannelEventMapping existing = ChannelEventMapping.builder()
          .id(10L)
          .projectId(PROJECT_ID)
          .channelId(CHANNEL_ID)
          .eventName("alert")
          .recipient("old@test.com")
          .recipientName("Old Name")
          .isActive(true)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      ChannelEventMapping updated = ChannelEventMapping.builder()
          .id(10L)
          .projectId(PROJECT_ID)
          .channelId(CHANNEL_ID)
          .eventName("alert")
          .recipient("new@test.com")
          .recipientName("Old Name")
          .isActive(true)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      when(mappingDao.getMappingById(eq(10L)))
          .thenReturn(Maybe.just(existing))
          .thenReturn(Maybe.just(updated));
      when(mappingDao.updateMapping(eq(10L), any()))
          .thenReturn(Single.just(1));
      when(channelDao.getChannelById(eq(CHANNEL_ID)))
          .thenReturn(Maybe.just(emailChannel()));

      var result = service.updateMapping(10L, request).blockingGet();

      assertThat(result.getRecipient()).isEqualTo("new@test.com");
      assertThat(result.getIsActive()).isTrue();
    }
  }

  @Nested
  class ChannelValidation {

    @Test
    void shouldThrowWhenSlackChannelMissingProjectId() {
      CreateChannelRequestDto request = CreateChannelRequestDto.builder()
          .channelType(ChannelType.SLACK)
          .name("Slack Channel")
          .config(SlackChannelConfig.builder().accessToken("token").build())
          .build();

      assertThatThrownBy(() -> service.createChannel(request).blockingGet())
          .hasMessageContaining("SLACK channels require a projectId");
    }

    @Test
    void shouldThrowWhenTeamsChannelMissingProjectId() {
      CreateChannelRequestDto request = CreateChannelRequestDto.builder()
          .channelType(ChannelType.TEAMS)
          .name("Teams Channel")
          .config(TeamsChannelConfig.builder().workflowUrl("https://teams.example.com").build())
          .build();

      assertThatThrownBy(() -> service.createChannel(request).blockingGet())
          .hasMessageContaining("TEAMS channels require a projectId");
    }

    @Test
    void shouldThrowWhenEmailChannelHasProjectId() {
      CreateChannelRequestDto request = CreateChannelRequestDto.builder()
          .channelType(ChannelType.EMAIL)
          .projectId(PROJECT_ID)
          .name("Email Channel")
          .config(EmailChannelConfig.builder().fromAddress("a@b.com").build())
          .build();

      assertThatThrownBy(() -> service.createChannel(request).blockingGet())
          .hasMessageContaining("EMAIL channels must not have a projectId");
    }

    @Test
    void shouldCreateSlackChannelWithProjectId() {
      CreateChannelRequestDto request = CreateChannelRequestDto.builder()
          .channelType(ChannelType.SLACK)
          .projectId(PROJECT_ID)
          .name("Slack Channel")
          .config(SlackChannelConfig.builder().accessToken("token").build())
          .build();

      when(channelDao.getActiveChannelByProjectAndType(eq(PROJECT_ID), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.empty());
      when(channelDao.createChannel(any())).thenReturn(Single.just(CHANNEL_ID));
      when(channelDao.getChannelById(eq(CHANNEL_ID))).thenReturn(Maybe.just(slackChannel()));

      var result = service.createChannel(request).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getChannelType()).isEqualTo(ChannelType.SLACK);
    }
  }

  @Nested
  class ChannelCreationWithMappings {

    @Test
    void shouldCreateChannelAndAutoMappingsWhenEventNamesProvided() {
      CreateChannelRequestDto request = CreateChannelRequestDto.builder()
          .channelType(ChannelType.SLACK)
          .projectId(PROJECT_ID)
          .name("Slack with events")
          .config(SlackChannelConfig.builder().accessToken("tok").build())
          .eventNames(List.of("alert", "deploy"))
          .build();

      when(channelDao.getActiveChannelByProjectAndType(eq(PROJECT_ID), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.empty());
      when(channelDao.createChannel(any())).thenReturn(Single.just(CHANNEL_ID));
      when(channelDao.getChannelById(eq(CHANNEL_ID))).thenReturn(Maybe.just(slackChannel()));
      when(mappingDao.createMapping(any(ChannelEventMapping.class)))
          .thenReturn(Single.just(1L));

      var result = service.createChannel(request).blockingGet();

      assertThat(result).isNotNull();
      verify(mappingDao, org.mockito.Mockito.times(2)).createMapping(any(ChannelEventMapping.class));
    }

    @Test
    void shouldCreateChannelWithoutMappingsWhenNoEventNames() {
      CreateChannelRequestDto request = CreateChannelRequestDto.builder()
          .channelType(ChannelType.SLACK)
          .projectId(PROJECT_ID)
          .name("Slack no events")
          .config(SlackChannelConfig.builder().accessToken("tok").build())
          .build();

      when(channelDao.getActiveChannelByProjectAndType(eq(PROJECT_ID), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.empty());
      when(channelDao.createChannel(any())).thenReturn(Single.just(CHANNEL_ID));
      when(channelDao.getChannelById(eq(CHANNEL_ID))).thenReturn(Maybe.just(slackChannel()));

      var result = service.createChannel(request).blockingGet();

      assertThat(result).isNotNull();
      verify(mappingDao, never()).createMapping(any());
    }
  }

  @Nested
  class SendNotificationByMappingId {

    @Mock Row mappingRow;
    @Mock NotificationProvider emailProvider;

    private void stubMappingRow(String projectId, String channelType, String recipient) {
      when(mappingRow.getString("project_id")).thenReturn(projectId);
      when(mappingRow.getLong("channel_id")).thenReturn(CHANNEL_ID);
      when(mappingRow.getString("channel_type")).thenReturn(channelType);
      when(mappingRow.getString("event_name")).thenReturn("alert");
      when(mappingRow.getString("recipient")).thenReturn(recipient);
      when(mappingRow.getValue("config")).thenReturn(
          new JsonObject().put("type", "EMAIL").put("fromAddress", "noreply@test.com"));
    }

    @Test
    void shouldSendByMappingIdSynchronously() {
      stubMappingRow(PROJECT_ID, "EMAIL", "user@test.com");

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(emailTemplate()));
      when(providerFactory.getProvider(eq(ChannelType.EMAIL)))
          .thenReturn(Optional.of(emailProvider));
      when(suppressionDao.isEmailSuppressed(eq(PROJECT_ID), eq("user@test.com")))
          .thenReturn(Single.just(false));
      when(logDao.insertLogIfNotExists(any()))
          .thenReturn(Single.just(1L));
      when(emailProvider.send(any(), any()))
          .thenReturn(Single.just(NotificationResult.builder()
              .success(true)
              .externalId("ext-1")
              .latencyMs(100)
              .build()));
      when(logDao.updateLogStatus(anyLong(), any(), anyInt(), any(), any(), any(), any(), anyInt()))
          .thenReturn(Single.just(1));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .build();

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getTotalRecipients()).isEqualTo(1);
      assertThat(result.getQueued()).isEqualTo(1);
      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void shouldErrorWhenMappingNotFound() {
      when(mappingDao.getActiveMappingWithChannelById(eq(999L)))
          .thenReturn(Maybe.empty());

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(999L)
          .build();

      service.sendNotification(PROJECT_ID, request)
          .test()
          .assertError(e -> e.getMessage().contains("Mapping not found"));
    }

    @Test
    void shouldErrorWhenMappingBelongsToDifferentProject() {
      stubMappingRow("other-project", "EMAIL", "user@test.com");
      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .build();

      service.sendNotification(PROJECT_ID, request)
          .test()
          .assertError(e -> e.getMessage().contains("does not belong to project"));
    }

    @Test
    void shouldErrorWhenNoRecipientsResolved() {
      stubMappingRow(PROJECT_ID, "EMAIL", null);
      when(mappingRow.getString("recipient")).thenReturn(null);

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .build();

      service.sendNotification(PROJECT_ID, request)
          .test()
          .assertError(e -> e.getMessage().contains("No recipients found"));
    }

    @Test
    void shouldErrorWhenNoTemplateFoundForMapping() {
      stubMappingRow(PROJECT_ID, "EMAIL", "user@test.com");
      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.empty());

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .build();

      service.sendNotification(PROJECT_ID, request)
          .test()
          .assertError(e -> e.getMessage().contains("No template found"));
    }

    @Test
    void shouldSkipDuplicateNotification() {
      stubMappingRow(PROJECT_ID, "EMAIL", "user@test.com");

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(emailTemplate()));
      when(providerFactory.getProvider(eq(ChannelType.EMAIL)))
          .thenReturn(Optional.of(emailProvider));
      when(suppressionDao.isEmailSuppressed(eq(PROJECT_ID), eq("user@test.com")))
          .thenReturn(Single.just(false));
      when(logDao.insertLogIfNotExists(any()))
          .thenReturn(Single.just(0L));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .idempotencyKey("dup-key")
          .build();

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.SKIPPED);
      assertThat(result.getResults().get(0).getErrorMessage()).contains("idempotency");
    }

    @Test
    void shouldSkipSuppressedEmail() {
      stubMappingRow(PROJECT_ID, "EMAIL", "suppressed@test.com");

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(emailTemplate()));
      when(providerFactory.getProvider(eq(ChannelType.EMAIL)))
          .thenReturn(Optional.of(emailProvider));
      when(suppressionDao.isEmailSuppressed(eq(PROJECT_ID), eq("suppressed@test.com")))
          .thenReturn(Single.just(true));
      when(logDao.insertLogIfNotExists(any()))
          .thenReturn(Single.just(1L));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .build();

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.SKIPPED);
      assertThat(result.getResults().get(0).getErrorMessage()).contains("suppressed");
    }

    @Test
    void shouldHandleProviderFailure() {
      stubMappingRow(PROJECT_ID, "EMAIL", "user@test.com");

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(emailTemplate()));
      when(providerFactory.getProvider(eq(ChannelType.EMAIL)))
          .thenReturn(Optional.of(emailProvider));
      when(suppressionDao.isEmailSuppressed(eq(PROJECT_ID), eq("user@test.com")))
          .thenReturn(Single.just(false));
      when(logDao.insertLogIfNotExists(any()))
          .thenReturn(Single.just(1L));
      when(emailProvider.send(any(), any()))
          .thenReturn(Single.just(NotificationResult.builder()
              .success(false)
              .permanentFailure(true)
              .errorMessage("Invalid email")
              .errorCode("InvalidAddress")
              .latencyMs(50)
              .build()));
      when(logDao.updateLogStatus(anyLong(), any(), anyInt(), any(), any(), any(), any(), anyInt()))
          .thenReturn(Single.just(1));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .build();

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getFailed()).isEqualTo(1);
      assertThat(result.getResults().get(0).getStatus())
          .isEqualTo(NotificationStatus.PERMANENT_FAILURE);
    }

    @Test
    void shouldReturnEmptyListWhenNoProviderRegistered() {
      stubMappingRow(PROJECT_ID, "SLACK", "C123");
      when(mappingRow.getString("channel_type")).thenReturn("SLACK");

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.just(slackTemplate()));
      when(providerFactory.getProvider(eq(ChannelType.SLACK)))
          .thenReturn(Optional.empty());

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .build();

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getTotalRecipients()).isZero();
    }

    @Test
    void shouldUseRequestRecipientsOverDbRecipients() {
      stubMappingRow(PROJECT_ID, "EMAIL", "db@test.com");

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(emailTemplate()));
      when(providerFactory.getProvider(eq(ChannelType.EMAIL)))
          .thenReturn(Optional.of(emailProvider));
      when(suppressionDao.isEmailSuppressed(eq(PROJECT_ID), eq("request@test.com")))
          .thenReturn(Single.just(false));
      when(logDao.insertLogIfNotExists(any()))
          .thenReturn(Single.just(1L));
      when(emailProvider.send(any(), any()))
          .thenReturn(Single.just(NotificationResult.builder()
              .success(true).externalId("e1").latencyMs(10).build()));
      when(logDao.updateLogStatus(anyLong(), any(), anyInt(), any(), any(), any(), any(), anyInt()))
          .thenReturn(Single.just(1));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .recipients(RecipientsDto.builder()
              .emails(List.of("request@test.com"))
              .build())
          .build();

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getTotalRecipients()).isEqualTo(1);
      assertThat(result.getResults().get(0).getRecipient()).isEqualTo("request@test.com");
    }

    @Test
    void shouldHandleSendException() {
      stubMappingRow(PROJECT_ID, "EMAIL", "user@test.com");

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(emailTemplate()));
      when(providerFactory.getProvider(eq(ChannelType.EMAIL)))
          .thenReturn(Optional.of(emailProvider));
      when(suppressionDao.isEmailSuppressed(eq(PROJECT_ID), eq("user@test.com")))
          .thenReturn(Single.just(false));
      when(logDao.insertLogIfNotExists(any()))
          .thenReturn(Single.error(new RuntimeException("DB error")));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .build();

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.FAILED);
      assertThat(result.getResults().get(0).getErrorMessage()).contains("DB error");
    }

    @Test
    void shouldHandleNonPermanentProviderFailure() {
      stubMappingRow(PROJECT_ID, "EMAIL", "user@test.com");

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(emailTemplate()));
      when(providerFactory.getProvider(eq(ChannelType.EMAIL)))
          .thenReturn(Optional.of(emailProvider));
      when(suppressionDao.isEmailSuppressed(eq(PROJECT_ID), eq("user@test.com")))
          .thenReturn(Single.just(false));
      when(logDao.insertLogIfNotExists(any()))
          .thenReturn(Single.just(1L));
      when(emailProvider.send(any(), any()))
          .thenReturn(Single.just(NotificationResult.builder()
              .success(false)
              .permanentFailure(false)
              .errorMessage("Temporary failure")
              .latencyMs(20)
              .build()));
      when(logDao.updateLogStatus(anyLong(), any(), anyInt(), any(), any(), any(), any(), anyInt()))
          .thenReturn(Single.just(1));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .build();

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.FAILED);
    }
  }

  @Nested
  class SendNotificationByEvent {

    @Mock Row eventRow;
    @Mock NotificationProvider emailProvider;

    private void stubEventRow(Long channelId, String channelType, String recipient) {
      when(eventRow.getLong("channel_id")).thenReturn(channelId);
      when(eventRow.getString("channel_type")).thenReturn(channelType);
      when(eventRow.getString("recipient")).thenReturn(recipient);
      when(eventRow.getValue("config")).thenReturn(
          new JsonObject().put("type", "EMAIL").put("fromAddress", "noreply@test.com"));
    }

    @Test
    void shouldSendByEventNameSynchronously() {
      stubEventRow(CHANNEL_ID, "EMAIL", "user@test.com");

      when(mappingDao.getActiveMappingsWithChannelByProjectAndEvent(
          eq(PROJECT_ID), eq("alert")))
          .thenReturn(Single.just(List.of(eventRow)));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(emailTemplate()));
      when(providerFactory.getProvider(eq(ChannelType.EMAIL)))
          .thenReturn(Optional.of(emailProvider));
      when(suppressionDao.isEmailSuppressed(eq(PROJECT_ID), eq("user@test.com")))
          .thenReturn(Single.just(false));
      when(logDao.insertLogIfNotExists(any()))
          .thenReturn(Single.just(1L));
      when(emailProvider.send(any(), any()))
          .thenReturn(Single.just(NotificationResult.builder()
              .success(true).externalId("ext-2").latencyMs(80).build()));
      when(logDao.updateLogStatus(anyLong(), any(), anyInt(), any(), any(), any(), any(), anyInt()))
          .thenReturn(Single.just(1));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .eventName("alert")
          .channelTypes(List.of(ChannelType.EMAIL))
          .build();

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getTotalRecipients()).isEqualTo(1);
      assertThat(result.getQueued()).isEqualTo(1);
    }

    @Test
    void shouldErrorWhenNoMappingsFoundForEvent() {
      when(mappingDao.getActiveMappingsWithChannelByProjectAndEvent(
          eq(PROJECT_ID), eq("unknown")))
          .thenReturn(Single.just(List.of()));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .eventName("unknown")
          .channelTypes(List.of(ChannelType.EMAIL))
          .build();

      service.sendNotification(PROJECT_ID, request)
          .test()
          .assertError(e -> e.getMessage().contains("No active channel mappings"));
    }

    @Test
    void shouldSkipChannelTypesNotInRequest() {
      stubEventRow(CHANNEL_ID, "SLACK", "C123");

      when(mappingDao.getActiveMappingsWithChannelByProjectAndEvent(
          eq(PROJECT_ID), eq("alert")))
          .thenReturn(Single.just(List.of(eventRow)));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .eventName("alert")
          .channelTypes(List.of(ChannelType.EMAIL))
          .build();

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getTotalRecipients()).isZero();
    }

    @Test
    void shouldReturnEmptyBatchWhenNoRecipientsAndNoneInRequest() {
      stubEventRow(CHANNEL_ID, "EMAIL", null);

      when(mappingDao.getActiveMappingsWithChannelByProjectAndEvent(
          eq(PROJECT_ID), eq("alert")))
          .thenReturn(Single.just(List.of(eventRow)));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .eventName("alert")
          .channelTypes(List.of(ChannelType.EMAIL))
          .build();

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getTotalRecipients()).isZero();
    }

    @Test
    void shouldErrorWhenRecipientsProvidedButDontMatchChannelTypes() {
      stubEventRow(CHANNEL_ID, "SLACK", null);

      when(mappingDao.getActiveMappingsWithChannelByProjectAndEvent(
          eq(PROJECT_ID), eq("alert")))
          .thenReturn(Single.just(List.of(eventRow)));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .eventName("alert")
          .channelTypes(List.of(ChannelType.EMAIL))
          .recipients(RecipientsDto.builder()
              .emails(List.of("user@test.com"))
              .build())
          .build();

      service.sendNotification(PROJECT_ID, request)
          .test()
          .assertError(e -> e.getMessage().contains("don't match the resolved channel types"));
    }
  }

  @Nested
  class SendNotificationValidation {

    @Test
    void shouldErrorWhenEventNameMissing() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .channelTypes(List.of(ChannelType.EMAIL))
          .build();

      service.sendNotification(PROJECT_ID, request)
          .test()
          .assertError(e -> e.getMessage().contains("eventName"));
    }

    @Test
    void shouldErrorWhenProjectIdMissing() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .eventName("alert")
          .channelTypes(List.of(ChannelType.EMAIL))
          .build();

      service.sendNotification(null, request)
          .test()
          .assertError(e -> e.getMessage().contains("projectId"));
    }

    @Test
    void shouldErrorWhenChannelTypesMissing() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .eventName("alert")
          .build();

      service.sendNotification(PROJECT_ID, request)
          .test()
          .assertError(e -> e.getMessage().contains("channelTypes"));
    }

    @Test
    void shouldErrorWhenAllFieldsMissing() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder().build();

      service.sendNotification(null, request)
          .test()
          .assertError(e ->
              e.getMessage().contains("eventName")
              && e.getMessage().contains("projectId")
              && e.getMessage().contains("channelTypes"));
    }

    @Test
    void shouldUseProvidedIdempotencyKey() {
      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.empty());

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .idempotencyKey("my-custom-key")
          .build();

      service.sendNotification(PROJECT_ID, request)
          .test()
          .assertError(e -> e.getMessage().contains("Mapping not found"));
    }
  }

  @Nested
  class AsyncSendNotification {

    @Mock Row mappingRow;

    private void stubMappingRow(String projectId, String channelType, String recipient) {
      when(mappingRow.getString("project_id")).thenReturn(projectId);
      when(mappingRow.getLong("channel_id")).thenReturn(CHANNEL_ID);
      when(mappingRow.getString("channel_type")).thenReturn(channelType);
      when(mappingRow.getString("event_name")).thenReturn("alert");
      when(mappingRow.getString("recipient")).thenReturn(recipient);
      when(mappingRow.getValue("config")).thenReturn(
          new JsonObject().put("type", "EMAIL").put("fromAddress", "noreply@test.com"));
    }

    @Test
    void shouldEnqueueByMappingIdAsynchronously() {
      stubMappingRow(PROJECT_ID, "EMAIL", "user@test.com");

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(emailTemplate()));
      when(suppressionDao.isEmailSuppressed(eq(PROJECT_ID), eq("user@test.com")))
          .thenReturn(Single.just(false));
      when(logDao.getLogByIdempotency(eq(PROJECT_ID), anyString(), eq(ChannelType.EMAIL),
          eq("user@test.com")))
          .thenReturn(Maybe.empty());
      when(logDao.insertLog(any()))
          .thenReturn(Single.just(LOG_ID));
      when(notificationQueue.enqueue(any()))
          .thenReturn(Single.just("sqs-msg-id"));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .build();

      var result = service.sendNotificationAsync(PROJECT_ID, request).blockingGet();

      assertThat(result.getTotalRecipients()).isEqualTo(1);
      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.QUEUED);
      assertThat(result.getResults().get(0).getExternalId()).isEqualTo("sqs-msg-id");
    }

    @Test
    void shouldSkipDuplicateInAsyncMode() {
      stubMappingRow(PROJECT_ID, "EMAIL", "user@test.com");

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(emailTemplate()));
      when(suppressionDao.isEmailSuppressed(eq(PROJECT_ID), eq("user@test.com")))
          .thenReturn(Single.just(false));
      when(logDao.getLogByIdempotency(eq(PROJECT_ID), anyString(), eq(ChannelType.EMAIL),
          eq("user@test.com")))
          .thenReturn(Maybe.just(notificationLog()));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .build();

      var result = service.sendNotificationAsync(PROJECT_ID, request).blockingGet();

      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.SKIPPED);
    }

    @Test
    void shouldHandleEnqueueError() {
      stubMappingRow(PROJECT_ID, "EMAIL", "user@test.com");

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(emailTemplate()));
      when(suppressionDao.isEmailSuppressed(eq(PROJECT_ID), eq("user@test.com")))
          .thenReturn(Single.just(false));
      when(logDao.getLogByIdempotency(eq(PROJECT_ID), anyString(), eq(ChannelType.EMAIL),
          eq("user@test.com")))
          .thenReturn(Maybe.empty());
      when(logDao.insertLog(any()))
          .thenReturn(Single.just(LOG_ID));
      when(notificationQueue.enqueue(any()))
          .thenReturn(Single.error(new RuntimeException("SQS unavailable")));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .build();

      var result = service.sendNotificationAsync(PROJECT_ID, request).blockingGet();

      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.FAILED);
      assertThat(result.getResults().get(0).getErrorMessage()).contains("SQS unavailable");
    }

    @Test
    void shouldSkipSuppressedEmailInAsyncMode() {
      stubMappingRow(PROJECT_ID, "EMAIL", "suppressed@test.com");

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(emailTemplate()));
      when(suppressionDao.isEmailSuppressed(eq(PROJECT_ID), eq("suppressed@test.com")))
          .thenReturn(Single.just(true));
      when(logDao.getLogByIdempotency(eq(PROJECT_ID), anyString(), eq(ChannelType.EMAIL),
          eq("suppressed@test.com")))
          .thenReturn(Maybe.empty());

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .build();

      var result = service.sendNotificationAsync(PROJECT_ID, request).blockingGet();

      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.SKIPPED);
      assertThat(result.getResults().get(0).getErrorMessage()).contains("suppressed");
    }
  }

  @Nested
  class SuppressionBypass {

    @Mock Row mappingRow;
    @Mock NotificationProvider slackProvider;

    @Test
    void shouldBypassSuppressionCheckForNonEmailChannels() {
      when(mappingRow.getString("project_id")).thenReturn(PROJECT_ID);
      when(mappingRow.getLong("channel_id")).thenReturn(CHANNEL_ID);
      when(mappingRow.getString("channel_type")).thenReturn("SLACK");
      when(mappingRow.getString("event_name")).thenReturn("alert");
      when(mappingRow.getString("recipient")).thenReturn("C123");
      when(mappingRow.getValue("config")).thenReturn(
          new JsonObject().put("type", "SLACK").put("accessToken", "xoxb-tok"));

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.just(slackTemplate()));
      when(providerFactory.getProvider(eq(ChannelType.SLACK)))
          .thenReturn(Optional.of(slackProvider));
      when(logDao.insertLogIfNotExists(any()))
          .thenReturn(Single.just(1L));
      when(slackProvider.send(any(), any()))
          .thenReturn(Single.just(NotificationResult.builder()
              .success(true).externalId("ts-1").latencyMs(50).build()));
      when(logDao.updateLogStatus(anyLong(), any(), anyInt(), any(), any(), any(), any(), anyInt()))
          .thenReturn(Single.just(1));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .build();

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getQueued()).isEqualTo(1);
      verify(suppressionDao, never()).isEmailSuppressed(any(), any());
    }
  }

  @Nested
  class RecipientResolution {

    @Mock Row mappingRow;
    @Mock NotificationProvider emailProvider;

    @Test
    void shouldResolveSlackRecipientsFromChannelAndUserIds() {
      when(mappingRow.getString("project_id")).thenReturn(PROJECT_ID);
      when(mappingRow.getLong("channel_id")).thenReturn(CHANNEL_ID);
      when(mappingRow.getString("channel_type")).thenReturn("SLACK");
      when(mappingRow.getString("event_name")).thenReturn("alert");
      when(mappingRow.getString("recipient")).thenReturn(null);
      when(mappingRow.getValue("config")).thenReturn(
          new JsonObject().put("type", "SLACK").put("accessToken", "xoxb-tok"));

      NotificationProvider slackProvider = org.mockito.Mockito.mock(NotificationProvider.class);

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.just(slackTemplate()));
      when(providerFactory.getProvider(eq(ChannelType.SLACK)))
          .thenReturn(Optional.of(slackProvider));
      when(logDao.insertLogIfNotExists(any()))
          .thenReturn(Single.just(1L));
      when(slackProvider.send(any(), any()))
          .thenReturn(Single.just(NotificationResult.builder()
              .success(true).externalId("ts-1").latencyMs(10).build()));
      when(logDao.updateLogStatus(anyLong(), any(), anyInt(), any(), any(), any(), any(), anyInt()))
          .thenReturn(Single.just(1));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .recipients(RecipientsDto.builder()
              .slackChannelIds(List.of("C123"))
              .slackUserIds(List.of("U456"))
              .build())
          .build();

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getTotalRecipients()).isEqualTo(2);
    }

    @Test
    void shouldResolveTeamsRecipients() {
      when(mappingRow.getString("project_id")).thenReturn(PROJECT_ID);
      when(mappingRow.getLong("channel_id")).thenReturn(CHANNEL_ID);
      when(mappingRow.getString("channel_type")).thenReturn("TEAMS");
      when(mappingRow.getString("event_name")).thenReturn("alert");
      when(mappingRow.getString("recipient")).thenReturn(null);
      when(mappingRow.getValue("config")).thenReturn(
          new JsonObject().put("type", "TEAMS").put("workflowUrl", "https://teams.example.com"));

      NotificationProvider teamsProvider = org.mockito.Mockito.mock(NotificationProvider.class);

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));

      NotificationTemplate teamsTemplate = NotificationTemplate.builder()
          .id(TEMPLATE_ID)
          .eventName("alert")
          .channelType(ChannelType.TEAMS)
          .version(1)
          .body(TeamsTemplateBody.builder().title("Alert").text("Hello").build())
          .isActive(true)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();

      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.TEAMS)))
          .thenReturn(Maybe.just(teamsTemplate));
      when(providerFactory.getProvider(eq(ChannelType.TEAMS)))
          .thenReturn(Optional.of(teamsProvider));
      when(logDao.insertLogIfNotExists(any()))
          .thenReturn(Single.just(1L));
      when(teamsProvider.send(any(), any()))
          .thenReturn(Single.just(NotificationResult.builder()
              .success(true).latencyMs(10).build()));
      when(logDao.updateLogStatus(anyLong(), any(), anyInt(), any(), any(), any(), any(), anyInt()))
          .thenReturn(Single.just(1));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .recipients(RecipientsDto.builder()
              .teamsWorkflowUrls(List.of("https://teams.example.com/hook"))
              .build())
          .build();

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getTotalRecipients()).isEqualTo(1);
    }

    @Test
    void shouldResolveSlackWebhookRecipients() {
      when(mappingRow.getString("project_id")).thenReturn(PROJECT_ID);
      when(mappingRow.getLong("channel_id")).thenReturn(CHANNEL_ID);
      when(mappingRow.getString("channel_type")).thenReturn("SLACK_WEBHOOK");
      when(mappingRow.getString("event_name")).thenReturn("alert");
      when(mappingRow.getString("recipient")).thenReturn(null);
      when(mappingRow.getValue("config")).thenReturn(
          new JsonObject().put("type", "SLACK_WEBHOOK"));

      NotificationProvider webhookProvider = org.mockito.Mockito.mock(NotificationProvider.class);

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));
      when(templateDao.getTemplateByEventNameAndChannel(eq("alert"), eq(ChannelType.SLACK_WEBHOOK)))
          .thenReturn(Maybe.just(slackTemplate()));
      when(providerFactory.getProvider(eq(ChannelType.SLACK_WEBHOOK)))
          .thenReturn(Optional.of(webhookProvider));
      when(logDao.insertLogIfNotExists(any()))
          .thenReturn(Single.just(1L));
      when(webhookProvider.send(any(), any()))
          .thenReturn(Single.just(NotificationResult.builder()
              .success(true).latencyMs(10).build()));
      when(logDao.updateLogStatus(anyLong(), any(), anyInt(), any(), any(), any(), any(), anyInt()))
          .thenReturn(Single.just(1));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .recipients(RecipientsDto.builder()
              .slackWebhookUrls(List.of("https://hooks.slack.com/xxx"))
              .build())
          .build();

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getTotalRecipients()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyListForNullRecipients() {
      when(mappingRow.getString("project_id")).thenReturn(PROJECT_ID);
      when(mappingRow.getLong("channel_id")).thenReturn(CHANNEL_ID);
      when(mappingRow.getString("channel_type")).thenReturn("EMAIL");
      when(mappingRow.getString("event_name")).thenReturn("alert");
      when(mappingRow.getString("recipient")).thenReturn(null);
      when(mappingRow.getValue("config")).thenReturn(
          new JsonObject().put("type", "EMAIL").put("fromAddress", "x@y.com"));

      when(mappingDao.getActiveMappingWithChannelById(eq(10L)))
          .thenReturn(Maybe.just(mappingRow));

      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .mappingId(10L)
          .recipients(null)
          .build();

      service.sendNotification(PROJECT_ID, request)
          .test()
          .assertError(e -> e.getMessage().contains("No recipients found"));
    }
  }
}
