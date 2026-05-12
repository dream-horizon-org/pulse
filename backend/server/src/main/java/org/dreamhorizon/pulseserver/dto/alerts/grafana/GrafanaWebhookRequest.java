package org.dreamhorizon.pulseserver.dto.alerts.grafana;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Incoming payload from Grafana unified alerting webhook contact point.
 *
 * <p>See <a
 * href="https://grafana.com/docs/grafana/latest/alerting/configure-notifications/manage-contact-points/integrations/webhook-notifier/">
 * Grafana webhook integration docs</a> for the full schema. Unknown fields are ignored so
 * Grafana version upgrades don't break ingestion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GrafanaWebhookRequest {

  /** "firing" or "resolved" (Grafana sets per group). */
  private String status;

  private String receiver;

  /** URL of Grafana instance (top-level), useful for fallback links. */
  private String externalURL;

  /** Per-alert payload. Usually a single alert when grouped, but can be a list. */
  private List<GrafanaAlert> alerts;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class GrafanaAlert {
    /** "firing" or "resolved". */
    private String status;

    /** Includes {@code alertname}, {@code severity}, etc. */
    private Map<String, String> labels;

    /** Includes {@code summary}, {@code description}. */
    private Map<String, String> annotations;

    /** Direct link to the alert rule in Grafana. */
    private String generatorURL;

    private String startsAt;
    private String endsAt;
    private String fingerprint;
  }
}
