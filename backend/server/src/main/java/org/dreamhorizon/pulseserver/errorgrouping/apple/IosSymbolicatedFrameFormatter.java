package org.dreamhorizon.pulseserver.errorgrouping.apple;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Reformats llvm-symbolizer output into Apple crash report–style lines (frame #, image, PC, symbol)
 * plus an optional second line for source location. See {@link org.dreamhorizon.pulseserver.errorgrouping.FramesParser}
 * {@code APPLE_CRASH_FRAME} for the raw layout.
 */
@UtilityClass
public class IosSymbolicatedFrameFormatter {

  /** Same groups as FramesParser.Regex.APPLE_CRASH_FRAME: idx, image, pc, tail. */
  private static final Pattern APPLE_CRASH_FRAME =
      Pattern.compile("^\\s*(\\d+)\\s+(\\S+)\\s+(0x[0-9a-fA-F]+)(.*)$");

  private static final Pattern TRAILING_PLUS_OFFSET = Pattern.compile("(\\s+\\+\\s+\\d+)\\s*$");

  /** llvm-symbolizer often includes {@code + N} in the symbol line; avoid duplicating from raw tail. */
  private static final Pattern SYMBOL_HAS_TRAILING_PLUS_OFFSET = Pattern.compile("\\+\\s*\\d+\\s*$");

  /** llvm line: {@code symbol /abs/path/File.m:42:11} */
  private static final Pattern LLVM_LOC_ABS =
      Pattern.compile("^(.+?)\\s+(/\\S+:\\d+(?::\\d+)?)\\s*$");

  /** llvm line: {@code symbol File.swift:10:5} */
  private static final Pattern LLVM_LOC_REL =
      Pattern.compile("^(.+?)\\s+(\\S+\\.(?:swift|m|mm|c|h|cpp|cc|hpp|cxx):\\d+(?::\\d+)?)\\s*$");

  private static final int IMAGE_WIDTH = 36;

  /**
   * @param rawLine original Apple frame line from the crash report
   * @param llvmMergedLine single line from {@code llvm-symbolizer} (symbol + optional path:line:col)
   * @return one or two lines (second line {@code at ...} when a source location was parsed); on parse
   *     failure returns {@code llvmMergedLine}
   */
  public static String formatSymbolicatedFrameLine(String rawLine, String llvmMergedLine) {
    if (rawLine == null || llvmMergedLine == null || llvmMergedLine.isBlank()) {
      return llvmMergedLine != null ? llvmMergedLine : rawLine;
    }
    if (rawLine.equals(llvmMergedLine)) {
      return rawLine;
    }
    String trimmedRaw = rawLine.stripLeading();
    Matcher apple = APPLE_CRASH_FRAME.matcher(trimmedRaw);
    if (!apple.matches()) {
      return llvmMergedLine;
    }
    String idx = apple.group(1);
    String image = apple.group(2);
    String pc = apple.group(3);
    String tail = apple.group(4) == null ? "" : apple.group(4);
    SymbolAndLocation sl = splitSymbolAndLocation(llvmMergedLine.trim());
    String symbol = sl.symbol().isEmpty() ? llvmMergedLine.trim() : sl.symbol();
    String plusOffset = extractPlusOffset(tail);
    if (SYMBOL_HAS_TRAILING_PLUS_OFFSET.matcher(symbol.trim()).find()) {
      plusOffset = "";
    }
    String primary =
        String.format("%s%s  %s  %s%s", padIndex(idx), padImage(image), pc, symbol, plusOffset);

    if (sl.location() == null || sl.location().isBlank()) {
      return primary;
    }
    return primary + "\n      at " + sl.location();
  }

  private static String padIndex(String idx) {
    return String.format("%-4s", idx);
  }

  private static String padImage(String image) {
    if (image == null || image.isEmpty()) {
      return " ".repeat(IMAGE_WIDTH);
    }
    if (image.length() >= IMAGE_WIDTH) {
      return image.substring(0, IMAGE_WIDTH - 3) + "...";
    }
    return String.format("%-" + IMAGE_WIDTH + "s", image);
  }

  static String extractPlusOffset(String tail) {
    if (tail == null || tail.isEmpty()) {
      return "";
    }
    Matcher m = TRAILING_PLUS_OFFSET.matcher(tail);
    return m.find() ? m.group(1) : "";
  }

  record SymbolAndLocation(String symbol, String location) {}

  static SymbolAndLocation splitSymbolAndLocation(String llvmLine) {
    if (llvmLine == null || llvmLine.isEmpty()) {
      return new SymbolAndLocation("", null);
    }
    Matcher abs = LLVM_LOC_ABS.matcher(llvmLine);
    if (abs.matches()) {
      return new SymbolAndLocation(abs.group(1).trim(), abs.group(2).trim());
    }
    Matcher rel = LLVM_LOC_REL.matcher(llvmLine);
    if (rel.matches()) {
      return new SymbolAndLocation(rel.group(1).trim(), rel.group(2).trim());
    }
    return new SymbolAndLocation(llvmLine, null);
  }
}
