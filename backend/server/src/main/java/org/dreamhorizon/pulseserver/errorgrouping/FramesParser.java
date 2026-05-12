package org.dreamhorizon.pulseserver.errorgrouping;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.dreamhorizon.pulseserver.errorgrouping.model.JavaFrame;
import org.dreamhorizon.pulseserver.errorgrouping.model.JsFrame;
import org.dreamhorizon.pulseserver.errorgrouping.model.Lane;
import org.dreamhorizon.pulseserver.errorgrouping.model.NdkFrame;
import org.dreamhorizon.pulseserver.errorgrouping.model.ParsedFrames;
import org.dreamhorizon.pulseserver.errorgrouping.utils.ErrorGroupingUtils;

/**
 * Tier-0 fingerprinting with pre-normalization symbolication.
 * What it does:
 * 1) Parse raw stack trace into lanes (JS / JAVA / NDK) with rich frame info.
 * 2) Detect minified/obfuscated/unsymbolicated frames.
 * 3) Symbolicate per lane if artifacts are available (JS implemented here).
 * 4) Choose PRIMARY lane (by in-app frames), normalize tokens, build signature.
 * 5) Hash (SHA-1) and construct a display name.
 * To enable JS symbolication, add dependency (Gradle):
 * implementation("com.google.javascript:closure-compiler:v20231002")
 * or a newer version that includes SourceMapConsumerV3.
 */
public final class FramesParser {

  // ---------- Config ----------

  // Reduced from 10 to 3 based on Fancode depth-sweep analysis:
  // - Top-10 buckets cover 91.9% of crashes (vs 88.7% at depth=10).
  // - P95 crash triage list shrinks from 26 to 17 buckets (-35%).
  // - #1 priority bug (Vmax Timer, 7,628 events, 58.8%) preserved exactly.
  // - ANR fragmentation drops 28% as a bonus.
  // Industry baselines (Crashlytics / Sentry / Bugsnag / Datadog) effectively
  // hash 1-3 in-app frames; without an in-app filter in place yet, depth=3 on
  // raw frames is the equivalent sweet spot.
  // See Confluence: "Fancode Depth Sweep — Picking a Frame-Depth for
  // Quick-Deploy Re-bucketing" (page id 4913659955).
  public static final int TOP_N_FRAMES = 3;

  public static final Set<String> NDK_INAPP_LIBS = Set.of(); // e.g., "libdream11.so"

  public static ParsedFrames parse(List<String> lines) {
    ParsedFrames st = new ParsedFrames();
    ParserState state = new ParserState();
    for (String line : lines) {
      String trimmed = line == null ? "" : line.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      updateIosThreadState(trimmed, state);
      captureIosProcessName(trimmed, state);
      // 1. Detect exception types
      detectExceptionTypes(line, trimmed, st, state);
      // 2. Parse frames (order matters: RN compact → standard JS → Java → NDK)
      // Track frame position for later reconstruction
      if (tryParseJsFrame(line, trimmed, st, state)) {
        continue;
      }
      if (tryParseJavaFrame(line, st, state)) {
        continue;
      }
      if (tryParseIosNativeFrame(line, trimmed, st, state)) {
        continue;
      }
      tryParseNdkFrame(line, st, state);
    }
    // Set the React Native flag if detected
    st.setReactNativeJsException(state.isReactNativeJsException);
    return st;
  }

