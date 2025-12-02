package org.dreamhorizon.pulseserver.errorgrouping.service;

import static org.dreamhorizon.pulseserver.errorgrouping.FramesParser.TOP_N_FRAMES;
import static org.dreamhorizon.pulseserver.errorgrouping.FramesParser.parse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import io.opentelemetry.proto.resource.v1.Resource;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.errorgrouping.Symbolicator;
import org.dreamhorizon.pulseserver.errorgrouping.model.CompleteSymbolication;
import org.dreamhorizon.pulseserver.errorgrouping.model.EventMeta;
import org.dreamhorizon.pulseserver.errorgrouping.model.Frame;
import org.dreamhorizon.pulseserver.errorgrouping.model.Group;
import org.dreamhorizon.pulseserver.errorgrouping.model.Lane;
import org.dreamhorizon.pulseserver.errorgrouping.model.ParsedFrames;
import org.dreamhorizon.pulseserver.errorgrouping.model.StackTraceEvent;
import org.dreamhorizon.pulseserver.errorgrouping.utils.ErrorGroupingUtils;


@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ErrorGroupingService {

  public static final String SIG_VERSION = "v1";
  private static final char[] HEX = "0123456789abcdef".toCharArray();
  private static final DateTimeFormatter DT64_9 =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS")
          .withZone(ZoneOffset.UTC);
  private final ClickhouseQueryService clickhouseQueryService;
  private final Symbolicator symbolicator;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public static String traceIdHex(ByteString bs) {
    if (bs == null || bs.isEmpty()) {
      return null;          // absent
    }
    byte[] b = bs.toByteArray();
    if (b.length != 16) {
      return null;                      // invalid length
    }
    return toHex(b);
  }

  public static String spanIdHex(ByteString bs) {
    if (bs == null || bs.isEmpty()) {
      return null;
    }
    byte[] b = bs.toByteArray();
    if (b.length != 8) {
      return null;
    }
    return toHex(b);
  }

  private static String toHex(byte[] bytes) {
    char[] out = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int v = bytes[i] & 0xFF;
      out[i * 2] = HEX[v >>> 4];
      out[i * 2 + 1] = HEX[v & 0x0F];
    }
    return new String(out);
  }

  private static String formatTs9(long epochNanos) {
    long seconds = Math.floorDiv(epochNanos, 1_000_000_000L);
    int nanos = (int) Math.floorMod(epochNanos, 1_000_000_000L);
    return DT64_9.format(Instant.ofEpochSecond(seconds, nanos));
  }

  // OPTIMIZATION: Use StringBuilder for better performance than String concatenation
  public static String buildSignature(String platform, List<String> excTypes, List<String> tokens) {
    // Pre-allocate capacity to avoid multiple resizes
    int capacity = 50 + platform.length() + excTypes.size() * 20 + tokens.size() * 30;
    StringBuilder sb = new StringBuilder(capacity);

    sb.append(SIG_VERSION).append("|platform:").append(platform).append("|exc:");

    // Manual join for exception types
    for (int i = 0; i < excTypes.size(); i++) {
      if (i > 0) {
        sb.append(">");
      }
      sb.append(excTypes.get(i));
    }

    sb.append("|frames:");

    // Manual join for frames
    for (int i = 0; i < tokens.size(); i++) {
      if (i > 0) {
        sb.append(">");
      }
      sb.append(tokens.get(i));
    }

    return sb.toString();
  }

  public static String buildDisplayName(Lane lane, List<String> excTypes, List<String> frames, String groupId) {
    String headline;
    if (excTypes.isEmpty()) {
      headline = (lane == Lane.NDK) ? "NativeError" : "Error";
    } else if (lane == Lane.JAVA && excTypes.size() >= 2) {
      headline = excTypes.get(0) + " caused by " + excTypes.get(excTypes.size() - 1);
    } else {
      headline = excTypes.get(0);
    }

    String loc = frames.isEmpty() ? "" : frames.get(0);
    String locPretty = switch (lane) {
      case JAVA -> ErrorGroupingUtils.shortenJava(loc);
      case JS -> ErrorGroupingUtils.shortenJs(loc);
      default -> loc;
    };

    String where = locPretty.isEmpty() ? "" :
        (lane == Lane.JS ? " in " : " at ") + locPretty;

    return headline + where + " [" + groupId + "]";
  }

  /**
   * Choose primary lane for error grouping.
   * Simple rule: Use the topmost exception type, then fallback to total frame count.
   */
  public static Lane choosePrimary(ParsedFrames st) {
    int js = st.getJsFrames().size();
    int jv = st.getJavaFrames().size();
    int nk = st.getNdkFrames().size();

    if (js == 0 && jv == 0 && nk == 0) {
      return Lane.UNKNOWN;
    }

    // Priority 1: Use topmost exception type (most reliable signal)
    if (st.getPrimaryExceptionLane() != null) {
      Lane primary = st.getPrimaryExceptionLane();
      if (primary == Lane.JS && js > 0) {
        return Lane.JS;
      }
      if (primary == Lane.JAVA && jv > 0) {
        return Lane.JAVA;
      }
      if (primary == Lane.NDK && nk > 0) {
        return Lane.NDK;
      }
    }

    // Priority 2: Total frame count (tie-breaker: JS > JAVA > NDK)
    int max = Math.max(js, Math.max(jv, nk));
    if (js == max) {
      return Lane.JS;
    }
    if (jv == max) {
      return Lane.JAVA;
    }
    return Lane.NDK;
  }

  public static List<String> typesForPrimary(ParsedFrames st, Lane lane) {
    List<String> types = switch (lane) {
      case JS -> st.getJsTypes();
      case JAVA -> st.getJavaTypes();
      case NDK -> st.getNdkTypes();
      default -> List.of();
    };
    if (types == null || types.isEmpty()) {
      if (!st.getJsTypes().isEmpty()) {
        return st.getJsTypes();
      }
      if (!st.getJavaTypes().isEmpty()) {
        return st.getJavaTypes();
      }
      if (!st.getNdkFrames().isEmpty()) {
        return st.getNdkTypes();
      }
      return List.of();
    }
    return types;
  }

  public static List<Frame> selectPrimaryTokens(ParsedFrames st, Lane lane, int topN) {
    List<? extends Frame> frames = switch (lane) {
      case JS -> st.getJsFrames();
      case JAVA -> st.getJavaFrames();
      case NDK -> st.getNdkFrames();
      default -> List.of();
    };
    if (frames.isEmpty()) {
      return List.of();
    }

    List<Frame> chosen = new ArrayList<>(topN);
    for (Frame f : frames) {
      if (f.isInApp()) {
        chosen.add(f);
        if (chosen.size() == topN) {
          break;
        }
      }
    }
    if (chosen.isEmpty()) {
      for (Frame f : frames) {
        chosen.add(f);
        if (chosen.size() == topN) {
          break;
        }
      }
    }
    return chosen;
  }

  public Single<Long> ingest(ExportLogsServiceRequest exportLogsServiceRequest) {
    return process(exportLogsServiceRequest)
        .flatMap(clickhouseQueryService::insertStackTraces);
  }

  public Single<List<StackTraceEvent>> process(ExportLogsServiceRequest exportLogsServiceRequest) {
    List<Single<StackTraceEvent>> events = new ArrayList<>();
    for (ResourceLogs rl : exportLogsServiceRequest.getResourceLogsList()) {
      Resource res = rl.getResource();

      // OPTIMIZATION: Convert resource attributes to map once per resource, reuse for all logs
      Map<String, String> resourceAttrMap = attributesToMap(res.getAttributesList());
      String appVersion = getResourceAttribute(resourceAttrMap, "app.build_name").orElse(null);
      String appVersionCode = getResourceAttribute(resourceAttrMap, "app.build_id").orElse(null);
      String platform = getResourceAttribute(resourceAttrMap, "os.name").orElse(null);


      for (ScopeLogs scopeLogs : rl.getScopeLogsList()) {
        for (LogRecord logRecord : scopeLogs.getLogRecordsList()) {
          // OPTIMIZATION: Convert log record attributes to map once per log
          Map<String, String> logAttrMap = attributesToMap(logRecord.getAttributesList());

          String stackTrace = getResourceAttribute(logAttrMap, "exception.stacktrace").orElse(null);


          EventMeta eventMeta = EventMeta.builder()
              .appVersion(appVersion)
              .appVersionCode(appVersionCode)
              .platform(platform)
              .build();

          // Use processWithCompleteSymbolication to get both grouping and full symbolication
          events.add(processWithCompleteSymbolication(stackTrace, eventMeta)
              .map(result -> {
                // Reconstruct complete symbolicated stack trace
                String symbolicatedStackTrace = result.completeSymbolication().reconstructStackTrace();

                return StackTraceEvent.builder()
                    .timestamp(formatTs9(logRecord.getObservedTimeUnixNano()))
                    .eventName(getResourceAttribute(logAttrMap, "pulse.type").orElse(null))
                    .exceptionStackTraceRaw(stackTrace)  // Raw original stack trace
                    .exceptionStackTrace(symbolicatedStackTrace)  // Complete symbolicated stack trace
                    .exceptionMessage(getResourceAttribute(logAttrMap, "exception.message").orElse(null))
                    .exceptionType(getResourceAttribute(logAttrMap, "exception.type").orElse(null))
                    .screenName(getResourceAttribute(logAttrMap, "screen.name").orElse(null))
                    .userId(getResourceAttribute(logAttrMap, "user.id").orElse(null))
                    .sessionId(getResourceAttribute(logAttrMap, "session.id").orElse(null))
                    .osVersion(getResourceAttribute(resourceAttrMap, "os.version").orElse(null))
                    .platform(platform)
                    .appVersionCode(appVersionCode)
                    .appVersion(appVersion)
                    .sdkVersion(getResourceAttribute(resourceAttrMap, "rum.sdk.version").orElse(null))
                    .deviceModel(getResourceAttribute(resourceAttrMap, "device.model.name").orElse(null))
                    .spanId(spanIdHex(logRecord.getSpanId()))
                    .traceId(traceIdHex(logRecord.getTraceId()))
                    .groupId(result.group().getGroupId())
                    .title(result.group().getDisplayName())
                    .signature(result.group().getSignature())
                    .fingerprint(result.group().getFingerprint())
                    .interactions(getInteractionNames(resourceAttrMap))
                    .build();
              }));
        }
      }
    }
    return Observable.fromIterable(events)              // List<Single<StackTraceEvent>>
        .flatMapMaybe(s -> s.toMaybe().onErrorComplete()) // skip any failing Single
        .toList();
  }

  @SneakyThrows
  private List<String> getInteractionNames(Map<String, String> resourceAttributes) {
    Optional<String> interactions = getResourceAttribute(resourceAttributes, "pulse.interaction.active.names");
    if (interactions.isPresent()) {
      return objectMapper.readValue(interactions.get(), new TypeReference<>() {
      });
    }
    return Collections.emptyList();
  }

  // OPTIMIZATION: Use Map for O(1) attribute lookup instead of O(n) iteration
  private Optional<String> getResourceAttribute(Map<String, String> resourceAttributes, String key) {
    return Optional.ofNullable(resourceAttributes.get(key));
  }

  // OPTIMIZATION: Convert List<KeyValue> to Map once per resource for efficient reuse
  private Map<String, String> attributesToMap(List<KeyValue> attributes) {
    Map<String, String> map = new HashMap<>();
    for (KeyValue kv : attributes) {
      map.put(kv.getKey(), kv.getValue().getStringValue());
    }
    return map;
  }

  private Single<List<String>> symbolicate(Lane lane, List<Frame> frames, EventMeta eventMeta) {
    return switch (lane) {
      case JS -> symbolicator.symbolicateJsInPlace(frames, eventMeta);
      case JAVA -> symbolicator.retrace(frames, eventMeta);
      case NDK -> Single.just(Collections.emptyList());
      case UNKNOWN -> Single.just(Collections.emptyList());
    };
  }

  /**
   * Process stack trace and return both grouping info AND complete symbolication.
   * This allows us to:
   * 1. Generate group ID based on primary lane
   * 2. Reconstruct full symbolicated stack trace preserving order
   */
  public Single<ProcessingResult> processWithCompleteSymbolication(String raw, EventMeta meta) {
    List<String> lines = Arrays.asList((raw == null ? "" : raw).split("\\R", -1));
    ParsedFrames parsedFrames = parse(lines);

    // Choose primary lane for grouping
    Lane primary = choosePrimary(parsedFrames);
    List<String> excTypes = typesForPrimary(parsedFrames, primary);
    List<Frame> primaryFrames = selectPrimaryTokens(parsedFrames, primary, TOP_N_FRAMES);

    // Symbolicate all lanes in parallel for complete stack trace
    Single<CompleteSymbolication> completeSymb = symbolicateComplete(parsedFrames, meta);

    // Symbolicate primary lane for grouping
    Single<List<String>> primaryTokens = symbolicate(primary, primaryFrames, meta);

    return Single.zip(primaryTokens, completeSymb, (tokens, complete) -> {
      // Build group from primary lane
      String platformTag = ErrorGroupingUtils.platformTag(primary);
      String signature = buildSignature(platformTag, excTypes, tokens);
      String sha1 = ErrorGroupingUtils.sha1Hex(signature);
      String groupId = "EXC-" + sha1.substring(0, 10).toUpperCase(Locale.ROOT);
      String title = buildDisplayName(primary, excTypes, tokens, groupId);
      Group group = new Group(platformTag, signature, sha1, groupId, title);

      return new ProcessingResult(group, complete);
    });
  }

  /**
   * Symbolicate ALL frames across ALL lanes (JS, Java, NDK) in parallel.
   * Returns CompleteSymbolication which can reconstruct the full stack trace.
   * OPTIMIZATION: Skip symbolication if no frames exist for a lane (early return).
   */
  private Single<CompleteSymbolication> symbolicateComplete(ParsedFrames parsedFrames, EventMeta meta) {
    // OPTIMIZATION: Early return if no frames to process
    if (parsedFrames.getJsFrames().isEmpty()
        && parsedFrames.getJavaFrames().isEmpty()
        && parsedFrames.getNdkFrames().isEmpty()) {
      return Single.just(new CompleteSymbolication(
          parsedFrames,
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList()
      ));
    }

    // Symbolicate all lanes in parallel
    Single<List<String>> jsSymb = symbolicateAllFrames(Lane.JS, parsedFrames.getJsFrames(), meta);
    Single<List<String>> javaSymb = symbolicateAllFrames(Lane.JAVA, parsedFrames.getJavaFrames(), meta);
    Single<List<String>> ndkSymb = symbolicateAllFrames(Lane.NDK, parsedFrames.getNdkFrames(), meta);

    return Single.zip(jsSymb, javaSymb, ndkSymb, (js, java, ndk) ->
        new CompleteSymbolication(parsedFrames, js, java, ndk)
    );
  }

  private Single<List<String>> symbolicateAllFrames(Lane lane, List<? extends Frame> frames, EventMeta meta) {
    if (frames.isEmpty()) {
      return Single.just(Collections.emptyList());
    }
    return symbolicate(lane, new ArrayList<>(frames), meta);
  }

  public record ProcessingResult(Group group, CompleteSymbolication completeSymbolication) {
  }
}
