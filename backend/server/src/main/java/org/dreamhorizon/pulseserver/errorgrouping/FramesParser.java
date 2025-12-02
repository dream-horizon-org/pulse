package org.dreamhorizon.pulseserver.errorgrouping;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.dreamhorizon.pulseserver.errorgrouping.model.JSFrame;
import org.dreamhorizon.pulseserver.errorgrouping.model.JavaFrame;
import org.dreamhorizon.pulseserver.errorgrouping.model.Lane;
import org.dreamhorizon.pulseserver.errorgrouping.model.NDKFrame;
import org.dreamhorizon.pulseserver.errorgrouping.model.ParsedFrames;
import org.dreamhorizon.pulseserver.errorgrouping.utils.ErrorGroupingUtils;

/**
 * Tier-0 fingerprinting with pre-normalization symbolication.
 * <p>
 * What it does:
 * 1) Parse raw stack trace into lanes (JS / JAVA / NDK) with rich frame info.
 * 2) Detect minified/obfuscated/unsymbolicated frames.
 * 3) Symbolicate per lane if artifacts are available (JS implemented here).
 * 4) Choose PRIMARY lane (by in-app frames), normalize tokens, build signature.
 * 5) Hash (SHA-1) and construct a display name.
 * <p>
 * To enable JS symbolication, add dependency (Gradle):
 * implementation("com.google.javascript:closure-compiler:v20231002")
 * or a newer version that includes SourceMapConsumerV3.
 */
public final class FramesParser {

  // ---------- Config ----------

  public static final int TOP_N_FRAMES = 10;

  public static final Set<String> NDK_INAPP_LIBS = Set.of(); // e.g., "libdream11.so"

  public static ParsedFrames parse(List<String> lines) {
    ParsedFrames st = new ParsedFrames();
    ParserState state = new ParserState();
    for (String line : lines) {
      String trimmed = line == null ? "" : line.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
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
      Matcher mJsTop = Regex.JS_ERR_LINE.matcher(trimmed);
      if (mJsTop.find()) {
        st.getJsTypes().add(mJsTop.group(1));
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

    // NDK signals
    Matcher sig = Regex.NDK_SIGNAL.matcher(trimmed);
    if (sig.find()) {
      String signal = sig.group();
      if (!st.getNdkTypes().contains(signal)) {
        st.getNdkTypes().add(signal);
        if (st.getPrimaryExceptionLane() == null) {
          st.setPrimaryExceptionLane(Lane.NDK);  // Track topmost exception
        }
      }
    }

    // Java headline (only if not claimed by JS and not a React Native JS exception)
    if (!state.sawTopType && !state.isReactNativeJsException) {
      Matcher mJavaTop = Regex.JAVA_TOP_TYPE.matcher(trimmed);
      if (mJavaTop.find()) {
        st.getJavaTypes().add(mJavaTop.group(1));
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
      st.getJsFrames().add(JSFrame.builder()
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
    Matcher jAt = Regex.JAVA_AT_LINE.matcher(line);
    if (!jAt.find()) {
      return false;
    }

    val classMethod = parseJavaClassMethod(jAt.group(1));
    val fileLineParsed = parseJavaFileLine(jAt.group(2));

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

  private static boolean tryParseNdkFrame(String line, ParsedFrames st, ParserState state) {
    Matcher ndk = Regex.NDK_LINE.matcher(line);
    if (!ndk.find()) {
      return false;
    }

    String libPath = ndk.group(2);
    String sym = ndk.group(3);
    st.getNdkFrames().add(NDKFrame.builder()
        .ndkPc(ndk.group(1))
        .ndkLib(basename(libPath))
        .ndkSymbol((sym == null || sym.isBlank()) ? null : sym.split("\\+", 2)[0])
        .rawLine(line)
        .originalPosition(state.framePosition++)
        .build());
    return true;
  }

  private static JSFrame buildJsFrame(String func, String file, String line, String col, String rawLine, int position) {
    return JSFrame.builder()
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

  private static class Regex {
    private static final Pattern JAVA_TOP_TYPE =
        Pattern.compile("^(?:Exception in thread \".*?\"\\s+)?([\\w$]+(?:\\.[\\w$]+)+)(?::.*)?$");
    private static final Pattern JAVA_CAUSED_BY =
        Pattern.compile("^\\s*Caused by:\\s*([\\w.$]+)(?::.*)?$");
    private static final Pattern JAVA_AT_LINE =
        Pattern.compile("^\\s*at\\s+([^\\s(]+)\\(([^)]*)\\)\\s*$"); // left(file:line)

    // React Native JavascriptException pattern
    private static final Pattern RN_JS_EXCEPTION =
        Pattern.compile("JavascriptException.*?\\b(Error|Exception)\\b");

    private static final Pattern JS_ERR_LINE =
        Pattern.compile("^\\s*([A-Za-z_$][A-Za-z0-9_$]*(?:Error|Exception)|Invariant Violation)\\s*:?.*$");
    private static final Pattern JS_AT_FUNC_FILE_LINE_COL =
        Pattern.compile("^\\s*at\\s+([^\\s(]+)\\s*\\(([^:]+):(\\d+):(\\d+)\\)\\s*$");
    private static final Pattern JS_AT_FILE_LINE_COL =
        Pattern.compile("^\\s*at\\s+([^:]+):(\\d+):(\\d+)\\s*$");

    // React Native compact format: functionName@bundleId:line:col or functionName@bundleId:line
    private static final Pattern RN_COMPACT_FRAME_WITH_COL =
        Pattern.compile("^\\s*([^@\\s]+)@([^:]+):(\\d+):(\\d+)\\s*$");
    private static final Pattern RN_COMPACT_FRAME_NO_COL =
        Pattern.compile("^\\s*([^@\\s]+)@([^:]+):(\\d+)\\s*$");

    private static final Pattern NDK_LINE =
        Pattern.compile("^\\s*#\\d+\\s+pc\\s+([0-9a-fA-Fx]+)\\s+(\\S+)(?:\\s+\\(([^)]+)\\))?.*$");
    private static final Pattern NDK_SIGNAL = Pattern.compile("\\bSIG[A-Z0-9]+\\b");
  }

  // Parser state holder
  private static class ParserState {
    boolean sawTopType = false;
    boolean isReactNativeJsException = false;
    int framePosition = 0;  // Track frame position for reconstruction
  }
}
