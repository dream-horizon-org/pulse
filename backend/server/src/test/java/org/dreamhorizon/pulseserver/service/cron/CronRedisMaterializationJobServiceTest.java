package org.dreamhorizon.pulseserver.service.cron;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.constant.CronJobType;
import org.dreamhorizon.pulseserver.dao.cronjobhistory.CronJobEnqueueResult;
import org.dreamhorizon.pulseserver.dao.cronjobhistory.CronJobHistoryDao;
import org.dreamhorizon.pulseserver.resources.internal.models.CronRedisSyncJobAcceptedRestResponse;
import org.dreamhorizon.pulseserver.service.kong.KongApiKeyRedisSyncService;
import org.dreamhorizon.pulseserver.service.kong.KongUsageCreditsRedisSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CronRedisMaterializationJobServiceTest {

  @Mock
  private CronJobHistoryDao cronJobHistoryDao;
  @Mock
  private KongApiKeyRedisSyncService kongApiKeyRedisSyncService;
  @Mock
  private KongUsageCreditsRedisSyncService kongUsageCreditsRedisSyncService;
  @Mock
  private UsageLimitNotificationProcessService usageLimitNotificationProcessService;

  private CronRedisMaterializationJobService service;

  @BeforeEach
  void setUp() {
    service = new CronRedisMaterializationJobService(
        cronJobHistoryDao,
        kongApiKeyRedisSyncService,
        kongUsageCreditsRedisSyncService,
        usageLimitNotificationProcessService);
  }

  @Test
  void shouldReturnDeduplicatedUsageCreditsJobWithoutCallingKong() {
    when(cronJobHistoryDao.enqueueOrDeduplicate(
        eq(CronJobType.USAGE_CREDITS_TO_REDIS),
        any(),
        eq(CronRedisMaterializationJobService.STALE_IN_PROGRESS_RECLAIMED)))
        .thenReturn(Single.just(new CronJobEnqueueResult(9L, true)));

    CronRedisSyncJobAcceptedRestResponse body =
        service.acceptUsageCreditsSyncToRedis().blockingGet();

    assertThat(body.getJobId()).isEqualTo(9L);
    assertThat(body.isDeduplicated()).isTrue();
    assertThat(body.getJobType()).isEqualTo(CronJobType.USAGE_CREDITS_TO_REDIS);
    verify(kongUsageCreditsRedisSyncService, never()).syncUsageCreditsToRedis();
  }

  @Test
  void shouldRunUsageCreditsKongSyncWhenNotDeduplicated() {
    when(cronJobHistoryDao.enqueueOrDeduplicate(
        eq(CronJobType.USAGE_CREDITS_TO_REDIS),
        any(),
        eq(CronRedisMaterializationJobService.STALE_IN_PROGRESS_RECLAIMED)))
        .thenReturn(Single.just(new CronJobEnqueueResult(2L, false)));
    when(kongUsageCreditsRedisSyncService.syncUsageCreditsToRedis()).thenReturn(Single.just(3));
    when(cronJobHistoryDao.markCompleted(2L)).thenReturn(Completable.complete());

    CronRedisSyncJobAcceptedRestResponse body =
        service.acceptUsageCreditsSyncToRedis().blockingGet();

    assertThat(body.getJobId()).isEqualTo(2L);
    assertThat(body.isDeduplicated()).isFalse();
    verify(kongUsageCreditsRedisSyncService, timeout(5_000)).syncUsageCreditsToRedis();
    verify(cronJobHistoryDao, timeout(5_000)).markCompleted(2L);
  }

  @Test
  void shouldReturnDeduplicatedUsageLimitNotificationsJobWithoutRunningProcess() {
    when(cronJobHistoryDao.enqueueOrDeduplicate(
        eq(CronJobType.USAGE_LIMIT_NOTIFICATIONS),
        any(),
        eq(CronRedisMaterializationJobService.STALE_IN_PROGRESS_RECLAIMED)))
        .thenReturn(Single.just(new CronJobEnqueueResult(11L, true)));

    CronRedisSyncJobAcceptedRestResponse body = service.acceptUsageLimitNotifications().blockingGet();

    assertThat(body.getJobId()).isEqualTo(11L);
    assertThat(body.isDeduplicated()).isTrue();
    assertThat(body.getJobType()).isEqualTo(CronJobType.USAGE_LIMIT_NOTIFICATIONS);
    verify(usageLimitNotificationProcessService, never()).processUsageLimitNotifications();
  }

  @Test
  void shouldRunUsageLimitNotificationsWhenNotDeduplicated() {
    when(cronJobHistoryDao.enqueueOrDeduplicate(
        eq(CronJobType.USAGE_LIMIT_NOTIFICATIONS),
        any(),
        eq(CronRedisMaterializationJobService.STALE_IN_PROGRESS_RECLAIMED)))
        .thenReturn(Single.just(new CronJobEnqueueResult(3L, false)));
    when(usageLimitNotificationProcessService.processUsageLimitNotifications())
        .thenReturn(Completable.complete());
    when(cronJobHistoryDao.markCompleted(3L)).thenReturn(Completable.complete());

    CronRedisSyncJobAcceptedRestResponse body = service.acceptUsageLimitNotifications().blockingGet();

    assertThat(body.getJobId()).isEqualTo(3L);
    assertThat(body.isDeduplicated()).isFalse();
    verify(usageLimitNotificationProcessService, timeout(5_000)).processUsageLimitNotifications();
    verify(cronJobHistoryDao, timeout(5_000)).markCompleted(3L);
  }
}
