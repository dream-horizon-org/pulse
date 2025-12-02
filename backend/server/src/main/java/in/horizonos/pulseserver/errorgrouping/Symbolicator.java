package in.horizonos.pulseserver.errorgrouping;

import com.android.tools.r8.retrace.ProguardMappingSupplier;
import com.android.tools.r8.retrace.Retrace;
import com.android.tools.r8.retrace.RetraceCommand;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.debugging.sourcemap.SourceMapConsumerV3;
import com.google.debugging.sourcemap.proto.Mapping;
import com.google.inject.Inject;
import in.horizonos.pulseserver.errorgrouping.model.EventMeta;
import in.horizonos.pulseserver.errorgrouping.model.Frame;
import in.horizonos.pulseserver.errorgrouping.model.JSFrame;
import in.horizonos.pulseserver.errorgrouping.model.UploadMetadata;
import in.horizonos.pulseserver.errorgrouping.service.SourceMapCache;
import io.reactivex.rxjava3.core.Single;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class Symbolicator {

  private final SourceMapCache sourceMapCache;

  // OPTIMIZATION: Circuit breaker - cache which versions don't have source maps to fail fast
  private final Cache<String, Boolean> sourceMapExists = Caffeine.newBuilder()
      .expireAfterWrite(Duration.ofMinutes(5))
      .maximumSize(500)
      .build();

  public String symbolicateNames(JSFrame frame, SourceMapConsumerV3 sourceMap) {
    // RN/Metro/Hermes report 1-based line & column; SourceMap expects 1-based too.
    Integer line = frame.getJsLine();
    Integer col = frame.getJsColumn();

    if (line == null || col == null) {
      // Not enough info; pass through unchanged.
      return frame.getToken();
    }

    Mapping.OriginalMapping om = sourceMap.getMappingForLine(line, col);
    if (om == null) {
      // No mapping → return original
      return frame.getToken();
    }

    // Prefer the identifier from the map; fall back to parsed function name.
    String function = nz(om.getIdentifier(), safeName(frame.getJsFunction()));
    String file = om.getOriginalFile(); // usually a relative path like "src/screens/Home.tsx"
    return String.join("#", file, function);
  }

  private String nz(String a, String b) {
    return (a == null || a.isBlank()) ? b : a;
  }

  private String safeName(String s) {
    return (s == null || s.isBlank()) ? "<anonymous>" : s;
  }


  /**
   * JS symbolication using a Source Map (Closure Tools).
   */
  @SneakyThrows
  public Single<List<String>> symbolicateJsInPlace(List<Frame> jsFrames, EventMeta eventMeta) {
    String cacheKey = eventMeta.getPlatform() + ":" + eventMeta.getAppVersion() + ":JS";

    // OPTIMIZATION: Fast path - check if we already know source map doesn't exist
    if (Boolean.FALSE.equals(sourceMapExists.getIfPresent(cacheKey))) {
      return Single.just(jsFrames.stream().map(Frame::getToken).toList());
    }

    return sourceMapCache.getSourceMap(UploadMetadata.builder()
            .versionCode(eventMeta.getAppVersionCode())
            .appVersion(eventMeta.getAppVersion())
            .platform(eventMeta.getPlatform())
            .type("JS")
            .build())
        .map(sourcemap -> {
          List<String> out = new ArrayList<>(jsFrames.size());
          for (Frame f : jsFrames) {
            out.add(symbolicateNames((JSFrame) f, sourcemap));
          }
          sourceMapExists.put(cacheKey, true);  // Cache success
          return out;
        })
        .onErrorReturn(error -> {
          sourceMapExists.put(cacheKey, false);  // Cache failure
          return jsFrames.stream().map(Frame::getToken).toList();
        });
  }

  /**
   * Java retrace: plug either Retrace API or CLI. Here we leave tokens if mapping not wired.
   */
  public Single<List<String>> retrace(List<Frame> javaFrames, EventMeta eventMeta) {
    String cacheKey = eventMeta.getPlatform() + ":" + eventMeta.getAppVersion() + ":JAVA";

    // OPTIMIZATION: Fast path - check if we already know ProGuard map doesn't exist
    if (Boolean.FALSE.equals(sourceMapExists.getIfPresent(cacheKey))) {
      return Single.just(javaFrames.stream().map(Frame::getToken).toList());
    }

    List<String> out = new ArrayList<>();

    return sourceMapCache.getProguardMap(UploadMetadata.builder()
            .versionCode(eventMeta.getAppVersionCode())
            .appVersion(eventMeta.getAppVersion())
            .platform(eventMeta.getPlatform())
            .type("JAVA")
            .build())
        .map(proguardMapProducer -> {
          Retrace.run(
              RetraceCommand.builder()
                  .setMappingSupplier(ProguardMappingSupplier.builder()
                      .setProguardMapProducer(proguardMapProducer)
                      .setLoadAllDefinitions(false) // lazy load for speed
                      .build())
                  .setStackTrace(javaFrames.stream().map(Frame::getRawLine).toList())
                  .setVerbose(true)
                  .setRetracedStackTraceConsumer(out::addAll)
                  .build());
          sourceMapExists.put(cacheKey, true);  // Cache success
          return out;
        })
        .onErrorReturn(error -> {
          sourceMapExists.put(cacheKey, false);  // Cache failure
          return javaFrames.stream().map(Frame::getToken).toList();
        });
  }
}
