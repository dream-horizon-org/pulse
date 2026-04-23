package org.dreamhorizon.pulsealertscron.util;

/**
 * Resolves the dashboard base URL for notification templates ({@code {{dashboardUrl}}}).
 */
public final class DashboardUrlResolver {

  /** Fallback when env/HOCON does not set {@code CONFIG_SERVICE_APPLICATION_DASHBOARDBASEURL}. */
  static final String FALLBACK_DASHBOARD_URL = "https://pulse-ux.com";

  private DashboardUrlResolver() {}

  /**
   * Returns trimmed origin with no trailing slash; blank or null uses {@link #FALLBACK_DASHBOARD_URL}.
   */
  public static String resolve(String configured) {
    if (configured == null) {
      return FALLBACK_DASHBOARD_URL;
    }
    String trimmed = configured.trim();
    if (trimmed.isEmpty()) {
      return FALLBACK_DASHBOARD_URL;
    }
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
