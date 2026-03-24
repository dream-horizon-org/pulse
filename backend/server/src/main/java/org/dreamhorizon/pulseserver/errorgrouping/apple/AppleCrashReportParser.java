package org.dreamhorizon.pulseserver.errorgrouping.apple;

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
