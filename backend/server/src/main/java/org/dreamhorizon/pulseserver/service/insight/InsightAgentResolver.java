package org.dreamhorizon.pulseserver.service.insight;

import org.dreamhorizon.pulseserver.dao.insightjob.InsightType;
import org.dreamhorizon.pulseserver.service.ai.impl.AiUpstreamProxyExecutor;

public interface InsightAgentResolver {
  AiUpstreamProxyExecutor resolve(InsightType type);
}
