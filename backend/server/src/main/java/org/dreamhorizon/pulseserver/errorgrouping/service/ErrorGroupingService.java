package org.dreamhorizon.pulseserver.errorgrouping.service;

import static org.dreamhorizon.pulseserver.grouping.parser.FramesParser.TOP_N_FRAMES;
import static org.dreamhorizon.pulseserver.grouping.parser.FramesParser.parse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
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
import org.dreamhorizon.pulseserver.errorgrouping.archive.StackTraceArchiveService;
import org.dreamhorizon.pulseserver.errorgrouping.Symbolicator;
import org.dreamhorizon.pulseserver.errorgrouping.model.CompleteSymbolication;
import org.dreamhorizon.pulseserver.errorgrouping.model.StackTraceEvent;
import org.dreamhorizon.pulseserver.grouping.Grouper;
import org.dreamhorizon.pulseserver.grouping.model.EventMeta;
import org.dreamhorizon.pulseserver.grouping.model.Frame;
import org.dreamhorizon.pulseserver.grouping.model.Group;
import org.dreamhorizon.pulseserver.grouping.model.Lane;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;
import org.dreamhorizon.pulseserver.grouping.util.ErrorGroupingUtils;
import org.dreamhorizon.pulseserver.service.configs.models.Sdk;


@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ErrorGroupingService {

  private static final char[] HEX = "0123456789abcdef".toCharArray();
  private static final DateTimeFormatter DT64_9 =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS")
          .withZone(ZoneOffset.UTC);
  private final ClickhouseQueryService clickhouseQueryService;
  private final StackTraceArchiveService stackTraceArchiveService;
  private final Symbolicator symbolicator;
  private final ObjectMapper objectMapper;
  public static final String LOG_PREFIX = "[PULSE ERROR GROUPING SERVICE]";

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

  public Single<Long> ingest(ExportLogsServiceRequest exportLogsServiceRequest) {
    return process(exportLogsServiceRequest)
        .flatMap(events ->
            clickhouseQueryService.insertStackTraces(events)
                .flatMap(rows -> stackTraceArchiveService.archive(events)
                    .onErrorComplete()
                    .andThen(Single.just(rows))));
  }

  public Single<List<StackTraceEvent>> process(ExportLogsServiceRequest exportLogsServiceRequest) {
    List<Single<StackTraceEvent>> events = new ArrayList<>();
    for (ResourceLogs rl : exportLogsServiceRequest.getResourceLogsList()) {
      Resource res = rl.getResource();

      // OPTIMIZATION: Convert resource attributes to map once per resource, reuse for all logs
      Map<String, String> resourceAttrMap = attributesToMap(res.getAttributesList());
      String appVersion = getResourceAttribute(resourceAttrMap, "app.build_name").orElse(null);
      String appVersionCode = getResourceAttribute(resourceAttrMap, "app.build_id").orElse(null);
      String platform = resolvePlatform(resourceAttrMap);
      String bundleId = getResourceAttribute(resourceAttrMap, "bundle_id").orElse(null);
      String projectId = getResourceAttribute(resourceAttrMap, "project.id").orElse(null);


      for (ScopeLogs scopeLogs : rl.getScopeLogsList()) {
        for (LogRecord logRecord : scopeLogs.getLogRecordsList()) {
          // OPTIMIZATION: Convert log record attributes to map once per log
          Map<String, String> logAttrMap = attributesToMap(logRecord.getAttributesList());


          String stackTrace = getResourceAttribute(logAttrMap, "exception.stacktrace").orElse(null);

          EventMeta eventMeta = EventMeta.builder()
              .appVersion(appVersion)
              .appVersionCode(appVersionCode)
              .platform(platform)
              .bundleId(bundleId)
              .projectId(projectId)
              .build();

          // Use processWithCompleteSymbolication to get both grouping and full symbolication
          events.add(processWithCompleteSymbolication(stackTrace, eventMeta)
              .map(result -> {
                // Reconstruct complete symbolicated stack trace
                String symbolicatedStackTrace = result.completeSymbolication().reconstructStackTrace();

                String pulseType = getResourceAttribute(logAttrMap, "pulse.type").orElse("otel");
                return StackTraceEvent.builder()
                    .timestamp(formatTs9(logRecord.getObservedTimeUnixNano()))
                    .eventName(logRecord.getEventName())
                    .pulseType(pulseType)
                    .exceptionStackTraceRaw(stackTrace)  // Raw original stack trace
                    .exceptionStackTrace(symbolicatedStackTrace)  // Complete symbolicated stack trace
                    .exceptionMessage(getResourceAttribute(logAttrMap, "exception.message").orElse(null))
                    .exceptionType(getResourceAttribute(logAttrMap, "exception.type").orElse(null))
                    .screenName(getResourceAttribute(logAttrMap, "screen.name").orElse(null))
                    .userId(getResourceAttribute(logAttrMap, "user.id")
                        .or(() -> getResourceAttribute(logAttrMap, "app.installation.id"))
                        .or(() -> getResourceAttribute(resourceAttrMap, "app.installation.id"))
                        .orElse(null))
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
                    .resourceAttributes(resourceAttrMap)
                    .scopeAttributes(attributesToMap(scopeLogs.getScope().getAttributesList()))
                    .logAttributes(logAttrMap)
                    .bundleId(bundleId)
                    .build();
              }));
        }
      }
    }
    return Observable.fromIterable(events)              // List<Single<StackTraceEvent>>
        .flatMapMaybe(s -> s.toMaybe().onErrorComplete()) // skip any failing Single
        .toList();
  }

  /**
   * Resolves dashboard platform once per OTLP resource (shared by all logs under that resource).
   * Resolution order:
   * <ol>
   *   <li>Pulse SDK identity ({@code telemetry.sdk.name} or {@code rum.sdk.name}), parsed as
   *       {@link Sdk}, mapped to {@code web} / {@code Android} / {@code iOS}. Unknown values fall
   *       through.</li>
   *   <li>{@code os.name} from resource.</li>
   * </ol>
   * The {@code platform} attribute on resource or log records is not used here.
   */
  private static String resolvePlatform(Map<String, String> resourceAttrs) {
    String sdkName = resourceAttrs.get("telemetry.sdk.name");
    if (sdkName == null || sdkName.isBlank()) {
      sdkName = resourceAttrs.get("rum.sdk.name");
    }
    if (sdkName != null && !sdkName.isBlank()) {
      try {
        Sdk sdk = Sdk.valueOf(sdkName);
        return switch (sdk) {
          case pulse_web_js -> "web";
          case pulse_android_java, pulse_android_rn -> "Android";
          case pulse_ios_swift, pulse_ios_rn -> "iOS";
        };
      } catch (IllegalArgumentException ignored) {
        // Not a known Pulse Sdk id — fall through to os.name
      }
    }
    return resourceAttrs.get("os.name");
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
      String value = anyValueToString(kv.getValue());
      map.put(kv.getKey(), value);
    }
    return map;
  }

  // Convert OTLP AnyValue to String (handles all types: string, bool, int, double, array)
  private String anyValueToString(AnyValue anyValue) {
    if (anyValue.hasStringValue()) {
      return anyValue.getStringValue();
    } else if (anyValue.hasBoolValue()) {
      return String.valueOf(anyValue.getBoolValue());
    } else if (anyValue.hasIntValue()) {
      return String.valueOf(anyValue.getIntValue());
    } else if (anyValue.hasDoubleValue()) {
      // Format double to match Android format (with .0 for integers)
      double val = anyValue.getDoubleValue();
      if (val == (long) val) {
        return String.valueOf((long) val) + ".0";
      }
      return String.valueOf(val);
    } else if (anyValue.hasArrayValue()) {
      // Convert array to string format matching Android: "[value1, value2, value3]"
      List<String> values = new ArrayList<>();
      for (AnyValue item : anyValue.getArrayValue().getValuesList()) {
        values.add(anyValueToString(item));
      }
      return "[" + String.join(", ", values) + "]";
    } else if (anyValue.hasKvlistValue()) {
      // Convert key-value list to JSON-like string
      Map<String, String> nested = new HashMap<>();
      for (KeyValue nestedKv : anyValue.getKvlistValue().getValuesList()) {
        nested.put(nestedKv.getKey(), anyValueToString(nestedKv.getValue()));
      }
      return nested.toString();
    }
    return "";
  }

  static boolean isIosPlatform(EventMeta meta) {
    if (meta == null || meta.getPlatform() == null) {
      return false;
    }
    String p = meta.getPlatform().toLowerCase(Locale.ROOT);
    return p.contains("ios") || p.contains("iphone") || p.contains("ipados");
  }

  private Single<List<String>> symbolicate(
      Lane lane, List<Frame> frames, EventMeta eventMeta, String rawReport, boolean iosStackTraceFormat) {
    return switch (lane) {
      case JS -> symbolicator.symbolicateJsInPlace(frames, eventMeta);
      case JAVA -> symbolicator.retrace(frames, eventMeta);
      case NDK -> Single.just(Collections.emptyList());
      case IOS_NATIVE -> {
        if (!isIosPlatform(eventMeta)) {
          yield Single.just(frames.stream()
              .map(f -> iosStackTraceFormat ? f.getRawLine() : f.getToken())
              .toList());
        }
        yield symbolicator.symbolicateIosNative(frames, eventMeta, rawReport, iosStackTraceFormat);
      }
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
    Lane primary = Grouper.choosePrimary(parsedFrames);
    log.info(
        "{} parsed primaryLane={} frameCounts js={} java={} ndk={} iosNative={} projectId={} appVersion={} "
            + "versionCode={} platform={} bundleId={}",
        LOG_PREFIX,
        primary,
        parsedFrames.getJsFrames().size(),
        parsedFrames.getJavaFrames().size(),
        parsedFrames.getNdkFrames().size(),
        parsedFrames.getIosNativeFrames().size(),
        meta.getProjectId(),
        meta.getAppVersion(),
        meta.getAppVersionCode(),
        meta.getPlatform(),
        meta.getBundleId());
    List<String> excTypes = Grouper.typesForPrimary(parsedFrames, primary);
    List<Frame> primaryFrames = Grouper.selectPrimaryTokens(parsedFrames, primary, TOP_N_FRAMES);

    // Symbolicate all lanes in parallel for complete stack trace
    Single<CompleteSymbolication> completeSymb = symbolicateComplete(parsedFrames, meta, raw);

    // Symbolicate primary lane for grouping
    Single<List<String>> primaryTokens = symbolicate(primary, primaryFrames, meta, raw, false);

    return Single.zip(primaryTokens, completeSymb, (tokens, complete) -> {
      // Build group from primary lane
      String platformTag = ErrorGroupingUtils.platformTag(primary);
      String signature = Grouper.buildSignature(platformTag, excTypes, tokens);
      String sha1 = ErrorGroupingUtils.sha1Hex(signature);
      String groupId = Grouper.computeGroupId(sha1);
      String title = Grouper.buildDisplayName(primary, excTypes, tokens, groupId);
      Group group = new Group(platformTag, signature, sha1, groupId, title);

      return new ProcessingResult(group, complete);
    });
  }

  /**
   * Symbolicate ALL frames across ALL lanes (JS, Java, NDK) in parallel.
   * Returns CompleteSymbolication which can reconstruct the full stack trace.
   * OPTIMIZATION: Skip symbolication if no frames exist for a lane (early return).
   */
  private Single<CompleteSymbolication> symbolicateComplete(ParsedFrames parsedFrames, EventMeta meta, String rawReport) {
    // OPTIMIZATION: Early return if no frames to process
    if (parsedFrames.getJsFrames().isEmpty()
        && parsedFrames.getJavaFrames().isEmpty()
        && parsedFrames.getNdkFrames().isEmpty()
        && parsedFrames.getIosNativeFrames().isEmpty()) {
      return Single.just(new CompleteSymbolication(
          parsedFrames,
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList()
      ));
    }

    // Symbolicate all lanes in parallel
    Single<List<String>> jsSymb = symbolicateAllFrames(Lane.JS, parsedFrames.getJsFrames(), meta, rawReport);
    Single<List<String>> javaSymb = symbolicateAllFrames(Lane.JAVA, parsedFrames.getJavaFrames(), meta, rawReport);
    Single<List<String>> ndkSymb = symbolicateAllFrames(Lane.NDK, parsedFrames.getNdkFrames(), meta, rawReport);
    Single<List<String>> iosSymb = symbolicateAllFrames(Lane.IOS_NATIVE, parsedFrames.getIosNativeFrames(), meta, rawReport);

    return Single.zip(jsSymb, javaSymb, ndkSymb, iosSymb,
        (js, java, ndk, ios) -> new CompleteSymbolication(parsedFrames, js, java, ndk, ios));
  }

  private Single<List<String>> symbolicateAllFrames(Lane lane, List<? extends Frame> frames, EventMeta meta, String rawReport) {
    if (frames.isEmpty()) {
      return Single.just(Collections.emptyList());
    }
    return symbolicate(lane, new ArrayList<>(frames), meta, rawReport, lane == Lane.IOS_NATIVE);
  }

  public record ProcessingResult(Group group, CompleteSymbolication completeSymbolication) {
  }
}
