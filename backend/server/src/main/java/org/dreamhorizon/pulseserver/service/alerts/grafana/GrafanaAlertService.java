package org.dreamhorizon.pulseserver.service.alerts.grafana;

import io.reactivex.rxjava3.core.Completable;
import org.dreamhorizon.pulseserver.dto.alerts.grafana.GrafanaWebhookRequest;

/**
 * Handles inbound Grafana alert webhooks. Resolves the current on-call engineer
 * via {@code OnCallService}, formats a Slack message tagging them, and posts to
 * the alerts Slack channel.
 *
 * <p>This is a <strong>stateless alert pass-through</strong> — it does not create
 * incidents, persist anything, or participate in ack/recover/close workflows.
 * For incident lifecycle, see {@code IncidentService}.
 */
public interface GrafanaAlertService {

  /**
   * Process a Grafana webhook payload end-to-end. Always completes successfully —
   * any internal failure is caught, logged, and surfaced as a {@code @channel}
   * fallback Slack message so alerts are never silently dropped.
   *
   * @param request parsed webhook payload (may contain multiple grouped alerts)
   */
  Completable handleAlert(GrafanaWebhookRequest request);
}
