package org.dreamhorizon.pulseserver.service.usagelimit.models;

import lombok.Getter;

import java.util.Optional;

@Getter
public enum UsageLimitParameter {
  MAX_USER_SESSIONS_PER_PROJECT(
      "max_user_sessions_per_project",
      "Max User Sessions per Project",
      "remaining_session_credit",
      WindowType.MONTHLY,
      DataType.INTEGER
  ),
  MAX_EVENTS_PER_PROJECT(
      "max_events_per_project",
      "Max Events per Project",
      "remaining_event_credit",
      WindowType.MONTHLY,
      DataType.INTEGER
  );

  private final String key;
  private final String displayName;
  private final String redisCreditKey;
  private final WindowType defaultWindowType;
  private final DataType dataType;

  UsageLimitParameter(String key, String displayName, String redisCreditKey, WindowType defaultWindowType, DataType dataType) {
    this.key = key;
    this.displayName = displayName;
    this.redisCreditKey = redisCreditKey;
    this.defaultWindowType = defaultWindowType;
    this.dataType = dataType;
  }

  public static UsageLimitParameter fromKey(String key) {
    for (UsageLimitParameter parameter : values()) {
      if (parameter.getKey().equals(key)) {
        return parameter;
      }
    }
    throw new IllegalArgumentException("No UsageLimitParameter found for key: " + key);
  }

  public static Optional<UsageLimitParameter> fromRedisCreditKey(String redisCreditKey) {
    for (UsageLimitParameter parameter : values()) {
      if (parameter.getRedisCreditKey().equals(redisCreditKey)) {
        return Optional.of(parameter);
      }
    }
    return Optional.empty();
  }
}

