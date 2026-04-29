package org.dreamhorizon.pulseserver.service.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.dao.incidentdao.IncidentDao;
import org.dreamhorizon.pulseserver.dao.incidentdao.models.IncidentRow;
import org.dreamhorizon.pulseserver.resources.incident.models.CreateIncidentRequestDto;
import org.dreamhorizon.pulseserver.resources.incident.models.enums.IncidentSeverity;
import org.dreamhorizon.pulseserver.resources.incident.models.enums.IncidentStatus;
import org.dreamhorizon.pulseserver.resources.notification.models.NotificationBatchResponseDto;
import org.dreamhorizon.pulseserver.resources.notification.models.SendNotificationRequestDto;
import org.dreamhorizon.pulseserver.service.notification.NotificationService;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationEventName;
import org.dreamhorizon.pulseserver.service.oncall.OnCallService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IncidentServiceImplTest {

  private static final String PROJECT_ID = "test-project";
  private static final long INCIDENT_ID = 1L;
  private static final String ACTION_BY = "admin@test.com";
  private static final String SLACK_CHANNEL = "C12345";
  private static final String REPORTER_EMAIL = "reporter@test.com";
  private static final String REPORTER_NAME = "Test Reporter";
  private static final String DEFAULT_PROJECT_ID = "default-project";

  @Mock IncidentDao incidentDao;
  @Mock NotificationService notificationService;
  @Mock NotificationConfig notificationConfig;
  @Mock OnCallService onCallService;

  IncidentServiceImpl service;

  @BeforeEach
  void setUp() {
    ProjectContext.setProjectId(PROJECT_ID);
    when(notificationConfig.getIncidentConfig())
        .thenReturn(new NotificationConfig.IncidentConfig(SLACK_CHANNEL, "GO_ALERT", null));
    when(onCallService.getOnCallSlackMentions()).thenReturn(Single.just("N/A"));
    service = new IncidentServiceImpl(incidentDao, notificationService, notificationConfig,
        onCallService);
  }

  @AfterEach
  void tearDown() {
    ProjectContext.clear();
  }

  private IncidentRow buildIncidentRow(IncidentStatus status) {
    return IncidentRow.builder()
        .id(INCIDENT_ID)
        .title("Test Incident")
        .description("Test Description")
        .severity(IncidentSeverity.P2)
        .reporterName(REPORTER_NAME)
        .reporterEmail(REPORTER_EMAIL)
        .orgIdentifier(PROJECT_ID)
        .status(status)
        .createdAt("2026-03-16T10:00:00")
        .updatedAt("2026-03-16T10:00:00")
        .build();
  }

  private CreateIncidentRequestDto buildCreateRequest() {
    return CreateIncidentRequestDto.builder()
        .title("Test Incident")
        .description("Test Description")
        .severity(IncidentSeverity.P2)
        .reporterName(REPORTER_NAME)
        .reporterEmail(REPORTER_EMAIL)
        .orgIdentifier(PROJECT_ID)
        .build();
  }

  private NotificationBatchResponseDto batchResponse() {
    return NotificationBatchResponseDto.builder()
        .totalRecipients(1)
        .queued(1)
        .failed(0)
        .build();
  }

  private void stubNotificationsSuccess() {
    when(notificationService.sendNotification(eq(DEFAULT_PROJECT_ID), any()))
        .thenReturn(Single.just(batchResponse()));
    when(notificationService.sendNotificationAsync(eq(DEFAULT_PROJECT_ID), any()))
        .thenReturn(Single.just(batchResponse()));
  }

  // ===================== GetIncidents =====================

  @Nested
  class GetIncidents {

    @Test
    void shouldReturnMappedIncidentList() {
      IncidentRow row1 = buildIncidentRow(IncidentStatus.OPEN);
      IncidentRow row2 = buildIncidentRow(IncidentStatus.ACKNOWLEDGED);
      row2.setId(2L);
      row2.setTitle("Second Incident");

      when(incidentDao.getIncidentsByProject(PROJECT_ID))
          .thenReturn(Single.just(List.of(row1, row2)));

      var result = service.getIncidents().blockingGet();

      assertThat(result).hasSize(2);
      assertThat(result.get(0).getTitle()).isEqualTo("Test Incident");
      assertThat(result.get(0).getStatus()).isEqualTo(IncidentStatus.OPEN);
      assertThat(result.get(1).getTitle()).isEqualTo("Second Incident");
      assertThat(result.get(1).getStatus()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
    }

    @Test
    void shouldReturnEmptyListWhenNoIncidents() {
      when(incidentDao.getIncidentsByProject(PROJECT_ID))
          .thenReturn(Single.just(List.of()));

      var result = service.getIncidents().blockingGet();

      assertThat(result).isEmpty();
    }

    @Test
    void shouldPropagateErrorWhenDaoFails() {
      when(incidentDao.getIncidentsByProject(PROJECT_ID))
          .thenReturn(Single.error(new RuntimeException("DB error")));

      service.getIncidents()
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().equals("DB error"));
    }

    @Test
    void shouldUseProjectIdFromContext() {
      when(incidentDao.getIncidentsByProject(PROJECT_ID))
          .thenReturn(Single.just(List.of()));

      service.getIncidents().blockingGet();

      verify(incidentDao).getIncidentsByProject(PROJECT_ID);
    }

    @Test
    void shouldThrowWhenProjectContextNotSet() {
      ProjectContext.clear();

      assertThatThrownBy(() -> service.getIncidents())
          .isInstanceOf(IllegalStateException.class);
    }
  }

  // ===================== CreateIncident =====================

  @Nested
  class CreateIncident {

    @Test
    void shouldCreateIncidentAndSendNotifications() {
      IncidentRow savedRow = buildIncidentRow(IncidentStatus.OPEN);
      when(incidentDao.insertIncident(any())).thenReturn(Single.just(savedRow));
      stubNotificationsSuccess();

      var result = service.createIncident(buildCreateRequest()).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(INCIDENT_ID);
      verify(notificationService).sendNotification(eq(DEFAULT_PROJECT_ID), any());
      verify(notificationService).sendNotificationAsync(eq(DEFAULT_PROJECT_ID), any());
    }

    @Test
    void shouldReturnCorrectResponseDto() {
      IncidentRow savedRow = buildIncidentRow(IncidentStatus.OPEN);
      when(incidentDao.insertIncident(any())).thenReturn(Single.just(savedRow));
      stubNotificationsSuccess();

      var result = service.createIncident(buildCreateRequest()).blockingGet();

      assertThat(result.getId()).isEqualTo(INCIDENT_ID);
      assertThat(result.getStatus()).isEqualTo(IncidentStatus.OPEN);
      assertThat(result.getCreatedAt()).isEqualTo("2026-03-16T10:00:00");
    }

    @Test
    void shouldSendSlackWithCorrectParams() {
      IncidentRow savedRow = buildIncidentRow(IncidentStatus.OPEN);
      when(incidentDao.insertIncident(any())).thenReturn(Single.just(savedRow));
      stubNotificationsSuccess();

      service.createIncident(buildCreateRequest()).blockingGet();

      ArgumentCaptor<SendNotificationRequestDto> captor =
          ArgumentCaptor.forClass(SendNotificationRequestDto.class);
      verify(notificationService).sendNotification(eq(DEFAULT_PROJECT_ID), captor.capture());

      SendNotificationRequestDto slackReq = captor.getValue();
      assertThat(slackReq.getChannelTypes()).containsExactly(ChannelType.SLACK);
      assertThat(slackReq.getEventName())
          .isEqualTo(NotificationEventName.CREATE_INCIDENT.getValue());
      assertThat(slackReq.getRecipients().getSlackChannelIds())
          .containsExactly(SLACK_CHANNEL);
      assertThat(slackReq.getParams())
          .containsEntry("title", "Test Incident")
          .containsEntry("severity", "P2")
          .containsEntry("reporterEmail", REPORTER_EMAIL);
    }

    @Test
    void shouldSendEmailToReporter() {
      IncidentRow savedRow = buildIncidentRow(IncidentStatus.OPEN);
      when(incidentDao.insertIncident(any())).thenReturn(Single.just(savedRow));
      stubNotificationsSuccess();

      service.createIncident(buildCreateRequest()).blockingGet();

      ArgumentCaptor<SendNotificationRequestDto> captor =
          ArgumentCaptor.forClass(SendNotificationRequestDto.class);
      verify(notificationService).sendNotificationAsync(eq(DEFAULT_PROJECT_ID), captor.capture());

      SendNotificationRequestDto emailReq = captor.getValue();
      assertThat(emailReq.getChannelTypes()).containsExactly(ChannelType.EMAIL);
      assertThat(emailReq.getEventName())
          .isEqualTo(NotificationEventName.CREATE_INCIDENT.getValue());
      assertThat(emailReq.getRecipients().getEmails())
          .containsExactly(REPORTER_EMAIL);
    }

    @Test
    void shouldPropagateErrorWhenInsertFails() {
      when(incidentDao.insertIncident(any()))
          .thenReturn(Single.error(new RuntimeException("Insert failed")));

      service.createIncident(buildCreateRequest())
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().equals("Insert failed"));

      verify(notificationService, never()).sendNotification(any(), any());
      verify(notificationService, never()).sendNotificationAsync(any(), any());
    }
  }

  // ===================== AcknowledgeIncident =====================

  @Nested
  class AcknowledgeIncident {

    @Test
    void shouldAcknowledgeOpenIncidentSuccessfully() {
      IncidentRow openRow = buildIncidentRow(IncidentStatus.OPEN);
      IncidentRow ackedRow = buildIncidentRow(IncidentStatus.ACKNOWLEDGED);

      when(incidentDao.getIncidentById(INCIDENT_ID)).thenReturn(Single.just(openRow));
      stubNotificationsSuccess();
      when(incidentDao.acknowledgeIncident(INCIDENT_ID)).thenReturn(Single.just(ackedRow));

      service.acknowledgeIncident(INCIDENT_ID, ACTION_BY)
          .test()
          .assertComplete()
          .assertNoErrors();

      verify(incidentDao).acknowledgeIncident(INCIDENT_ID);
    }

    @Test
    void shouldRejectWhenStatusIsNotOpen() {
      IncidentRow ackedRow = buildIncidentRow(IncidentStatus.ACKNOWLEDGED);
      when(incidentDao.getIncidentById(INCIDENT_ID)).thenReturn(Single.just(ackedRow));

      service.acknowledgeIncident(INCIDENT_ID, ACTION_BY)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("not in OPEN state"));

      verify(incidentDao, never()).acknowledgeIncident(INCIDENT_ID);
    }

    @Test
    void shouldRejectWhenStatusIsRecovered() {
      IncidentRow recoveredRow = buildIncidentRow(IncidentStatus.RECOVERED);
      when(incidentDao.getIncidentById(INCIDENT_ID)).thenReturn(Single.just(recoveredRow));

      service.acknowledgeIncident(INCIDENT_ID, ACTION_BY)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("not in OPEN state"));

      verify(incidentDao, never()).acknowledgeIncident(INCIDENT_ID);
      verify(notificationService, never()).sendNotification(any(), any());
    }

    @Test
    void shouldSendNotificationsWithActionBy() {
      IncidentRow openRow = buildIncidentRow(IncidentStatus.OPEN);
      IncidentRow ackedRow = buildIncidentRow(IncidentStatus.ACKNOWLEDGED);

      when(incidentDao.getIncidentById(INCIDENT_ID)).thenReturn(Single.just(openRow));
      stubNotificationsSuccess();
      when(incidentDao.acknowledgeIncident(INCIDENT_ID)).thenReturn(Single.just(ackedRow));

      service.acknowledgeIncident(INCIDENT_ID, ACTION_BY).blockingAwait();

      ArgumentCaptor<SendNotificationRequestDto> captor =
          ArgumentCaptor.forClass(SendNotificationRequestDto.class);
      verify(notificationService).sendNotification(eq(DEFAULT_PROJECT_ID), captor.capture());

      SendNotificationRequestDto slackReq = captor.getValue();
      assertThat(slackReq.getEventName())
          .isEqualTo(NotificationEventName.ACKNOWLEDGE_INCIDENT.getValue());
      assertThat(slackReq.getParams())
          .containsEntry("actionBy", ACTION_BY);
      assertThat(slackReq.getRecipients().getSlackChannelIds())
          .containsExactly(SLACK_CHANNEL);
    }

    @Test
    void shouldPropagateErrorWhenDaoGetFails() {
      when(incidentDao.getIncidentById(INCIDENT_ID))
          .thenReturn(Single.error(new RuntimeException("Not found")));

      service.acknowledgeIncident(INCIDENT_ID, ACTION_BY)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().equals("Not found"));

      verify(notificationService, never()).sendNotification(any(), any());
      verify(notificationService, never()).sendNotificationAsync(any(), any());
    }
  }

  // ===================== RecoverIncident =====================

  @Nested
  class RecoverIncident {

    @Test
    void shouldRecoverAcknowledgedIncidentSuccessfully() {
      IncidentRow ackedRow = buildIncidentRow(IncidentStatus.ACKNOWLEDGED);
      IncidentRow recoveredRow = buildIncidentRow(IncidentStatus.RECOVERED);

      when(incidentDao.getIncidentById(INCIDENT_ID)).thenReturn(Single.just(ackedRow));
      stubNotificationsSuccess();
      when(incidentDao.recoverIncident(INCIDENT_ID)).thenReturn(Single.just(recoveredRow));

      service.recoverIncident(INCIDENT_ID, ACTION_BY)
          .test()
          .assertComplete()
          .assertNoErrors();

      verify(incidentDao).recoverIncident(INCIDENT_ID);
    }

    @Test
    void shouldRejectWhenStatusIsNotAcknowledged() {
      IncidentRow openRow = buildIncidentRow(IncidentStatus.OPEN);
      when(incidentDao.getIncidentById(INCIDENT_ID)).thenReturn(Single.just(openRow));

      service.recoverIncident(INCIDENT_ID, ACTION_BY)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("not in ACKNOWLEDGED state"));

      verify(incidentDao, never()).recoverIncident(INCIDENT_ID);
    }

    @Test
    void shouldRejectWhenStatusIsRecovered() {
      IncidentRow recoveredRow = buildIncidentRow(IncidentStatus.RECOVERED);
      when(incidentDao.getIncidentById(INCIDENT_ID)).thenReturn(Single.just(recoveredRow));

      service.recoverIncident(INCIDENT_ID, ACTION_BY)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("not in ACKNOWLEDGED state"));

      verify(incidentDao, never()).recoverIncident(INCIDENT_ID);
      verify(notificationService, never()).sendNotification(any(), any());
    }

    @Test
    void shouldSendNotificationsWithActionBy() {
      IncidentRow ackedRow = buildIncidentRow(IncidentStatus.ACKNOWLEDGED);
      IncidentRow recoveredRow = buildIncidentRow(IncidentStatus.RECOVERED);

      when(incidentDao.getIncidentById(INCIDENT_ID)).thenReturn(Single.just(ackedRow));
      stubNotificationsSuccess();
      when(incidentDao.recoverIncident(INCIDENT_ID)).thenReturn(Single.just(recoveredRow));

      service.recoverIncident(INCIDENT_ID, ACTION_BY).blockingAwait();

      ArgumentCaptor<SendNotificationRequestDto> captor =
          ArgumentCaptor.forClass(SendNotificationRequestDto.class);
      verify(notificationService).sendNotification(eq(DEFAULT_PROJECT_ID), captor.capture());

      SendNotificationRequestDto slackReq = captor.getValue();
      assertThat(slackReq.getEventName())
          .isEqualTo(NotificationEventName.RECOVERED_INCIDENT.getValue());
      assertThat(slackReq.getParams())
          .containsEntry("actionBy", ACTION_BY);
    }

    @Test
    void shouldPropagateErrorWhenDaoGetFails() {
      when(incidentDao.getIncidentById(INCIDENT_ID))
          .thenReturn(Single.error(new RuntimeException("DB down")));

      service.recoverIncident(INCIDENT_ID, ACTION_BY)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().equals("DB down"));

      verify(notificationService, never()).sendNotification(any(), any());
      verify(notificationService, never()).sendNotificationAsync(any(), any());
    }
  }

  // ===================== CloseIncident =====================

  @Nested
  class CloseIncident {

    @Test
    void shouldCloseRecoveredIncidentSuccessfully() {
      IncidentRow recoveredRow = buildIncidentRow(IncidentStatus.RECOVERED);
      IncidentRow closedRow = buildIncidentRow(IncidentStatus.CLOSED);

      when(incidentDao.getIncidentById(INCIDENT_ID)).thenReturn(Single.just(recoveredRow));
      stubNotificationsSuccess();
      when(incidentDao.closeIncident(INCIDENT_ID)).thenReturn(Single.just(closedRow));

      service.closeIncident(INCIDENT_ID, ACTION_BY)
          .test()
          .assertComplete()
          .assertNoErrors();

      verify(incidentDao).closeIncident(INCIDENT_ID);
    }

    @Test
    void shouldRejectWhenStatusIsNotRecovered() {
      IncidentRow openRow = buildIncidentRow(IncidentStatus.OPEN);
      when(incidentDao.getIncidentById(INCIDENT_ID)).thenReturn(Single.just(openRow));

      service.closeIncident(INCIDENT_ID, ACTION_BY)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("not in RECOVERED state"));

      verify(incidentDao, never()).closeIncident(INCIDENT_ID);
    }

    @Test
    void shouldRejectWhenStatusIsAcknowledged() {
      IncidentRow ackedRow = buildIncidentRow(IncidentStatus.ACKNOWLEDGED);
      when(incidentDao.getIncidentById(INCIDENT_ID)).thenReturn(Single.just(ackedRow));

      service.closeIncident(INCIDENT_ID, ACTION_BY)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("not in RECOVERED state"));

      verify(incidentDao, never()).closeIncident(INCIDENT_ID);
      verify(notificationService, never()).sendNotification(any(), any());
    }

    @Test
    void shouldSendNotificationsWithActionBy() {
      IncidentRow recoveredRow = buildIncidentRow(IncidentStatus.RECOVERED);
      IncidentRow closedRow = buildIncidentRow(IncidentStatus.CLOSED);

      when(incidentDao.getIncidentById(INCIDENT_ID)).thenReturn(Single.just(recoveredRow));
      stubNotificationsSuccess();
      when(incidentDao.closeIncident(INCIDENT_ID)).thenReturn(Single.just(closedRow));

      service.closeIncident(INCIDENT_ID, ACTION_BY).blockingAwait();

      ArgumentCaptor<SendNotificationRequestDto> captor =
          ArgumentCaptor.forClass(SendNotificationRequestDto.class);
      verify(notificationService).sendNotification(eq(DEFAULT_PROJECT_ID), captor.capture());

      SendNotificationRequestDto slackReq = captor.getValue();
      assertThat(slackReq.getEventName())
          .isEqualTo(NotificationEventName.CLOSE_INCIDENT.getValue());
      assertThat(slackReq.getParams())
          .containsEntry("actionBy", ACTION_BY);
    }

    @Test
    void shouldPropagateErrorWhenDaoGetFails() {
      when(incidentDao.getIncidentById(INCIDENT_ID))
          .thenReturn(Single.error(new RuntimeException("Connection refused")));

      service.closeIncident(INCIDENT_ID, ACTION_BY)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().equals("Connection refused"));

      verify(notificationService, never()).sendNotification(any(), any());
      verify(notificationService, never()).sendNotificationAsync(any(), any());
    }
  }
}
