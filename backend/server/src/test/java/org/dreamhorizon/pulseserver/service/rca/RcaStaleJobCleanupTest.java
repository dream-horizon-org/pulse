package org.dreamhorizon.pulseserver.service.rca;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaReportJobDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RcaStaleJobCleanupTest {

  @Mock private Vertx vertx;
  @Mock private RcaReportJobDao jobDao;

  @Test
  void shouldSchedulePeriodicCleanupOnStartup() {
    new RcaStaleJobCleanup(vertx, jobDao);

    verify(vertx).setPeriodic(eq(RcaStaleJobCleanup.CLEANUP_INTERVAL_MS), any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldMarkStaleJobsWhenTimerFires() {
    when(jobDao.markStaleJobsFailed(RcaStaleJobCleanup.STALE_THRESHOLD_MINUTES))
        .thenReturn(Single.just(3));

    ArgumentCaptor<Handler<Long>> captor = ArgumentCaptor.forClass(Handler.class);
    new RcaStaleJobCleanup(vertx, jobDao);
    verify(vertx).setPeriodic(anyLong(), captor.capture());

    captor.getValue().handle(1L);

    verify(jobDao).markStaleJobsFailed(RcaStaleJobCleanup.STALE_THRESHOLD_MINUTES);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldNotMarkAnyJobsWhenCountIsZero() {
    when(jobDao.markStaleJobsFailed(RcaStaleJobCleanup.STALE_THRESHOLD_MINUTES))
        .thenReturn(Single.just(0));

    ArgumentCaptor<Handler<Long>> captor = ArgumentCaptor.forClass(Handler.class);
    new RcaStaleJobCleanup(vertx, jobDao);
    verify(vertx).setPeriodic(anyLong(), captor.capture());

    assertThatCode(() -> captor.getValue().handle(1L)).doesNotThrowAnyException();
    verify(jobDao).markStaleJobsFailed(RcaStaleJobCleanup.STALE_THRESHOLD_MINUTES);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldSuppressCleanupErrorWithoutThrowing() {
    when(jobDao.markStaleJobsFailed(anyInt()))
        .thenReturn(Single.error(new RuntimeException("db down")));

    ArgumentCaptor<Handler<Long>> captor = ArgumentCaptor.forClass(Handler.class);
    new RcaStaleJobCleanup(vertx, jobDao);
    verify(vertx).setPeriodic(anyLong(), captor.capture());

    assertThatCode(() -> captor.getValue().handle(1L)).doesNotThrowAnyException();
  }
}
