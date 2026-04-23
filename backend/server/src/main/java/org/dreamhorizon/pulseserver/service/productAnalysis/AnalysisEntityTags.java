package org.dreamhorizon.pulseserver.service.productAnalysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.dreamhorizon.pulseserver.error.ServiceError;

/** Validation and normalization for funnel/journey tag strings. */
public final class AnalysisEntityTags {

  private static final int MAX_TAGS_PER_ENTITY = 64;
  private static final int MAX_TAG_LENGTH = 128;

  private AnalysisEntityTags() {}

  public static List<String> normalizeOrThrow(List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String s : raw) {
      if (s == null) {
        continue;
      }
      String t = s.trim();
      if (t.isEmpty()) {
        continue;
      }
      if (t.length() > MAX_TAG_LENGTH) {
        throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
            "Each tag must be at most " + MAX_TAG_LENGTH + " characters");
      }
      out.add(t);
    }
    if (out.size() > MAX_TAGS_PER_ENTITY) {
      throw ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
          "At most " + MAX_TAGS_PER_ENTITY + " tags allowed per funnel or journey");
    }
    return new ArrayList<>(out);
  }
}