  private static void detectExceptionTypes(String line, String trimmed, ParsedFrames st, ParserState state) {
    // Detect React Native JavascriptException
    Matcher rnJsEx = Regex.RN_JS_EXCEPTION.matcher(line);
    if (rnJsEx.find()) {
      state.isReactNativeJsException = true;
      if (!state.sawTopType && rnJsEx.group(1) != null) {
        st.getJsTypes().add(rnJsEx.group(1));
        st.setPrimaryExceptionLane(Lane.JS);  // Track topmost exception
        st.setExceptionHeaderLine(trimmed);   // Store full exception line
        state.sawTopType = true;
      }
    }

    // JS error line (TypeError, Error, etc.)
    if (!state.sawTopType) {
      Matcher jsTop = Regex.JS_ERR_LINE.matcher(trimmed);
      if (jsTop.find()) {
        st.getJsTypes().add(jsTop.group(1));
        st.setPrimaryExceptionLane(Lane.JS);  // Track topmost exception
        st.setExceptionHeaderLine(trimmed);   // Store full exception line
        state.sawTopType = true;
      }
    }

    // Java "Caused by" lines
    Matcher mcb = Regex.JAVA_CAUSED_BY.matcher(line);
    if (mcb.find()) {
      st.getJavaTypes().add(mcb.group(1));
    }

    // Additional JS error type
    Matcher mjs = Regex.JS_ERR_LINE.matcher(trimmed);
    if (mjs.find() && st.getJsTypes().isEmpty()) {
      st.getJsTypes().add(mjs.group(1));
    }

    // NDK tombstone signals (avoid Apple "EXC_CRASH (SIGABRT)" lines)
    Matcher sig = Regex.NDK_SIGNAL.matcher(trimmed);
    if (sig.find() && !trimmed.contains("Exception Type:") && !trimmed.contains("EXC_")) {
      String signal = sig.group();
      if (!st.getNdkTypes().contains(signal)) {
        st.getNdkTypes().add(signal);
        if (st.getPrimaryExceptionLane() == null) {
          st.setPrimaryExceptionLane(Lane.NDK);  // Track topmost exception
        }
      }
    }

    // Apple / KSCrash style (crashed thread + Binary Images)
    Matcher iosExc = Regex.IOS_EXCEPTION_TYPE.matcher(trimmed);
    if (iosExc.find()) {
      String desc = iosExc.group(1).trim();
      if (!st.getIosNativeTypes().contains(desc)) {
        st.getIosNativeTypes().add(desc);
      }
      if (!state.sawTopType) {
        st.setPrimaryExceptionLane(Lane.IOS_NATIVE);
        st.setExceptionHeaderLine(trimmed);
        state.sawTopType = true;
      }
    }

    // Java headline (only if not claimed by JS and not a React Native JS exception)
    if (!state.sawTopType && !state.isReactNativeJsException) {
      Matcher javaTop = Regex.JAVA_TOP_TYPE.matcher(trimmed);
      if (javaTop.find()) {
        st.getJavaTypes().add(javaTop.group(1));
        st.setPrimaryExceptionLane(Lane.JAVA);  // Track topmost exception
        st.setExceptionHeaderLine(trimmed);     // Store full exception line
        state.sawTopType = true;
      }
    }
  }

  private static boolean tryParseJsFrame(String line, String trimmed, ParsedFrames st, ParserState state) {
    // React Native compact format (check first)
    if (tryParseRnCompactFrame(line, trimmed, st, state)) {
      return true;
    }

    // Standard JS format: "at func (file:line:col)"
    Matcher js1 = Regex.JS_AT_FUNC_FILE_LINE_COL.matcher(line);
    if (js1.find()) {
      st.getJsFrames().add(buildJsFrame(
          js1.group(1), js1.group(2), js1.group(3), js1.group(4), line, state.framePosition++));
      return true;
    }

    // Standard JS format: "at file:line:col"
    Matcher js2 = Regex.JS_AT_FILE_LINE_COL.matcher(line);
    if (js2.find()) {
      st.getJsFrames().add(buildJsFrame(
          "anonymous", js2.group(1), js2.group(2), js2.group(3), line, state.framePosition++));
      return true;
    }

    return false;
  }

  private static boolean tryParseRnCompactFrame(String line, String trimmed, ParsedFrames st, ParserState state) {
    // React Native format with column: "functionName@bundleId:line:column"
    Matcher rnCompact1 = Regex.RN_COMPACT_FRAME_WITH_COL.matcher(trimmed);
    if (rnCompact1.find()) {
      st.getJsFrames().add(buildJsFrame(
          rnCompact1.group(1), rnCompact1.group(2),
          rnCompact1.group(3), rnCompact1.group(4), line, state.framePosition++));
      return true;
    }

    // React Native minified format: "functionName@bundleId:offset"
    // Offset is character position (column) on line 1
    Matcher rnCompact2 = Regex.RN_COMPACT_FRAME_NO_COL.matcher(trimmed);
    if (rnCompact2.find()) {
      st.getJsFrames().add(JsFrame.builder()
          .jsFunction(ErrorGroupingUtils.normalizeJsFunction(rnCompact2.group(1)))
          .jsFile(ErrorGroupingUtils.sanitizeJsFile(rnCompact2.group(2)))
          .jsLine(1)  // Minified bundles are on line 1
          .jsColumn(ErrorGroupingUtils.safeInt(rnCompact2.group(3)))
          .rawLine(line)
          .originalPosition(state.framePosition++)
          .build());
      return true;
    }

    return false;
  }

