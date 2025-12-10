package org.dreamhorizon.pulseserver.errorgrouping.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import io.opentelemetry.proto.resource.v1.Resource;
import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.errorgrouping.Symbolicator;
import org.dreamhorizon.pulseserver.errorgrouping.model.EventMeta;
import org.dreamhorizon.pulseserver.errorgrouping.model.Frame;
import org.dreamhorizon.pulseserver.errorgrouping.model.JavaFrame;
import org.dreamhorizon.pulseserver.errorgrouping.model.JsFrame;
import org.dreamhorizon.pulseserver.errorgrouping.model.Lane;
import org.dreamhorizon.pulseserver.errorgrouping.model.NdkFrame;
import org.dreamhorizon.pulseserver.errorgrouping.model.ParsedFrames;
import org.dreamhorizon.pulseserver.errorgrouping.model.StackTraceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ErrorGroupingServiceTest {

  @Mock
  private ClickhouseQueryService clickhouseQueryService;

  @Mock
  private Symbolicator symbolicator;

  private ErrorGroupingService errorGroupingService;

  @BeforeEach
  void setUp() {
    errorGroupingService = new ErrorGroupingService(clickhouseQueryService, symbolicator);
  }

  // Helper methods to create test data
  private JsFrame createJsFrame(int position) {
    return createJsFrame(position, true);
  }

  private JsFrame createJsFrame(int position, boolean inApp) {
    JsFrame frame = JsFrame.builder()
        .jsFile("testFile.js")
        .jsFunction("testFunc")
        .jsLine(10)
        .jsColumn(5)
        .rawLine("testFunc@testFile.js:10:5")
        .originalPosition(position)
        .build();
    // Override inApp if needed for testing
    if (!inApp) {
      frame.setInApp(false);
    }
    return frame;
  }

  private JavaFrame createJavaFrame(int position) {
    return JavaFrame.builder()
        .javaClass("com.test.TestClass")
        .javaMethod("testMethod")
        .javaFile("TestClass.java")
        .javaLine(10)
        .rawLine("at com.test.TestClass.testMethod(TestClass.java:10)")
        .originalPosition(position)
        .build();
  }

  private NdkFrame createNdkFrame(int position) {
    return NdkFrame.builder()
        .ndkLib("libnative.so")
        .ndkPc("0x1234")
        .ndkSymbol("nativeFunc")
        .rawLine("libnative.so+0x1234")
        .originalPosition(position)
        .build();
  }

  @Nested
  class TraceIdHexTests {
    @Test
    void shouldConvertValidTraceIdToHex() {
      byte[] bytes = new byte[16];
      for (int i = 0; i < 16; i++) {
        bytes[i] = (byte) i;
      }
      ByteString bs = ByteString.copyFrom(bytes);

      String result = ErrorGroupingService.traceIdHex(bs);

      assertEquals("000102030405060708090a0b0c0d0e0f", result);
    }

    @Test
    void shouldReturnNullForEmptyByteString() {
      assertNull(ErrorGroupingService.traceIdHex(ByteString.EMPTY));
    }

    @Test
    void shouldReturnNullForNullByteString() {
      assertNull(ErrorGroupingService.traceIdHex(null));
    }

    @Test
    void shouldReturnNullForInvalidLength() {
      ByteString bs = ByteString.copyFrom(new byte[8]); // Invalid length
      assertNull(ErrorGroupingService.traceIdHex(bs));
    }
  }

  @Nested
  class SpanIdHexTests {
    @Test
    void shouldConvertValidSpanIdToHex() {
      byte[] bytes = new byte[8];
      for (int i = 0; i < 8; i++) {
        bytes[i] = (byte) i;
      }
      ByteString bs = ByteString.copyFrom(bytes);

      String result = ErrorGroupingService.spanIdHex(bs);

      assertEquals("0001020304050607", result);
    }

    @Test
    void shouldReturnNullForEmptyByteString() {
      assertNull(ErrorGroupingService.spanIdHex(ByteString.EMPTY));
    }

    @Test
    void shouldReturnNullForInvalidLength() {
      ByteString bs = ByteString.copyFrom(new byte[16]); // Invalid length
      assertNull(ErrorGroupingService.spanIdHex(bs));
    }
  }

  @Nested
  class ChoosePrimaryTests {
    @Test
    void shouldReturnUnknownWhenNoFrames() {
      ParsedFrames parsed = ParsedFrames.builder()
          .jsFrames(Collections.emptyList())
          .javaFrames(Collections.emptyList())
          .ndkFrames(Collections.emptyList())
          .build();

      Lane result = ErrorGroupingService.choosePrimary(parsed);

      assertEquals(Lane.UNKNOWN, result);
    }

    @Test
    void shouldPrioritizeJsWhenPrimaryExceptionLaneIsJs() {
      ParsedFrames parsed = ParsedFrames.builder()
          .primaryExceptionLane(Lane.JS)
          .jsFrames(List.of(createJsFrame(0)))
          .javaFrames(List.of(createJavaFrame(1), createJavaFrame(2)))
          .ndkFrames(Collections.emptyList())
          .build();

      Lane result = ErrorGroupingService.choosePrimary(parsed);

      assertEquals(Lane.JS, result);
    }

    @Test
    void shouldPrioritizeJavaWhenPrimaryExceptionLaneIsJava() {
      ParsedFrames parsed = ParsedFrames.builder()
          .primaryExceptionLane(Lane.JAVA)
          .jsFrames(Collections.emptyList())
          .javaFrames(List.of(createJavaFrame(0)))
          .ndkFrames(Collections.emptyList())
          .build();

      Lane result = ErrorGroupingService.choosePrimary(parsed);

      assertEquals(Lane.JAVA, result);
    }

    @Test
    void shouldFallbackToFrameCountWhenNoExceptionLane() {
      ParsedFrames parsed = ParsedFrames.builder()
          .primaryExceptionLane(null)
          .jsFrames(List.of(createJsFrame(0), createJsFrame(1)))
          .javaFrames(List.of(createJavaFrame(2)))
          .ndkFrames(Collections.emptyList())
          .build();

      Lane result = ErrorGroupingService.choosePrimary(parsed);

      assertEquals(Lane.JS, result);
    }

    @Test
    void shouldReturnJavaWhenMoreJavaFrames() {
      ParsedFrames parsed = ParsedFrames.builder()
          .primaryExceptionLane(null)
          .jsFrames(List.of(createJsFrame(0)))
          .javaFrames(List.of(createJavaFrame(1), createJavaFrame(2), createJavaFrame(3)))
          .ndkFrames(Collections.emptyList())
          .build();

      Lane result = ErrorGroupingService.choosePrimary(parsed);

      assertEquals(Lane.JAVA, result);
    }

    @Test
    void shouldReturnNdkWhenOnlyNdkFrames() {
      ParsedFrames parsed = ParsedFrames.builder()
          .primaryExceptionLane(null)
          .jsFrames(Collections.emptyList())
          .javaFrames(Collections.emptyList())
          .ndkFrames(List.of(createNdkFrame(0)))
          .build();

      Lane result = ErrorGroupingService.choosePrimary(parsed);

      assertEquals(Lane.NDK, result);
    }

    @Test
    void shouldPreferJsOverJavaOnTie() {
      ParsedFrames parsed = ParsedFrames.builder()
          .primaryExceptionLane(null)
          .jsFrames(List.of(createJsFrame(0), createJsFrame(1)))
          .javaFrames(List.of(createJavaFrame(2), createJavaFrame(3)))
          .ndkFrames(Collections.emptyList())
          .build();

      Lane result = ErrorGroupingService.choosePrimary(parsed);

      assertEquals(Lane.JS, result);
    }

    @Test
    void shouldIgnoreExceptionLaneIfNoFramesForThatLane() {
      // Primary exception is JS but no JS frames available
      ParsedFrames parsed = ParsedFrames.builder()
          .primaryExceptionLane(Lane.JS)
          .jsFrames(Collections.emptyList())
          .javaFrames(List.of(createJavaFrame(0)))
          .ndkFrames(Collections.emptyList())
          .build();

      Lane result = ErrorGroupingService.choosePrimary(parsed);

      assertEquals(Lane.JAVA, result); // Falls back to frame count
    }
  }

  @Nested
  class TypesForPrimaryTests {
    @Test
    void shouldReturnJsTypesForJsLane() {
      List<String> jsTypes = List.of("Error", "TypeError");
      ParsedFrames parsed = ParsedFrames.builder()
          .jsTypes(jsTypes)
          .javaTypes(List.of("JavaException"))
          .ndkTypes(Collections.emptyList())
          .build();

      List<String> result = ErrorGroupingService.typesForPrimary(parsed, Lane.JS);

      assertEquals(jsTypes, result);
    }

    @Test
    void shouldReturnJavaTypesForJavaLane() {
      List<String> javaTypes = List.of("NullPointerException", "IllegalStateException");
      ParsedFrames parsed = ParsedFrames.builder()
          .jsTypes(Collections.emptyList())
          .javaTypes(javaTypes)
          .ndkTypes(Collections.emptyList())
          .build();

      List<String> result = ErrorGroupingService.typesForPrimary(parsed, Lane.JAVA);

      assertEquals(javaTypes, result);
    }

    @Test
    void shouldFallbackToJsTypesWhenPrimaryLaneHasNoTypes() {
      List<String> jsTypes = List.of("Error");
      ParsedFrames parsed = ParsedFrames.builder()
          .jsTypes(jsTypes)
          .javaTypes(Collections.emptyList())
          .ndkTypes(Collections.emptyList())
          .build();

      List<String> result = ErrorGroupingService.typesForPrimary(parsed, Lane.JAVA);

      assertEquals(jsTypes, result);
    }

    @Test
    void shouldReturnEmptyListWhenNoTypes() {
      ParsedFrames parsed = ParsedFrames.builder()
          .jsTypes(Collections.emptyList())
          .javaTypes(Collections.emptyList())
          .ndkTypes(Collections.emptyList())
          .ndkFrames(Collections.emptyList())
          .build();

      List<String> result = ErrorGroupingService.typesForPrimary(parsed, Lane.UNKNOWN);

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class SelectPrimaryTokensTests {
    @Test
    void shouldSelectTopNInAppFrames() {
      List<JsFrame> frames = List.of(
          createJsFrame(0, true),   // in-app
          createJsFrame(1, false),  // not in-app
          createJsFrame(2, true),   // in-app
          createJsFrame(3, true)    // in-app
      );
      ParsedFrames parsed = ParsedFrames.builder()
          .jsFrames(frames)
          .build();

      List<Frame> result = ErrorGroupingService.selectPrimaryTokens(parsed, Lane.JS, 2);

      assertEquals(2, result.size());
      assertTrue(result.get(0).isInApp());
      assertTrue(result.get(1).isInApp());
      assertEquals(0, result.get(0).getOriginalPosition());
      assertEquals(2, result.get(1).getOriginalPosition());
    }

    @Test
    void shouldFallbackToAllFramesWhenNoInAppFrames() {
      List<JsFrame> frames = List.of(
          createJsFrame(0, false),
          createJsFrame(1, false),
          createJsFrame(2, false)
      );
      ParsedFrames parsed = ParsedFrames.builder()
          .jsFrames(frames)
          .build();

      List<Frame> result = ErrorGroupingService.selectPrimaryTokens(parsed, Lane.JS, 2);

      assertEquals(2, result.size());
      assertFalse(result.get(0).isInApp());
      assertFalse(result.get(1).isInApp());
    }

    @Test
    void shouldReturnEmptyListForUnknownLane() {
      ParsedFrames parsed = ParsedFrames.builder()
          .jsFrames(List.of(createJsFrame(0)))
          .build();

      List<Frame> result = ErrorGroupingService.selectPrimaryTokens(parsed, Lane.UNKNOWN, 5);

      assertTrue(result.isEmpty());
    }

    @Test
    void shouldLimitResultsToTopN() {
      List<JsFrame> frames = List.of(
          createJsFrame(0, true),
          createJsFrame(1, true),
          createJsFrame(2, true),
          createJsFrame(3, true),
          createJsFrame(4, true)
      );
      ParsedFrames parsed = ParsedFrames.builder()
          .jsFrames(frames)
          .build();

      List<Frame> result = ErrorGroupingService.selectPrimaryTokens(parsed, Lane.JS, 3);

      assertEquals(3, result.size());
    }
  }

  @Nested
  class BuildSignatureTests {
    @Test
    void shouldBuildSignatureWithAllComponents() {
      String platform = "js";
      List<String> excTypes = List.of("Error", "TypeError");
      List<String> tokens = List.of("func1@file1:10:5", "func2@file2:20:10");

      String result = ErrorGroupingService.buildSignature(platform, excTypes, tokens);

      assertEquals("v1|platform:js|exc:Error>TypeError|frames:func1@file1:10:5>func2@file2:20:10", result);
    }

    @Test
    void shouldBuildSignatureWithEmptyTypes() {
      String platform = "java";
      List<String> excTypes = Collections.emptyList();
      List<String> tokens = List.of("Class.method(File.java:10)");

      String result = ErrorGroupingService.buildSignature(platform, excTypes, tokens);

      assertEquals("v1|platform:java|exc:|frames:Class.method(File.java:10)", result);
    }

    @Test
    void shouldBuildSignatureWithEmptyTokens() {
      String platform = "ndk";
      List<String> excTypes = List.of("SIGSEGV");
      List<String> tokens = Collections.emptyList();

      String result = ErrorGroupingService.buildSignature(platform, excTypes, tokens);

      assertEquals("v1|platform:ndk|exc:SIGSEGV|frames:", result);
    }

    @Test
    void shouldBuildSignatureWithSingleValues() {
      String platform = "js";
      List<String> excTypes = List.of("Error");
      List<String> tokens = List.of("main@index.js:1:1");

      String result = ErrorGroupingService.buildSignature(platform, excTypes, tokens);

      assertEquals("v1|platform:js|exc:Error|frames:main@index.js:1:1", result);
    }
  }

  @Nested
  class BuildDisplayNameTests {
    @Test
    void shouldBuildDisplayNameForJavaWithSingleException() {
      List<String> excTypes = List.of("NullPointerException");
      List<String> frames = List.of("at com.example.Class.method(Class.java:10)");

      String result = ErrorGroupingService.buildDisplayName(Lane.JAVA, excTypes, frames, "EXC-123ABC");

      assertEquals("NullPointerException at method(Class.java:10) [EXC-123ABC]", result);
    }

    @Test
    void shouldBuildDisplayNameForJavaWithCausedBy() {
      List<String> excTypes = List.of("RuntimeException", "NullPointerException");
      List<String> frames = List.of("com.example.Class.method(Class.java:10)");

      String result = ErrorGroupingService.buildDisplayName(Lane.JAVA, excTypes, frames, "EXC-456DEF");

      assertEquals("RuntimeException caused by NullPointerException at method(Class.java:10) [EXC-456DEF]", result);
    }

    @Test
    void shouldBuildDisplayNameForJsError() {
      List<String> excTypes = List.of("TypeError");
      List<String> frames = List.of("myFunction@app/utils/helper.js:42:15");

      String result = ErrorGroupingService.buildDisplayName(Lane.JS, excTypes, frames, "EXC-789GHI");

      assertEquals("TypeError in utils/helper.js:42:15 [EXC-789GHI]", result);
    }

    @Test
    void shouldBuildDisplayNameForNdkError() {
      List<String> excTypes = Collections.emptyList();
      List<String> frames = List.of("libnative.so+0x1234");

      String result = ErrorGroupingService.buildDisplayName(Lane.NDK, excTypes, frames, "EXC-ABC123");

      assertEquals("NativeError at libnative.so+0x1234 [EXC-ABC123]", result);
    }

    @Test
    void shouldHandleEmptyFrames() {
      List<String> excTypes = List.of("Error");
      List<String> frames = Collections.emptyList();

      String result = ErrorGroupingService.buildDisplayName(Lane.JS, excTypes, frames, "EXC-XYZ999");

      assertEquals("Error [EXC-XYZ999]", result);
    }

    @Test
    void shouldHandleEmptyTypesForJava() {
      List<String> excTypes = Collections.emptyList();
      List<String> frames = List.of("Class.method(File.java:10)");

      String result = ErrorGroupingService.buildDisplayName(Lane.JAVA, excTypes, frames, "EXC-111222");

      assertEquals("Error at method(File.java:10) [EXC-111222]", result);
    }
  }

  @Nested
  class ProcessWithCompleteSymbolicationTests {
    @Test
    void shouldProcessReactNativeJsException() {
      String stackTrace = "Error: Test error\n" +
          "    at func1 (index.bundle:1:1000)\n" +
          "    at func2 (index.bundle:1:2000)";

      EventMeta meta = EventMeta.builder()
          .appVersion("1.0.0")
          .platform("android")
          .build();

      // Mock symbolication responses
      when(symbolicator.symbolicateJsInPlace(anyList(), eq(meta)))
          .thenReturn(Single.just(List.of("func1@app.js:10:5", "func2@app.js:20:10")));

      lenient().when(symbolicator.retrace(anyList(), eq(meta)))
          .thenReturn(Single.just(Collections.emptyList()));

      Single<ErrorGroupingService.ProcessingResult> result = errorGroupingService.processWithCompleteSymbolication(stackTrace, meta);

      ErrorGroupingService.ProcessingResult processingResult = result.blockingGet();
      assertNotNull(processingResult);
      assertNotNull(processingResult.group());
      assertEquals("js", processingResult.group().getPlatform());
      assertTrue(processingResult.group().getGroupId().startsWith("EXC-"));
    }

    @Test
    void shouldProcessPureJavaException() {
      String stackTrace = "java.lang.NullPointerException: Null value\n" +
          "    at com.example.Class.method(Class.java:10)\n" +
          "    at com.example.Main.run(Main.java:20)";

      EventMeta meta = EventMeta.builder()
          .appVersion("1.0.0")
          .platform("android")
          .build();

      when(symbolicator.retrace(anyList(), eq(meta)))
          .thenReturn(Single.just(List.of(
              "com.example.Class.method(Class.java:10)",
              "com.example.Main.run(Main.java:20)"
          )));

      lenient().when(symbolicator.symbolicateJsInPlace(anyList(), eq(meta)))
          .thenReturn(Single.just(Collections.emptyList()));

      Single<ErrorGroupingService.ProcessingResult> result = errorGroupingService.processWithCompleteSymbolication(stackTrace, meta);

      ErrorGroupingService.ProcessingResult processingResult = result.blockingGet();
      assertNotNull(processingResult);
      assertEquals("java", processingResult.group().getPlatform());
      assertTrue(processingResult.group().getSignature().contains("NullPointerException"));
    }

    @Test
    void shouldHandleEmptyStackTrace() {
      EventMeta meta = EventMeta.builder()
          .appVersion("1.0.0")
          .platform("android")
          .build();

      lenient().when(symbolicator.symbolicateJsInPlace(anyList(), eq(meta)))
          .thenReturn(Single.just(Collections.emptyList()));
      lenient().when(symbolicator.retrace(anyList(), eq(meta)))
          .thenReturn(Single.just(Collections.emptyList()));

      Single<ErrorGroupingService.ProcessingResult> result = errorGroupingService.processWithCompleteSymbolication("", meta);

      ErrorGroupingService.ProcessingResult processingResult = result.blockingGet();
      assertNotNull(processingResult);
      assertEquals("unknown", processingResult.group().getPlatform());
    }
  }

  @Nested
  class ProcessLogsTests {
    @Test
    void shouldProcessOtelLogRequest() {
      // Build OTEL request
      LogRecord logRecord = LogRecord.newBuilder()
          .setObservedTimeUnixNano(System.currentTimeMillis() * 1_000_000)
          .addAttributes(KeyValue.newBuilder()
              .setKey("exception.stacktrace")
              .setValue(AnyValue.newBuilder().setStringValue("Error: Test\n    at func@file.js:1:1").build())
              .build())
          .addAttributes(KeyValue.newBuilder()
              .setKey("exception.message")
              .setValue(AnyValue.newBuilder().setStringValue("Test error").build())
              .build())
          .addAttributes(KeyValue.newBuilder()
              .setKey("exception.type")
              .setValue(AnyValue.newBuilder().setStringValue("Error").build())
              .build())
          .addAttributes(KeyValue.newBuilder()
              .setKey("pulse.type")
              .setValue(AnyValue.newBuilder().setStringValue("crash").build())
              .build())
          .build();

      Resource resource = Resource.newBuilder()
          .addAttributes(KeyValue.newBuilder()
              .setKey("app.build_name")
              .setValue(AnyValue.newBuilder().setStringValue("1.0.0").build())
              .build())
          .addAttributes(KeyValue.newBuilder()
              .setKey("app.build_id")
              .setValue(AnyValue.newBuilder().setStringValue("100").build())
              .build())
          .addAttributes(KeyValue.newBuilder()
              .setKey("os.name")
              .setValue(AnyValue.newBuilder().setStringValue("android").build())
              .build())
          .build();

      ScopeLogs scopeLogs = ScopeLogs.newBuilder()
          .addLogRecords(logRecord)
          .build();

      ResourceLogs resourceLogs = ResourceLogs.newBuilder()
          .setResource(resource)
          .addScopeLogs(scopeLogs)
          .build();

      ExportLogsServiceRequest request = ExportLogsServiceRequest.newBuilder()
          .addResourceLogs(resourceLogs)
          .build();

      // Mock symbolication
      when(symbolicator.symbolicateJsInPlace(anyList(), any()))
          .thenReturn(Single.just(List.of("func@file.js:1:1")));
      lenient().when(symbolicator.retrace(anyList(), any()))
          .thenReturn(Single.just(Collections.emptyList()));

      Single<List<StackTraceEvent>> result = errorGroupingService.process(request);

      List<StackTraceEvent> events = result.blockingGet();
      assertEquals(1, events.size());

      StackTraceEvent event = events.get(0);
      assertEquals("1.0.0", event.getAppVersion());
      assertEquals("100", event.getAppVersionCode());
      assertEquals("android", event.getPlatform());
      assertEquals("Test error", event.getExceptionMessage());
      assertEquals("Error", event.getExceptionType());
      assertNotNull(event.getExceptionStackTrace());
      assertNotNull(event.getExceptionStackTraceRaw());
      assertTrue(event.getGroupId().startsWith("EXC-"));
    }
  }
}

