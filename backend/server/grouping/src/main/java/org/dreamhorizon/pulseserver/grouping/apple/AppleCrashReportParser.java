package org.dreamhorizon.pulseserver.grouping.apple;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Parses fields from Apple/KSCrash text reports for LLVM symbolication.
 * {@code Process:} for the app binary name, {@code Binary Images} for slide (load) and UUID per image.
 */
@UtilityClass
public class AppleCrashReportParser {

  private static final Pattern PROCESS =
      Pattern.compile("(?m)^Process:\\s+(\\S+)\\s+\\[\\d+\\]\\s*$");

  /**
   * Binary Images line: {@code 0x102938000 - 0x10294ffff PulseIOSExample arm64  <uuid> /path}
   */
  private static final Pattern BINARY_IMAGE =
      Pattern.compile(
          "(?m)^0x([0-9a-fA-F]+)\\s+-\\s+0x[0-9a-fA-F]+\\s+(\\S+)\\s+\\S+\\s+<([0-9a-fA-F]+)>\\s+");

  /** Same layout as {@link #BINARY_IMAGE} but captures CPU type for {@code llvm-objdump --arch=…}. */
  private static final Pattern BINARY_IMAGE_WITH_CPU =
      Pattern.compile(
          "(?m)^0x[0-9a-fA-F]+\\s+-\\s+0x[0-9a-fA-F]+\\s+(\\S+)\\s+(\\S+)\\s+<([0-9a-fA-F]+)>\\s+");

  public static Optional<String> parseProcessBinaryName(String rawReport) {
    if (rawReport == null || rawReport.isEmpty()) {
      return Optional.empty();
    }
    Matcher m = PROCESS.matcher(rawReport);
    if (m.find()) {
      return Optional.of(m.group(1).trim());
    }
    return Optional.empty();
  }

  /**
   * Load address (slide) for the named binary in {@code Binary Images} (first column {@code 0x…}).
   */
  public static Optional<Long> parseLoadAddressForBinary(String rawReport, String binaryName) {
    if (rawReport == null || binaryName == null || binaryName.isEmpty()) {
      return Optional.empty();
    }
    Matcher m = BINARY_IMAGE.matcher(rawReport);
    while (m.find()) {
      if (binaryName.equals(m.group(2))) {
        return Optional.of(parseHexLong(m.group(1)));
      }
    }
    return Optional.empty();
  }

  /**
   * UUID from crash report for the binary (32 hex chars, no dashes).
   */
  public static Optional<String> parseUuidForBinary(String rawReport, String binaryName) {
    if (rawReport == null || binaryName == null || binaryName.isEmpty()) {
      return Optional.empty();
    }
    Matcher m = BINARY_IMAGE.matcher(rawReport);
    while (m.find()) {
      if (binaryName.equals(m.group(2))) {
        return Optional.of(m.group(3).toLowerCase(Locale.ROOT));
      }
    }
    return Optional.empty();
  }

  /**
   * CPU type from Binary Images for the app (e.g. {@code arm64}, {@code x86_64}) for universal simulator
   * dSYMs: pass to {@code llvm-objdump --arch=} so {@code __TEXT} vmaddr / LC_UUID match the crashed slice.
   */
  public static Optional<String> parseCpuTypeForBinary(String rawReport, String binaryName) {
    if (rawReport == null || binaryName == null || binaryName.isEmpty()) {
      return Optional.empty();
    }
    Matcher m = BINARY_IMAGE_WITH_CPU.matcher(rawReport);
    while (m.find()) {
      if (binaryName.equals(m.group(1))) {
        return Optional.of(normalizeCpuTypeForLlvm(m.group(2)));
      }
    }
    return Optional.empty();
  }

  static String normalizeCpuTypeForLlvm(String cpu) {
    if (cpu == null || cpu.isEmpty()) {
      return "";
    }
    String t = cpu.trim().toLowerCase(Locale.ROOT);
    if (t.contains("arm-64") || "arm64".equals(t) || "aarch64".equals(t)) {
      return "arm64";
    }
    if (t.contains("x86-64") || "x86_64".equals(t) || "amd64".equals(t)) {
      return "x86_64";
    }
    if ("i386".equals(t) || "i686".equals(t)) {
      return "i386";
    }
    return cpu.trim();
  }

  public static long parseHexLong(String hex) {
    String s = hex.trim();
    if (s.startsWith("0x") || s.startsWith("0X")) {
      s = s.substring(2);
    }
    return Long.parseUnsignedLong(s, 16);
  }

  /**
   * Whether a crashed-thread frame image matches the {@code Process:} binary name.
   * Reports often use {@code Foo.debug.dylib} or a path in the frame column while {@code Process:} is {@code Foo}.
   */
  public static boolean frameImageMatchesProcess(String processName, String frameImage) {
    if (processName == null || frameImage == null || processName.isEmpty() || frameImage.isEmpty()) {
      return false;
    }
    if ("(null)".equalsIgnoreCase(frameImage.trim())) {
      return false;
    }
    if (processName.equals(frameImage)) {
      return true;
    }
    String img = frameImage;
    int slash = img.lastIndexOf('/');
    if (slash >= 0) {
      img = img.substring(slash + 1);
    }
    if (processName.equals(img)) {
      return true;
    }
    if (img.equals(processName + ".debug.dylib")) {
      return true;
    }
    if (img.startsWith(processName + ".") && img.endsWith(".dylib")) {
      return true;
    }
    return false;
  }
}
