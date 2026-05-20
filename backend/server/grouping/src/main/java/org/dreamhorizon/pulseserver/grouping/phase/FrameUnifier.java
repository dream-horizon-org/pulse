package org.dreamhorizon.pulseserver.grouping.phase;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.dreamhorizon.pulseserver.grouping.model.Frame;
import org.dreamhorizon.pulseserver.grouping.model.JavaFrame;
import org.dreamhorizon.pulseserver.grouping.model.ParsedFrames;

/**
 * Phase 1a — canonical-shape normalization.
 *
 * <p>The bulk of normalization happens at parse time in
 * {@code FramesParser}. This pass is a defensive, idempotent consolidation
 * that ensures every frame token follows the canonical
 * {@code package.Class#method} (Java) / {@code file#fn} (JS) /
 * {@code lib#sym} (NDK / iOS) shape, with build-volatile suffixes removed:</p>
 *
 * <ul>
 *   <li>Java method {@code lambda$xxx$0}, {@code lambda$xxx$N} &rarr;
 *       {@code lambda} (lambda counters shift when surrounding code changes).</li>
 *   <li>Java class name trailing {@code $0}, {@code $1}, … anonymous-class
 *       suffixes are stripped (build-dependent numbering).</li>
 * </ul>
 *
 * <p>Idempotent — running it twice yields the same frames.</p>
 */
@UtilityClass
public class FrameUnifier {

  /** Strips trailing {@code $digit} run from a class name (anonymous class numbering). */
  private static final Pattern ANON_DIGIT = Pattern.compile("\\$\\d+");

  /**
   * Run the unifier across every frame in {@code frames}. Currently only Java
   * frames need work — JS/NDK/iOS tokens come out clean from the parser.
   */
  public static void unifyAll(ParsedFrames frames) {
    if (frames == null) {
      return;
    }
    for (JavaFrame f : frames.getJavaFrames()) {
      unifyJava(f);
    }
  }

  /**
   * Normalize one Java frame in-place. Idempotent. Safe to call before or
   * after parser-level normalization.
   */
  public static void unifyJava(JavaFrame frame) {
    if (frame == null) {
      return;
    }
    String cls = frame.getJavaClass();
    String method = frame.getJavaMethod();
    String normalizedCls = normalizeJavaClass(cls);
    String normalizedMethod = normalizeJavaMethod(method);
    if (!java.util.Objects.equals(cls, normalizedCls)) {
      frame.setJavaClass(normalizedCls);
    }
    if (!java.util.Objects.equals(method, normalizedMethod)) {
      frame.setJavaMethod(normalizedMethod);
    }
    rebuildJavaToken(frame);
  }

  /** Strip {@code $digit} anonymous-class numbering from a Java class FQCN. */
  public static String normalizeJavaClass(String javaClass) {
    if (javaClass == null || javaClass.isEmpty()) {
      return javaClass;
    }
    return ANON_DIGIT.matcher(javaClass).replaceAll("");
  }

  /**
   * Collapse {@code lambda$xxx$N} (and any other {@code lambda$…} form) into a
   * single canonical {@code lambda} marker. Build-specific lambda counters
   * shift with unrelated edits to the enclosing class.
   */
  public static String normalizeJavaMethod(String javaMethod) {
    if (javaMethod == null || javaMethod.isEmpty()) {
      return javaMethod;
    }
    if (javaMethod.contains("lambda$")) {
      return "lambda";
    }
    return javaMethod;
  }

  private static void rebuildJavaToken(Frame frame) {
    if (!(frame instanceof JavaFrame jf)) {
      return;
    }
    String cls = jf.getJavaClass() == null ? "" : jf.getJavaClass();
    String method = jf.getJavaMethod() == null ? "" : jf.getJavaMethod();
    jf.setToken(cls + "#" + method);
  }
}