  private static boolean tryParseJavaFrame(String line, ParsedFrames st, ParserState state) {
    Matcher javaAt = Regex.JAVA_AT_LINE.matcher(line);
    if (!javaAt.find()) {
      return false;
    }

    val classMethod = parseJavaClassMethod(javaAt.group(1));
    val fileLineParsed = parseJavaFileLine(javaAt.group(2));

    st.getJavaFrames().add(JavaFrame.builder()
        .javaClass(classMethod.getLeft())
        .javaMethod(classMethod.getRight())
        .javaFile(fileLineParsed.getLeft())
        .javaLine(fileLineParsed.getRight())
        .rawLine(line)
        .originalPosition(state.framePosition++)
        .build());
    return true;
  }

  /**
   * Apple crash report: only lines inside {@code Thread N Crashed:} … next {@code Thread M:} block.
   * Same shape as {@link NdkFrame} with {@link Lane#IOS_NATIVE} (image → ndkLib, PC → ndkPc).
   */
  private static boolean tryParseIosNativeFrame(String line, String trimmed, ParsedFrames st, ParserState state) {
    if (!state.inIosCrashedThread) {
      return false;
    }
    Matcher m = Regex.APPLE_CRASH_FRAME.matcher(trimmed);
    if (!m.find()) {
      return false;
    }
    String image = m.group(2).trim();
    String pc = m.group(3);
    if (!pc.startsWith("0x") && !pc.startsWith("0X")) {
      pc = "0x" + pc;
    }
    String rest = m.group(4) == null ? "" : m.group(4).trim();
    String sym = rest;
    int plusIdx = rest.indexOf(" + ");
    if (plusIdx > 0) {
      sym = rest.substring(0, plusIdx).trim();
    }
    if (sym.isEmpty()) {
      sym = null;
    }
    // "(null)" image rows often use "0x0 + <decimal>" with no real symbol name
    if ("(null)".equals(image)) {
      sym = null;
    }
    st.getIosNativeFrames().add(NdkFrame.builder()
        .lane(Lane.IOS_NATIVE)
        .iosAppBinaryName(state.iosProcessName)
        .ndkPc(pc)
        .ndkLib(image)
        .ndkSymbol(sym)
        .rawLine(line)
        .originalPosition(state.framePosition++)
        .build());
    if (st.getPrimaryExceptionLane() == null) {
      st.setPrimaryExceptionLane(Lane.IOS_NATIVE);
    }
    return true;
  }

  private static boolean tryParseNdkFrame(String line, ParsedFrames st, ParserState state) {
    Matcher ndk = Regex.NDK_LINE.matcher(line);
    if (!ndk.find()) {
      return false;
    }

    String libPath = ndk.group(2);
    String sym = ndk.group(3);
    st.getNdkFrames().add(NdkFrame.builder()
        .ndkPc(ndk.group(1))
        .ndkLib(basename(libPath))
        .ndkSymbol((sym == null || sym.isBlank()) ? null : sym.split("\\+", 2)[0])
        .rawLine(line)
        .originalPosition(state.framePosition++)
        .build());
    return true;
  }

  private static JsFrame buildJsFrame(String func, String file, String line, String col, String rawLine, int position) {
    return JsFrame.builder()
        .jsFunction(ErrorGroupingUtils.normalizeJsFunction(func))
        .jsFile(ErrorGroupingUtils.sanitizeJsFile(file))
        .jsLine(ErrorGroupingUtils.safeInt(line))
        .jsColumn(ErrorGroupingUtils.safeInt(col))
        .rawLine(rawLine)
        .originalPosition(position)
        .build();
  }

  private static Pair<String, String> parseJavaClassMethod(String left) {
    String l = left;
    int slash = l.indexOf('/');
    if (slash >= 0) {
      l = l.substring(slash + 1);
    }
    int dot = l.lastIndexOf('.');
    if (dot <= 0 || dot == l.length() - 1) {
      return Pair.of(l, "");
    }
    String cls = l.substring(0, dot).replaceAll("\\$\\d+", "");
    String m = l.substring(dot + 1);
    if (m.contains("lambda$")) {
      m = "lambda";
    }
    return Pair.of(cls, m);
  }

  private static Pair<String, Integer> parseJavaFileLine(String s) {
    if (s == null) {
      return Pair.of(null, null);
    }
    int idx = s.indexOf(':');
    if (idx > 0) {
      return Pair.of(s.substring(0, idx), ErrorGroupingUtils.safeInt(s.substring(idx + 1)));
    } else {
      return Pair.of(s, null);
    }
  }

