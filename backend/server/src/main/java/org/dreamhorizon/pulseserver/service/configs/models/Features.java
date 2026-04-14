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
  custom_events,
  rn_screen_load,
  rn_screen_interactive,
  rn_screen_session,
  session_replay,
  click,
  /** Dashboard screen heatmaps (Pulse UI); client SDKs may ignore. */
  heatmap,
  android_network,
  ios_network,
  rn_network,
  ios_crash,
  ios_lifecycle,
  android_activity,
  android_fragment,
  android_slowrendering,
  location;

  public static List<String> getFeatures() {
    return Arrays.stream(Features.values()).map(Enum::name).collect(Collectors.toList());
  }
}
