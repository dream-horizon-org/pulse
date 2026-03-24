package org.dreamhorizon.pulseserver.errorgrouping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.errorgrouping.apple.AppleCrashReportParser;
import org.dreamhorizon.pulseserver.errorgrouping.model.NdkFrame;

/**
 * Runs {@code llvm-objdump} (Mach-O vmaddr + UUID) and {@code llvm-symbolizer} for iOS dSYM symbolication.
 * {@code FILE_HEX = vmaddr + PC - LOAD} (with vmaddr from {@code llvm-objdump --macho --private-headers}).
 */
@Slf4j
public class IosLlvmSymbolicator {

  public static final String LOG_PREFIX = "[PULSE IOS SYMBOLICATOR]";

  @com.google.inject.Inject
  public IosLlvmSymbolicator() {
    this(IosLlvmConfig.builder().build());
  }

  /** Fallback when line-oriented parse does not match (llvm output varies by version/OS). */
  private static final Pattern VMADDR_AFTER_TEXT =
      Pattern.compile("(?is)segname\\s+__TEXT.*?vmaddr\\s+(0x[0-9a-fA-F]+)");
  private static final Pattern UUID_LINE =
      Pattern.compile("UUID:\\s*([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})", Pattern.CASE_INSENSITIVE);
  private static final Pattern UUID_ANGLE =
      Pattern.compile("(?i)UUID\\s*[:=]\\s*<([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})>");
  private static final Pattern UUID_LOOSE =
      Pattern.compile("(?i)UUID\\s*[:=]\\s*([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})");
  private static final Pattern UUID_32_HEX = Pattern.compile("\\b([0-9A-Fa-f]{32})\\b");
  /** {@code LC_UUID} load command line as printed by {@code llvm-objdump -p} / {@code --private-headers}. */
  private static final Pattern UUID_AFTER_LC_UUID =
      Pattern.compile("(?im)^\\s*uuid\\s+([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})\\s*$");

  private final IosLlvmConfig config;

  public IosLlvmSymbolicator(IosLlvmConfig config) {
    this.config = config;
  }

