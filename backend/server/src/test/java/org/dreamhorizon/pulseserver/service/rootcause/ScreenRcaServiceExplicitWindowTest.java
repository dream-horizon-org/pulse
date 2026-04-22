package org.dreamhorizon.pulseserver.service.rootcause;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rootcause.ScreenRootCauseCacheDao;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperFactory;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperUtil;
import org.junit.jupiter.api.Test;

class ScreenRcaServiceExplicitWindowTest {

  @Test
  void rejectsExplicitWindowLongerThan90Days() {
    RootCauseConfig cfg = RootCauseConfig.withDefaults(null);
    ClickhouseQueryService ch = mock(ClickhouseQueryService.class);
    ScreenRcaService svc =
        new ScreenRcaService(
            cfg,
            ch,
            mock(ScreenRootCauseCacheDao.class),
            new ObjectMapperUtil(ObjectMapperFactory.get()));
    Instant start = Instant.parse("2026-01-01T00:00:00Z");
    Instant end = start.plusSeconds(86400L * 91);

    svc.getScreenRootCause("pid", "Home", start, end, false).test().assertError(Throwable.class);

    verifyNoInteractions(ch);
  }
}
