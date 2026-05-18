package org.dreamhorizon.pulses3archiver.mapper;

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AttributeUtils {

  private AttributeUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static Map<String, String> toMap(List<KeyValue> kvList) {
    if (kvList == null || kvList.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, String> result = new HashMap<>(kvList.size());
    for (KeyValue kv : kvList) {
      result.put(kv.getKey(), anyValueToString(kv.getValue()));
    }
    return result;
  }

  public static String get(Map<String, String> attrs, String key) {
    return attrs.getOrDefault(key, "");
  }

  public static String coalesce(Map<String, String> attrs, String... keys) {
    for (String key : keys) {
      String val = attrs.get(key);
      if (val != null && !val.isEmpty()) {
        return val;
      }
    }
    return "";
  }

  public static int parseUInt16OrZero(String value) {
    if (value == null || value.isEmpty()) {
      return 0;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return Math.max(0, Math.min(parsed, 65535));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  public static int parseUInt8OrZero(String value) {
    if (value == null || value.isEmpty()) {
      return 0;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return Math.max(0, Math.min(parsed, 255));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  public static float parseFloat32OrZero(String value) {
    if (value == null || value.isEmpty()) {
      return 0f;
    }
    try {
      return Float.parseFloat(value.trim());
    } catch (NumberFormatException e) {
      return 0f;
    }
  }

  public static double parseFloat64OrZero(String value) {
    if (value == null || value.isEmpty()) {
      return 0d;
    }
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return 0d;
    }
  }

  public static boolean parseBool(String value) {
    return "true".equalsIgnoreCase(value);
  }

  public static long nanosToMicros(long nanos) {
    return nanos / 1000L;
  }

  public static int hourFromNanos(long nanos) {
    return Instant.ofEpochSecond(0, nanos).atOffset(java.time.ZoneOffset.UTC).getHour();
  }

  public static String bytesToHex(ByteString bytes) {
    if (bytes == null || bytes.isEmpty()) {
      return "";
    }
    byte[] arr = bytes.toByteArray();
    StringBuilder sb = new StringBuilder(arr.length * 2);
    for (byte b : arr) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private static String anyValueToString(AnyValue value) {
    if (value == null) {
      return "";
    }
    switch (value.getValueCase()) {
      case STRING_VALUE: return value.getStringValue();
      case BOOL_VALUE:   return String.valueOf(value.getBoolValue());
      case INT_VALUE:    return String.valueOf(value.getIntValue());
      case DOUBLE_VALUE: return String.valueOf(value.getDoubleValue());
      default:           return value.toString();
    }
  }
}
