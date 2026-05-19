package org.dreamhorizon.pulseserver.service.insight;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightType;
import org.dreamhorizon.pulseserver.service.ai.impl.AiUpstreamProxyExecutor;

/** Returns the shared AiUpstreamProxyExecutor for all insight types (default). */
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class DefaultInsightAgentResolver implements InsightAgentResolver {

  private final AiUpstreamProxyExecutor executor;

  @Override
  public AiUpstreamProxyExecutor resolve(final InsightType type) {
    return executor;
  }
}
