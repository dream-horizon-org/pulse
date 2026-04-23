package org.dreamhorizon.pulseserver.service.analytics;

import io.reactivex.rxjava3.core.Single;

/**
 * Service for triggering analytics batch jobs.
 */
public interface AnalyticsBatchService {

  /**
   * Triggers the daily batch job for computing funnel results.
   *
   * @return a single emitting true if triggered successfully
   */
  Single<Boolean> triggerFunnelsBatch();

  /**
   * Triggers the daily batch job for computing journey results.
   *
   * @return a single emitting true if triggered successfully
   */
  Single<Boolean> triggerJourneysBatch();

  /**
   * Triggers the incremental batch job for processing events.
   *
   * @return a single emitting true if triggered successfully
   */
  Single<Boolean> triggerEventsBatch();

  /**
   * Triggers the on-save job for a specific funnel.
   *
   * @param funnelId the ID of the funnel to compute
   * @return a single emitting true if triggered successfully
   */
  Single<Boolean> triggerFunnelOnSaveJob(Long funnelId);

  /**
   * Triggers the on-save job for a specific journey.
   *
   * @param journeyId the ID of the journey to compute
   * @return a single emitting true if triggered successfully
   */
  Single<Boolean> triggerJourneyOnSaveJob(Long journeyId);
}