  public List<String> symbolicateFrames(List<NdkFrame> frames, String rawReport, byte[] dsymZipBytes) {
    List<String> out = new ArrayList<>(frames.size());
    if (frames.isEmpty()) {
      return out;
    }
    Optional<String> appOpt = AppleCrashReportParser.parseProcessBinaryName(rawReport);
    log.info(
        "{} llvm inputs frames={} rawLen={} dsymZipBytes={} processNameParsed={}",
        LOG_PREFIX,
        frames.size(),
        rawReport == null ? 0 : rawReport.length(),
        dsymZipBytes == null ? 0 : dsymZipBytes.length,
        appOpt.isPresent());
    String appBinary = appOpt.orElse("");
    if (appBinary.isEmpty()) {
      log.warn("{} missing Process: name; cannot match app frames", LOG_PREFIX);
    }
    if (dsymZipBytes == null || dsymZipBytes.length == 0) {
      log.info("{} no dSYM bytes; returning raw frames count={}", LOG_PREFIX, frames.size());
      for (NdkFrame f : frames) {
        out.add(f.getRawLine());
      }
      return out;
    }

    Path tempRoot = null;
    try {
      tempRoot = Files.createTempDirectory("pulse-ios-dsym");
      Path dwarfPath = extractDsymAndFindDwarf(dsymZipBytes, tempRoot);
      if (dwarfPath == null || !Files.isRegularFile(dwarfPath)) {
        log.warn("{} could not locate DWARF inside dSYM zip", LOG_PREFIX);
        return rawLines(frames);
      }

      Optional<String> crashUuid =
          appBinary.isEmpty() ? Optional.empty() : AppleCrashReportParser.parseUuidForBinary(rawReport, appBinary);
      Optional<Long> loadOpt =
          appBinary.isEmpty() ? Optional.empty() : AppleCrashReportParser.parseLoadAddressForBinary(rawReport, appBinary);

      MachoPrivateHeaders macho = runObjdumpMachoPrivateHeaders(dwarfPath);
      String objdumpUuid = macho.uuidNormalized();
      String crashUuidNorm = crashUuid.map(IosLlvmSymbolicator::normalizeUuid).orElse("");
      if (crashUuid.isPresent()) {
        if (objdumpUuid == null || objdumpUuid.isEmpty()) {
          log.warn("{} could not parse dSYM UUID from llvm-objdump --private-headers; proceeding without UUID check",
              LOG_PREFIX);
        } else if (!crashUuidNorm.equals(objdumpUuid)) {
          log.error("{} dSYM UUID mismatch crash={} dwarf={} — skipping LLVM symbolication",
              LOG_PREFIX, shortHash(crashUuidNorm), shortHash(objdumpUuid));
          return rawLines(frames);
        } else {
          log.info("{} dSYM UUID check OK (from private-headers)", LOG_PREFIX);
        }
      } else {
        log.warn("{} crash report missing UUID for app binary {}; proceeding", LOG_PREFIX, appBinary);
      }

      if (loadOpt.isEmpty()) {
        log.warn("{} could not parse load address for {}; skipping LLVM", LOG_PREFIX, appBinary);
        return rawLines(frames);
      }
      long load = loadOpt.get();

      Long vmaddr = macho.vmaddr();
      if (vmaddr == null) {
        log.error("{} could not parse __TEXT vmaddr from llvm-objdump; skipping LLVM", LOG_PREFIX);
        return rawLines(frames);
      }

      List<Integer> appIndices = new ArrayList<>();
      List<Long> fileAddrs = new ArrayList<>();
      for (int i = 0; i < frames.size(); i++) {
        NdkFrame f = frames.get(i);
        if (appBinary.isEmpty()
            || !AppleCrashReportParser.frameImageMatchesProcess(appBinary, f.getNdkLib())) {
          continue;
        }
        try {
          long pc = AppleCrashReportParser.parseHexLong(f.getNdkPc());
          long fileAddr = vmaddr + pc - load;
          appIndices.add(i);
          fileAddrs.add(fileAddr);
        } catch (Exception e) {
          log.warn("{} bad PC for frame idx={} msg={}", LOG_PREFIX, i, e.getMessage());
        }
      }

      log.info("{} LOAD=0x{} vmaddr=0x{} appPCs={} rawLen={} rawHash={}",
          LOG_PREFIX,
          Long.toHexString(load),
          Long.toHexString(vmaddr),
          fileAddrs.size(),
          rawReport.length(),
          shortHash(rawReport));

      if (fileAddrs.isEmpty() && !frames.isEmpty() && !appBinary.isEmpty()) {
        log.warn(
            "{} no frames matched app process={} for LLVM (see frameImageMatchesProcess); "
                + "distinctFrameImages(sample)={}",
            LOG_PREFIX,
            appBinary,
            frames.stream().map(NdkFrame::getNdkLib).distinct().limit(12).toList());
      }

      List<String> llvmLines = runLlvmSymbolizer(dwarfPath, fileAddrs);
      int symIdx = 0;
      for (int i = 0; i < frames.size(); i++) {
        NdkFrame f = frames.get(i);
        if (symIdx < appIndices.size() && appIndices.get(symIdx) == i) {
          String pretty = symIdx < llvmLines.size() ? llvmLines.get(symIdx) : f.getRawLine();
          if (isUnhelpfulLlvmSymbolication(pretty)) {
            pretty = f.getRawLine();
          }
          out.add(pretty);
          symIdx++;
        } else {
          out.add(f.getRawLine());
        }
      }
      return out;
    } catch (Exception e) {
      log.error("{} symbolication failed: {}", LOG_PREFIX, e.getMessage(), e);
      return rawLines(frames);
    } finally {
      if (tempRoot != null) {
        deleteRecursively(tempRoot);
      }
    }
  }

  /**
   * llvm-symbolizer often prints {@code ??} / {@code ??:0:0} when a PC has no DWARF mapping — worse than the
   * original crash line, so we keep raw.
   */
  public static boolean isUnhelpfulLlvmSymbolication(String line) {
    if (line == null || line.isBlank()) {
      return true;
    }
    String t = line.trim();
    if ("??".equals(t)) {
      return true;
    }
    if (t.contains("??:0:0")) {
      return true;
    }
    // e.g. "?? ??:0:0" or "symbol ??:0:0" with unknown file
    if (t.startsWith("??") && t.contains(":0:0")) {
      return true;
    }
    return false;
  }

  private static List<String> rawLines(List<NdkFrame> frames) {
    List<String> r = new ArrayList<>(frames.size());
    for (NdkFrame f : frames) {
      r.add(f.getRawLine());
    }
    return r;
  }

  /**
   * One {@code llvm-objdump --macho --private-headers} call: vmaddr for __TEXT and UUID from LC_UUID.
   */
  private record MachoPrivateHeaders(Long vmaddr, String uuidNormalized) {}

