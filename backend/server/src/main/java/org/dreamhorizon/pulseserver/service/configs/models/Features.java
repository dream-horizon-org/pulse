package org.dreamhorizon.pulseserver.service.configs.models;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum Features {
  interaction,
  java_crash,
  js_crash,
  java_anr,
  network_change,
  network_instrumentation,
  screen_session,
  custom_events,
  rn_navigation;

  public static List<String> getFeatures() {
    return Arrays.stream(Features.values()).map(Enum::name).collect(Collectors.toList());
  }
}
