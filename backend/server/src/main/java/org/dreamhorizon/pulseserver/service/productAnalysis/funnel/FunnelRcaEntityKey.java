package org.dreamhorizon.pulseserver.service.productAnalysis.funnel;

import org.dreamhorizon.pulseserver.error.ServiceError;

/** Parses async RCA entity keys {@code {funnelId}:{focusStepIndex}}. */
public final class FunnelRcaEntityKey {

  private FunnelRcaEntityKey() {}

  public static String format(long funnelId, int focusStepIndex) {
    return funnelId + ":" + focusStepIndex;
  }

  public static Parsed parse(String entityKey) {
    if (entityKey == null || entityKey.isBlank()) {
      throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
          "entityKey is required for funnel RCA");
    }
    int colon = entityKey.indexOf(':');
    if (colon <= 0 || colon >= entityKey.length() - 1) {
      throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
          "entityKey must be funnelId:focusStepIndex");
    }
    try {
      long funnelId = Long.parseLong(entityKey.substring(0, colon).trim());
      int focusStepIndex = Integer.parseInt(entityKey.substring(colon + 1).trim());
      return new Parsed(funnelId, focusStepIndex);
    } catch (NumberFormatException e) {
      throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
          "entityKey must be funnelId:focusStepIndex");
    }
  }

  public record Parsed(long funnelId, int focusStepIndex) {}
}