  private MachoPrivateHeaders runObjdumpMachoPrivateHeaders(Path dwarfPath) throws IOException, InterruptedException {
    Process proc = new ProcessBuilder(
        config.getLlvmObjdumpPath(),
        "--macho",
        "--private-headers",
        dwarfPath.toAbsolutePath().toString())
        .redirectErrorStream(true)
        .start();
    String output = readLimited(proc.getInputStream(), config.getMaxCaptureChars());
    boolean finished = proc.waitFor(config.getTimeoutSeconds(), TimeUnit.SECONDS);
    if (!finished) {
      proc.destroyForcibly();
      log.warn("{} llvm-objdump --macho --private-headers timed out", LOG_PREFIX);
      return new MachoPrivateHeaders(null, null);
    }
    int ev = proc.exitValue();
    if (ev != 0) {
      log.warn("{} llvm-objdump --macho --private-headers exit={} head={}",
          LOG_PREFIX, ev, oneLinePreview(output, 400));
    }
    Long vmaddr = parseVmaddrAfterTextSegment(output);
    String uuid = parseUuidFromObjdumpOutput(output);
    return new MachoPrivateHeaders(vmaddr, uuid);
  }

  /**
   * __TEXT segment vmaddr from {@code llvm-objdump --macho --private-headers} output.
   */
  public static Long parseVmaddrAfterTextSegment(String output) {
    if (output == null || output.isBlank()) {
      return null;
    }
    String[] lines = output.split("\\R");
    boolean afterSegnameText = false;
    for (String line : lines) {
      if (line == null) {
        continue;
      }
      String lower = line.toLowerCase(Locale.ROOT);
      if (lower.contains("segname") && lower.contains("__text")) {
        afterSegnameText = true;
        continue;
      }
      if (afterSegnameText && lower.contains("vmaddr")) {
        Matcher hex = Pattern.compile("0x[0-9a-fA-F]+").matcher(line);
        if (hex.find()) {
          return AppleCrashReportParser.parseHexLong(hex.group());
        }
        String[] parts = line.trim().split("\\s+");
        for (int i = 1; i < parts.length; i++) {
          if (parts[i].startsWith("0x") || parts[i].startsWith("0X")) {
            try {
              return AppleCrashReportParser.parseHexLong(parts[i]);
            } catch (Exception ignored) {
              // try next token
            }
          }
        }
        break;
      }
    }
    Matcher vm = VMADDR_AFTER_TEXT.matcher(output);
    if (vm.find()) {
      return AppleCrashReportParser.parseHexLong(vm.group(1));
    }
    return null;
  }

  /** Tries several shapes from llvm-objdump Mach-O output (private-headers, load commands, or --uuid). */
  public static String parseUuidFromObjdumpOutput(String output) {
    if (output == null || output.isBlank()) {
      return null;
    }
    Matcher lc = UUID_AFTER_LC_UUID.matcher(output);
    if (lc.find()) {
      return normalizeUuid(lc.group(1));
    }
    Matcher m = UUID_LINE.matcher(output);
    if (m.find()) {
      return normalizeUuid(m.group(1));
    }
    m = UUID_ANGLE.matcher(output);
    if (m.find()) {
      return normalizeUuid(m.group(1));
    }
    m = UUID_LOOSE.matcher(output);
    if (m.find()) {
      return normalizeUuid(m.group(1));
    }
    for (String ln : output.split("\\R")) {
      if (!ln.toLowerCase(Locale.ROOT).contains("uuid")) {
        continue;
      }
      Matcher hm = UUID_32_HEX.matcher(ln);
      if (hm.find()) {
        return normalizeUuid(hm.group(1));
      }
    }
    return null;
  }

  private static String oneLinePreview(String s, int max) {
    if (s == null) {
      return "";
    }
    String t = s.replace('\r', ' ').replace("\n", " | ").trim();
    return t.length() <= max ? t : t.substring(0, max) + "...";
  }