  private static String basename(String path) {
    String p = path.replace('\\', '/');
    int i = p.lastIndexOf('/');
    return (i >= 0) ? p.substring(i + 1) : p;
  }

  private static void captureIosProcessName(String trimmed, ParserState state) {
    Matcher pm = Regex.IOS_PROCESS.matcher(trimmed);
    if (pm.find()) {
      state.iosProcessName = pm.group(1).trim();
    }
  }

  /**
   * Tracks the {@code Thread N Crashed:} block.
   * Plain {@code Thread M:} (not "name:") ends the block.
   */
  private static void updateIosThreadState(String trimmed, ParserState state) {
    if (Regex.THREAD_CRASHED.matcher(trimmed).find()) {
      state.inIosCrashedThread = true;
      return;
    }
    Matcher plainThread = Regex.THREAD_PLAIN.matcher(trimmed);
    if (plainThread.find()) {
      state.inIosCrashedThread = false;
    }
  }

  private static class Regex {
    private static final Pattern JAVA_TOP_TYPE =
        Pattern.compile("^(?:Exception in thread \".*?\"\\s+)?([\\w$]+(?:\\.[\\w$]+)+)(?::.*)?$");
    private static final Pattern JAVA_CAUSED_BY =
        Pattern.compile("^\\s*Caused by:\\s*([\\w.$]+)(?::.*)?$");
    private static final Pattern JAVA_AT_LINE =
        Pattern.compile("^\\s*(?:at\\s+)?([^\\s(]+)\\(([^)]*)\\)\\s*$"); // left(file:line) - "at " is optional

    // React Native JavascriptException pattern
    private static final Pattern RN_JS_EXCEPTION =
        Pattern.compile("JavascriptException.*?\\b(Error|Exception)\\b");

    private static final Pattern JS_ERR_LINE =
        Pattern.compile("^\\s*([A-Za-z_$][A-Za-z0-9_$]*(?:Error|Exception)|Invariant Violation)\\s*:?.*$");
    private static final Pattern JS_AT_FUNC_FILE_LINE_COL =
        Pattern.compile("^\\s*(?:at\\s+)?([^\\s(]+)\\s*\\(([^:]+):(\\d+):(\\d+)\\)\\s*$"); // "at " is optional
    private static final Pattern JS_AT_FILE_LINE_COL =
        Pattern.compile("^\\s*(?:at\\s+)?([^:]+):(\\d+):(\\d+)\\s*$"); // "at " is optional

    // React Native compact format: functionName@bundleId:line:col or functionName@bundleId:line
    private static final Pattern RN_COMPACT_FRAME_WITH_COL =
        Pattern.compile("^\\s*([^@\\s]+)@([^:]+):(\\d+):(\\d+)\\s*$");
    private static final Pattern RN_COMPACT_FRAME_NO_COL =
        Pattern.compile("^\\s*([^@\\s]+)@([^:]+):(\\d+)\\s*$");

    private static final Pattern NDK_LINE =
        Pattern.compile("^\\s*#\\d+\\s+pc\\s+([0-9a-fA-Fx]+)\\s+(\\S+)(?:\\s+\\(([^)]+)\\))?.*$");
    private static final Pattern NDK_SIGNAL = Pattern.compile("\\bSIG[A-Z0-9]+\\b");

    private static final Pattern IOS_PROCESS =
        Pattern.compile("^Process:\\s+(\\S+)\\s+\\[\\d+\\]\\s*$");
    private static final Pattern IOS_EXCEPTION_TYPE =
        Pattern.compile("^Exception Type:\\s*(.+)$");
    /** Crash report frame: frame# image address symbol + offset */
    private static final Pattern APPLE_CRASH_FRAME =
        Pattern.compile("^\\s*(\\d+)\\s+(\\S+)\\s+(0x[0-9a-fA-F]+)(.*)$");
    private static final Pattern THREAD_CRASHED = Pattern.compile("^Thread\\s+\\d+\\s+Crashed:\\s*$");
    private static final Pattern THREAD_PLAIN = Pattern.compile("^Thread\\s+\\d+:\\s*$");
  }

  // Parser state holder
  private static class ParserState {
    boolean sawTopType = false;
    boolean isReactNativeJsException = false;
    int framePosition = 0;  // Track frame position for reconstruction
    boolean inIosCrashedThread = false;
    String iosProcessName = null;
  }
}
