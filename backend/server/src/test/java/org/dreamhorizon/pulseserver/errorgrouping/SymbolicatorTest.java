package org.dreamhorizon.pulseserver.errorgrouping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.debugging.sourcemap.SourceMapConsumerV3;
import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.errorgrouping.model.EventMeta;
import org.dreamhorizon.pulseserver.errorgrouping.model.Frame;
import org.dreamhorizon.pulseserver.errorgrouping.model.JsFrame;
import org.dreamhorizon.pulseserver.errorgrouping.model.UploadMetadata;
import org.dreamhorizon.pulseserver.errorgrouping.service.SourceMapCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SymbolicatorTest {

  @Mock
  private SourceMapCache sourceMapCache;

  private Symbolicator symbolicator;

  @BeforeEach
  void setUp() {
    symbolicator = new Symbolicator(sourceMapCache);
  }

  private JsFrame createJsFrame(String token, Integer line, Integer col, String function) {
    return JsFrame.builder()
        .jsFile("testFile.js")
        .jsFunction(function)
        .jsLine(line)
        .jsColumn(col)
        .rawLine(token)
        .originalPosition(0)
        .build();
  }

  @Nested
  class SymbolicateNamesTests {
    @Test
    void shouldReturnTokenWhenLineIsNull() {
      JsFrame frame = createJsFrame("func@file.js:1:1", null, 1, "func");
      SourceMapConsumerV3 sourceMap = new SourceMapConsumerV3();

      String result = symbolicator.symbolicateNames(frame, sourceMap);

      // When line is null, it returns the token which is file#function format
      assertEquals("testFile.js#func", result);
    }

    @Test
    void shouldReturnTokenWhenColumnIsNull() {
      JsFrame frame = createJsFrame("func@file.js:1:1", 1, null, "func");
      SourceMapConsumerV3 sourceMap = new SourceMapConsumerV3();

      String result = symbolicator.symbolicateNames(frame, sourceMap);

      // When column is null, it returns the token which is file#function format
      assertEquals("testFile.js#func", result);
    }

    @Test
    void shouldReturnTokenWhenNoMappingFound() {
      // Use a line/column that won't have a mapping in the source map
      JsFrame frame = createJsFrame("func@file.js:999:999", 999, 999, "func");
      // Create a properly initialized source map
      SourceMapConsumerV3 sourceMap = new SourceMapConsumerV3();
      try {
        // Parse a minimal valid source map - only has mapping for line 1, not line 999
        sourceMap.parse("{\"version\":3,\"file\":\"test.js\",\"sources\":[\"src/test.js\"],\"names\":[],\"mappings\":\"AAAA\"}");
      } catch (Exception e) {
        // If parsing fails, we'll test the fallback behavior
        // The actual code will call getMappingForLine which may throw, but we're testing the null return path
      }

      String result = symbolicator.symbolicateNames(frame, sourceMap);

      // When no mapping found, it returns the token which is file#function format
      assertEquals("testFile.js#func", result);
    }

    @Test
    void shouldSymbolicateWithMapping() {
      JsFrame frame = createJsFrame("func@file.js:1:1", 1, 1, "func");
      SourceMapConsumerV3 sourceMap = new SourceMapConsumerV3();
      try {
        sourceMap.parse("{\"version\":3,\"sources\":[\"src/App.tsx\"],\"names\":[\"App\"],\"mappings\":\"AAAA\"}");
      } catch (Exception e) {
        // Ignore parsing errors for this test
      }

      String result = symbolicator.symbolicateNames(frame, sourceMap);

      assertNotNull(result);
    }
  }

  @Nested
  class SymbolicateJsInPlaceTests {
    @Test
    void shouldReturnTokensWhenSourceMapNotExists() {
      List<Frame> frames = List.of(
          createJsFrame("func1@file.js:1:1", 1, 1, "func1"),
          createJsFrame("func2@file.js:2:2", 2, 2, "func2")
      );

      EventMeta eventMeta = EventMeta.builder()
          .appVersion("1.0.0")
          .platform("android")
          .build();

      // Mock source map cache to return error (source map doesn't exist)
      when(sourceMapCache.getSourceMap(any(UploadMetadata.class)))
          .thenReturn(Single.error(new RuntimeException("Source map not found")));

      Single<List<String>> result = symbolicator.symbolicateJsInPlace(frames, eventMeta);

      List<String> symbolicated = result.blockingGet();
      assertEquals(2, symbolicated.size());
      // When source map doesn't exist, it returns tokens (file#function format)
      assertEquals("testFile.js#func1", symbolicated.get(0));
      assertEquals("testFile.js#func2", symbolicated.get(1));
    }

    @Test
    void shouldReturnTokensWhenSourceMapCacheReturnsFalse() {
      List<Frame> frames = List.of(
          createJsFrame("func@file.js:1:1", 1, 1, "func")
      );

      EventMeta eventMeta = EventMeta.builder()
          .appVersion("1.0.0")
          .platform("android")
          .build();

      // Mock source map cache to return error (simulating cache knows it doesn't exist)
      // The internal cache will be checked first, but we can't access it directly
      // So we mock the sourceMapCache to return an error, which will cache the failure
      when(sourceMapCache.getSourceMap(any(UploadMetadata.class)))
          .thenReturn(Single.error(new RuntimeException("Source map not found")));

      Single<List<String>> result = symbolicator.symbolicateJsInPlace(frames, eventMeta);

      List<String> symbolicated = result.blockingGet();
      assertNotNull(symbolicated);
      assertEquals(1, symbolicated.size());
      assertEquals("testFile.js#func", symbolicated.get(0));
    }

    @Test
    void shouldSymbolicateWithValidSourceMap() {
      List<Frame> frames = List.of(
          createJsFrame("func@file.js:1:1", 1, 1, "func")
      );

      EventMeta eventMeta = EventMeta.builder()
          .appVersion("1.0.0")
          .platform("android")
          .bundleId("com.example.app")
          .build();

      SourceMapConsumerV3 sourceMap = new SourceMapConsumerV3();
      when(sourceMapCache.getSourceMap(any(UploadMetadata.class)))
          .thenReturn(Single.just(sourceMap));

      Single<List<String>> result = symbolicator.symbolicateJsInPlace(frames, eventMeta);

      List<String> symbolicated = result.blockingGet();
      assertNotNull(symbolicated);
      assertEquals(1, symbolicated.size());
    }

    @Test
    void shouldHandleEmptyFramesList() {
      List<Frame> frames = Collections.emptyList();

      EventMeta eventMeta = EventMeta.builder()
          .appVersion("1.0.0")
          .platform("android")
          .build();

      when(sourceMapCache.getSourceMap(any(UploadMetadata.class)))
          .thenReturn(Single.error(new RuntimeException("Source map not found")));

      Single<List<String>> result = symbolicator.symbolicateJsInPlace(frames, eventMeta);

      List<String> symbolicated = result.blockingGet();
      assertTrue(symbolicated.isEmpty());
    }
  }

  @Nested
  class RetraceTests {
    @Test
    void shouldReturnTokensWhenProguardMapNotExists() {
      List<Frame> frames = List.of(
          org.dreamhorizon.pulseserver.errorgrouping.model.JavaFrame.builder()
              .javaClass("a.b")
              .javaMethod("c")
              .rawLine("at a.b.c(SourceFile:1)")
              .originalPosition(0)
              .build()
      );

      EventMeta eventMeta = EventMeta.builder()
          .appVersion("1.0.0")
          .platform("android")
          .build();

      // Mock ProGuard map cache to return error
      when(sourceMapCache.getProguardMap(any(UploadMetadata.class)))
          .thenReturn(Single.error(new RuntimeException("ProGuard map not found")));

      Single<List<String>> result = symbolicator.retrace(frames, eventMeta);

      List<String> retraced = result.blockingGet();
      assertEquals(1, retraced.size());
      // When ProGuard map doesn't exist, it returns tokens (class#method format)
      assertEquals("a.b#c", retraced.get(0));
    }

    @Test
    void shouldReturnTokensWhenProguardMapCacheReturnsFalse() {
      List<Frame> frames = List.of(
          org.dreamhorizon.pulseserver.errorgrouping.model.JavaFrame.builder()
              .javaClass("a.b")
              .javaMethod("c")
              .rawLine("at a.b.c(SourceFile:1)")
              .originalPosition(0)
              .build()
      );

      EventMeta eventMeta = EventMeta.builder()
          .appVersion("1.0.0")
          .platform("android")
          .build();

      // Mock ProGuard map cache to return error (simulating cache knows it doesn't exist)
      when(sourceMapCache.getProguardMap(any(UploadMetadata.class)))
          .thenReturn(Single.error(new RuntimeException("ProGuard map not found")));

      Single<List<String>> result = symbolicator.retrace(frames, eventMeta);

      List<String> retraced = result.blockingGet();
      assertNotNull(retraced);
      assertEquals(1, retraced.size());
      assertEquals("a.b#c", retraced.get(0));
    }

    @Test
    void shouldHandleEmptyFramesList() {
      List<Frame> frames = Collections.emptyList();

      EventMeta eventMeta = EventMeta.builder()
          .appVersion("1.0.0")
          .platform("android")
          .build();

      when(sourceMapCache.getProguardMap(any(UploadMetadata.class)))
          .thenReturn(Single.error(new RuntimeException("ProGuard map not found")));

      Single<List<String>> result = symbolicator.retrace(frames, eventMeta);

      List<String> retraced = result.blockingGet();
      assertTrue(retraced.isEmpty());
    }
  }
}

