package org.dreamhorizon.pulseserver.errorgrouping;

import com.android.tools.r8.retrace.ProguardMappingSupplier;
import com.android.tools.r8.retrace.Retrace;
import com.android.tools.r8.retrace.RetraceCommand;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.debugging.sourcemap.SourceMapConsumerV3;
import com.google.debugging.sourcemap.proto.Mapping;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.Vertx;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.grouping.model.EventMeta;
import org.dreamhorizon.pulseserver.grouping.model.Frame;
import org.dreamhorizon.pulseserver.grouping.model.JsFrame;
import org.dreamhorizon.pulseserver.grouping.model.NdkFrame;
import org.dreamhorizon.pulseserver.errorgrouping.model.SymbolFileType;
import org.dreamhorizon.pulseserver.errorgrouping.model.UploadMetadata;
import org.dreamhorizon.pulseserver.errorgrouping.service.DsymCache;
import org.dreamhorizon.pulseserver.errorgrouping.service.SourceMapCache;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class Symbolicator {

  private static final String LOG_PREFIX = "[PULSE SYMBOLICATOR]";
  private static final Duration NEGATIVE_CACHE_TTL = Duration.ofMinutes(5);

  private final SourceMapCache sourceMapCache;
  private final DsymCache dsymCache;
  private final IosLlvmSymbolicator iosLlvmSymbolicator;
  private final Vertx vertx;

  /**
   * Short negative cache: {@code false} = recent miss/error for this key (skip S3/DB for a few minutes).
   * {@code true} = load succeeded at least once in this window (optional; invalidation on success is enough).
   */
  private final Cache<String, Boolean> sourceMapExists = Caffeine.newBuilder()
      .expireAfterWrite(NEGATIVE_CACHE_TTL)
      .maximumSize(500)
      .build();

  public String symbolicateNames(JsFrame frame, SourceMapConsumerV3 sourceMap) {
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
    String cacheKey = eventMeta.getPlatform() + ":" + eventMeta.getAppVersion() + ":" + SymbolFileType.JS;

    log.info(
        "{} js start frames={} projectId={} appVersion={} versionCode={} platform={} cacheKey={}",
        LOG_PREFIX,
        jsFrames.size(),
        eventMeta.getProjectId(),
        eventMeta.getAppVersion(),
        eventMeta.getAppVersionCode(),
        eventMeta.getPlatform(),
        cacheKey);

    if (Boolean.FALSE.equals(sourceMapExists.getIfPresent(cacheKey))) {
      log.info("{} js skip negative-cache (5m) cacheKey={}", LOG_PREFIX, cacheKey);
      return Single.just(jsFrames.stream().map(Frame::getToken).toList());
    }

    return sourceMapCache.getSourceMap(UploadMetadata.builder()
            .versionCode(eventMeta.getAppVersionCode())
            .appVersion(eventMeta.getAppVersion())
            .platform(eventMeta.getPlatform())
            .bundleId(eventMeta.getBundleId())
            .projectId(eventMeta.getProjectId())
            .type(SymbolFileType.JS)
            .build())
        .map(sourcemap -> {
          List<String> out = new ArrayList<>(jsFrames.size());
          for (Frame f : jsFrames) {
            String symbolicated = symbolicateNames((JsFrame) f, sourcemap);
            // Update the frame token in-place so the grouping pipeline (Grouper.group)
            // classifies and signs on deobfuscated names rather than minified ones.
            // The returned list still drives CompleteSymbolication reconstruction.
            f.setToken(symbolicated);
            out.add(symbolicated);
          }
          sourceMapExists.put(cacheKey, Boolean.TRUE);
          log.info("{} JS deobfuscation successful: cacheKey={} outputLines={}", LOG_PREFIX, cacheKey, out.size());
          return out;
        })
        .onErrorReturn(error -> {
          sourceMapExists.put(cacheKey, Boolean.FALSE);
          log.warn("JS deobfuscation failed: projectId={}, appVersion={}, versionCode={}, platform={}, frames={}, error={}",
              eventMeta.getProjectId(), eventMeta.getAppVersion(), eventMeta.getAppVersionCode(),
              eventMeta.getPlatform(), jsFrames.size(), error.getMessage());
          return jsFrames.stream().map(Frame::getToken).toList();
        });
  }

  /**
   * Java retrace: plug either Retrace API or CLI. Here we leave tokens if mapping not wired.
   */
  public Single<List<String>> retrace(List<Frame> javaFrames, EventMeta eventMeta) {
    String cacheKey = eventMeta.getPlatform() + ":" + eventMeta.getAppVersion() + ":" + SymbolFileType.MAPPING;

    log.info(
        "{} java start frames={} projectId={} appVersion={} versionCode={} platform={} cacheKey={}",
        LOG_PREFIX,
        javaFrames.size(),
        eventMeta.getProjectId(),
        eventMeta.getAppVersion(),
        eventMeta.getAppVersionCode(),
        eventMeta.getPlatform(),
        cacheKey);

    if (Boolean.FALSE.equals(sourceMapExists.getIfPresent(cacheKey))) {
      log.info("{} java retrace skip negative-cache (5m) cacheKey={}", LOG_PREFIX, cacheKey);
      return Single.just(javaFrames.stream().map(Frame::getToken).toList());
    }

    List<String> out = new ArrayList<>();

    return sourceMapCache.getProguardMap(UploadMetadata.builder()
            .versionCode(eventMeta.getAppVersionCode())
            .appVersion(eventMeta.getAppVersion())
            .platform(eventMeta.getPlatform())
            .projectId(eventMeta.getProjectId())
            .type(SymbolFileType.MAPPING)
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
          // Write back the deobfuscated canonical token onto each input frame
          // so the grouping pipeline (Grouper.group) sees `com.dream11.Foo#bar`
          // instead of `a.b.c#d`. Retrace may expand frames due to inlining,
          // so we walk the output list and update one frame at a time in input
          // order — extra (inlined) lines are kept in `out` for the full-stack
          // reconstruction but ignored for token assignment.
          applyJavaTokens(javaFrames, out);
          sourceMapExists.put(cacheKey, Boolean.TRUE);
          log.info("{} Java deobfuscation successful: cacheKey={}", LOG_PREFIX, cacheKey);
          return out;
        })
        .onErrorReturn(error -> {
          sourceMapExists.put(cacheKey, Boolean.FALSE);
          log.warn("Java deobfuscation failed: projectId={}, appVersion={}, versionCode={}, platform={}, frames={}, error={}",
              eventMeta.getProjectId(), eventMeta.getAppVersion(), eventMeta.getAppVersionCode(),
              eventMeta.getPlatform(), javaFrames.size(), error.getMessage());
          return javaFrames.stream().map(Frame::getToken).toList();
        });
  }

  /**
   * Walk the verbose-retrace output and write back the deobfuscated canonical
   * token ({@code pkg.Class#method}) onto each input frame in input order.
   * Extra inlined-expansion lines in {@code retracedLines} are kept in the
   * output (used by {@code CompleteSymbolication}) but ignored here — they
   * don't have their own input frame to update.
   */
  static void applyJavaTokens(List<Frame> javaFrames, List<String> retracedLines) {
    if (javaFrames == null || retracedLines == null) {
      return;
    }
    int frameIdx = 0;
    for (String line : retracedLines) {
      if (frameIdx >= javaFrames.size()) {
        break;
      }
      String canonical = extractJavaCanonical(line);
      if (canonical != null && !canonical.isEmpty()) {
        javaFrames.get(frameIdx).setToken(canonical);
        frameIdx++;
      }
    }
  }

  /**
   * Pull {@code pkg.Class#method} canonical form out of a single retrace
   * output line. Handles plain ({@code "at com.foo.Bar.method(File.java:42)"})
   * and verbose ({@code "void com.foo.Bar.method(File.java:42)"}) shapes.
   * Returns {@code null} for lines that don't look like frames (e.g.
   * {@code "# {... inlined ...}"} annotations).
   */
  static String extractJavaCanonical(String line) {
    if (line == null) {
      return null;
    }
    String s = line.trim();
    if (s.startsWith("at ")) {
      s = s.substring(3).trim();
    }
    int paren = s.indexOf('(');
    if (paren > 0) {
      s = s.substring(0, paren).trim();
    }
    // Verbose retrace can prefix the FQN with a return type (e.g. "void com.foo.Bar.method").
    // Take just the trailing FQN.
    int lastSpace = s.lastIndexOf(' ');
    if (lastSpace >= 0) {
      s = s.substring(lastSpace + 1);
    }
    int lastDot = s.lastIndexOf('.');
    if (lastDot <= 0 || lastDot >= s.length() - 1) {
      return null;
    }
    String cls = s.substring(0, lastDot);
    String method = s.substring(lastDot + 1);
    // A useful canonical class is package-qualified — bare class names without a dot
    // (e.g. truly unresolvable obfuscated frames retrace leaves alone) get skipped.
    if (!cls.contains(".")) {
      return null;
    }
    return cls + "#" + method;
  }

  /**
   * iOS Mach-O symbolication (crashed thread). {@code stackTraceFormat}: {@code true} = use raw lines
   * when LLVM is skipped; {@code false} = use frame tokens for grouping signatures.
   */
  public Single<List<String>> symbolicateIosNative(
      List<Frame> frames, EventMeta eventMeta, String rawReport, boolean stackTraceFormat) {
    if (frames.isEmpty()) {
      return Single.just(List.of());
    }
    log.info(
        "{} ios_native start frames={} projectId={} appVersion={} versionCode={} platform={} bundleId={} "
            + "stackTraceFormat={}",
        LOG_PREFIX,
        frames.size(),
        eventMeta.getProjectId(),
        eventMeta.getAppVersion(),
        eventMeta.getAppVersionCode(),
        eventMeta.getPlatform(),
        eventMeta.getBundleId(),
        stackTraceFormat);
    List<NdkFrame> ndkFrames = new ArrayList<>(frames.size());
    for (Frame f : frames) {
      ndkFrames.add((NdkFrame) f);
    }
    UploadMetadata dsymKey = UploadMetadata.builder()
        .type(SymbolFileType.DSYM)
        .appVersion(eventMeta.getAppVersion())
        .versionCode(eventMeta.getAppVersionCode())
        .platform(eventMeta.getPlatform())
        .bundleId(eventMeta.getBundleId())
        .projectId(eventMeta.getProjectId())
        .build();

    return dsymCache.getDsym(dsymKey)
        .flatMap(opt -> Single.<List<String>>create(emitter -> {
          int dsymLen =
              opt.filter(b -> b != null && b.length > 0).map(b -> b.length).orElse(0);
          log.info(
              "{} ios_native dsym cacheHit={} dsymBytes={}",
              LOG_PREFIX,
              opt.isPresent() && dsymLen > 0,
              dsymLen);
          vertx.getDelegate().<List<String>>executeBlocking(
              promise -> {
                try {
                  byte[] bytes = opt.filter(b -> b != null && b.length > 0).orElse(null);
                  List<String> lines = iosLlvmSymbolicator.symbolicateFrames(
                      ndkFrames, rawReport == null ? "" : rawReport, bytes);
                  int llvmChanged = 0;
                  for (int i = 0; i < lines.size() && i < ndkFrames.size(); i++) {
                    if (!lines.get(i).equals(ndkFrames.get(i).getRawLine())) {
                      llvmChanged++;
                    }
                  }
                  log.info(
                      "{} ios_native llvm rawLinesChanged={} totalFrames={}",
                      LOG_PREFIX,
                      llvmChanged,
                      ndkFrames.size());
                  promise.complete(mapIosNativeOutput(ndkFrames, lines, stackTraceFormat));
                } catch (Exception e) {
                  promise.fail(e);
                }
              },
              false,
              ar -> {
                if (ar.succeeded()) {
                  emitter.onSuccess(ar.result());
                } else {
                  emitter.onError(ar.cause());
                }
              });
        }))
        .onErrorReturn(error -> {
          log.warn(
              "{} ios_native failed projectId={} appVersion={}",
              LOG_PREFIX,
              eventMeta.getProjectId(),
              eventMeta.getAppVersion(),
              error);
          return fallbackIosNative(ndkFrames, stackTraceFormat);
        });
  }

  private static List<String> mapIosNativeOutput(
      List<NdkFrame> frames, List<String> lines, boolean stackTraceFormat) {
    List<String> out = new ArrayList<>(frames.size());
    for (int i = 0; i < frames.size(); i++) {
      String line = i < lines.size() ? lines.get(i) : frames.get(i).getRawLine();
      if (!stackTraceFormat && line.equals(frames.get(i).getRawLine())) {
        out.add(frames.get(i).getToken());
      } else {
        out.add(line);
      }
    }
    return out;
  }

  private static List<String> fallbackIosNative(List<NdkFrame> frames, boolean stackTraceFormat) {
    return frames.stream()
        .map(f -> stackTraceFormat ? f.getRawLine() : f.getToken())
        .toList();
  }
}
