package org.dreamhorizon.pulseserver.service.errorattribution;

import java.util.Map;

/**
 * In-memory bundle for one HTTP response: summary aggregate (cacheable shape) plus optional
 * drill-down payloads per signal. Drill-down is not persisted on {@link ErrorAttributionResult}.
 */
public record ErrorAttributionWithDrillDown(
    ErrorAttributionResult summary, Map<String, ErrorAttributionDrillDownResult> drillDownBySignal) {}