  private List<String> runLlvmSymbolizer(Path dwarfPath, List<Long> fileAddrs)
      throws IOException, InterruptedException {
    List<String> symbols = new ArrayList<>();
    if (fileAddrs.isEmpty()) {
      return symbols;
    }
    Process proc = new ProcessBuilder(
        config.getLlvmSymbolizerPath(),
        "--obj=" + dwarfPath.toAbsolutePath(),
        "--default-arch=" + config.getDefaultArch(),
        "--demangle")
        .redirectErrorStream(true)
        .start();
    StringBuilder stdin = new StringBuilder();
    for (Long a : fileAddrs) {
      stdin.append("0x").append(Long.toHexString(a)).append('\n');
    }
    proc.getOutputStream().write(stdin.toString().getBytes(StandardCharsets.UTF_8));
    proc.getOutputStream().close();

    String out = readLimited(proc.getInputStream(), config.getMaxCaptureChars());
    String err = ""; // merged via redirectErrorStream
    boolean finished = proc.waitFor(config.getTimeoutSeconds(), TimeUnit.SECONDS);
    if (!finished) {
      proc.destroyForcibly();
      log.warn("{} llvm-symbolizer timed out addrs={}", LOG_PREFIX, fileAddrs.size());
      return symbols;
    }
    if (proc.exitValue() != 0) {
      log.warn("{} llvm-symbolizer exit={} stderrMergedLen={}",
          LOG_PREFIX, proc.exitValue(), Math.min(err.length(), 200));
    }
    symbols.addAll(parseSymbolizerOutput(out));
    return symbols;
  }

  /**
   * One symbol line + one file:line line per address; blank line between addresses (LLVM format).
   */
  public static List<String> parseSymbolizerOutput(String raw) {
    List<String> result = new ArrayList<>();
    String[] lines = raw.split("\\R");
    String sym = null;
    boolean addedForAddr = false;
    for (String ln : lines) {
      if (ln.isEmpty()) {
        if (sym != null && !addedForAddr) {
          result.add(sym);
        }
        sym = null;
        addedForAddr = false;
        continue;
      }
      if (ln.contains(":") && sym != null && !addedForAddr) {
        result.add(sym + " " + ln.trim());
        addedForAddr = true;
        sym = null;
      } else if (!ln.contains(":")) {
        sym = ln.trim();
      }
    }
    if (sym != null && !addedForAddr) {
      result.add(sym);
    }
    return result;
  }

  private static String readLimited(java.io.InputStream in, int maxChars) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = br.readLine()) != null) {
        if (sb.length() + line.length() > maxChars) {
          break;
        }
        sb.append(line).append('\n');
      }
    }
    return sb.toString();
  }

  public static String normalizeUuid(String withDashes) {
    return withDashes.replace("-", "").toLowerCase(Locale.ROOT);
  }

  private static String shortHash(String s) {
    if (s == null) {
      return "null";
    }
    int h = s.hashCode();
    return Integer.toHexString(h);
  }

  static Path extractDsymAndFindDwarf(byte[] zipBytes, Path destDir) throws IOException {
    Path zipFile = destDir.resolve("upload.zip");
    Files.write(zipFile, zipBytes);
    try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
      ZipEntry e;
      while ((e = zis.getNextEntry()) != null) {
        if (e.isDirectory()) {
          continue;
        }
        Path out = destDir.resolve(e.getName()).normalize();
        if (!out.startsWith(destDir)) {
          continue;
        }
        Files.createDirectories(out.getParent());
        Files.copy(zis, out);
      }
    }
    return findDwarfUnder(destDir);
  }

  static Path findDwarfUnder(Path root) throws IOException {
    final Path[] found = {null};
    Files.walkFileTree(root, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        String p = file.toString().replace('\\', '/');
        if (p.contains(".dSYM/Contents/Resources/DWARF/")
            && !p.endsWith("/") && attrs.isRegularFile()) {
          found[0] = file;
          return FileVisitResult.TERMINATE;
        }
        return FileVisitResult.CONTINUE;
      }
    });
    return found[0];
  }

  static void deleteRecursively(Path root) {
    try {
      Files.walkFileTree(root, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.deleteIfExists(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.deleteIfExists(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Value
  @Builder
  public static class IosLlvmConfig {
    @Builder.Default
    String llvmSymbolizerPath = firstEnv("LLVM_SYMBOLIZER_PATH", "llvm-symbolizer");
    @Builder.Default
    String llvmObjdumpPath = firstEnv("LLVM_OBJDUMP_PATH", "llvm-objdump");
    @Builder.Default
    long timeoutSeconds = parseLongEnv("LLVM_SUBPROCESS_TIMEOUT_SEC", 60L);
    @Builder.Default
    String defaultArch = firstEnv("LLVM_DEFAULT_ARCH", "arm64");
    @Builder.Default
    int maxCaptureChars = 2_000_000;

    private static String firstEnv(String key, String def) {
      String v = System.getenv(key);
      return (v == null || v.isBlank()) ? def : v.trim();
    }

    private static long parseLongEnv(String key, long def) {
      String v = System.getenv(key);
      if (v == null || v.isBlank()) {
        return def;
      }
      try {
        return Long.parseLong(v.trim());
      } catch (NumberFormatException e) {
        return def;
      }
    }
  }
}
