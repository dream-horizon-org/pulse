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

  public static final String LOG_PREFIX = "[PULSE NATIVE SYM]";

  @com.google.inject.Inject
  public IosLlvmSymbolicator() {
    this(IosLlvmConfig.builder().build());
  }

  private static final Pattern VMADDR_AFTER_TEXT =
      Pattern.compile("(?is)segname\\s+__TEXT.*?vmaddr\\s+(0x[0-9a-fA-F]+)");
  private static final Pattern UUID_LINE =
      Pattern.compile("UUID:\\s*([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})", Pattern.CASE_INSENSITIVE);

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

      String objdumpUuid = runObjdumpUuid(dwarfPath);
      if (crashUuid.isPresent()) {
        if (objdumpUuid == null || objdumpUuid.isEmpty()) {
          log.warn("{} llvm-objdump did not return UUID; proceeding without UUID check", LOG_PREFIX);
        } else if (!crashUuid.get().equals(objdumpUuid)) {
          log.error("{} dSYM UUID mismatch crash={} dwarf={} — skipping LLVM symbolication",
              LOG_PREFIX, shortHash(crashUuid.get()), shortHash(objdumpUuid));
          return rawLines(frames);
        }
      } else {
        log.warn("{} crash report missing UUID for app binary {}; proceeding", LOG_PREFIX, appBinary);
      }

      if (loadOpt.isEmpty()) {
        log.warn("{} could not parse load address for {}; skipping LLVM", LOG_PREFIX, appBinary);
        return rawLines(frames);
      }
      long load = loadOpt.get();

      Long vmaddr = parseVmaddrFromPrivateHeaders(dwarfPath);
      if (vmaddr == null) {
        log.error("{} could not parse __TEXT vmaddr from llvm-objdump; skipping LLVM", LOG_PREFIX);
        return rawLines(frames);
      }

      List<Integer> appIndices = new ArrayList<>();
      List<Long> fileAddrs = new ArrayList<>();
      for (int i = 0; i < frames.size(); i++) {
        NdkFrame f = frames.get(i);
        if (appBinary.isEmpty() || !appBinary.equals(f.getNdkLib())) {
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

      List<String> llvmLines = runLlvmSymbolizer(dwarfPath, fileAddrs);
      int symIdx = 0;
      for (int i = 0; i < frames.size(); i++) {
        NdkFrame f = frames.get(i);
        if (symIdx < appIndices.size() && appIndices.get(symIdx) == i) {
          String pretty = symIdx < llvmLines.size() ? llvmLines.get(symIdx) : f.getRawLine();
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

  private static List<String> rawLines(List<NdkFrame> frames) {
    List<String> r = new ArrayList<>(frames.size());
    for (NdkFrame f : frames) {
      r.add(f.getRawLine());
    }
    return r;
  }

  private String runObjdumpUuid(Path dwarfPath) throws IOException, InterruptedException {
    Process proc = new ProcessBuilder(
        config.getLlvmObjdumpPath(),
        "--macho",
        "--uuid",
        dwarfPath.toAbsolutePath().toString())
        .redirectErrorStream(true)
        .start();
    String output = readLimited(proc.getInputStream(), config.getMaxCaptureChars());
    boolean finished = proc.waitFor(config.getTimeoutSeconds(), TimeUnit.SECONDS);
    if (!finished) {
      proc.destroyForcibly();
      log.warn("{} llvm-objdump --uuid timed out", LOG_PREFIX);
      return null;
    }
    Matcher m = UUID_LINE.matcher(output);
    if (m.find()) {
      return normalizeUuid(m.group(1));
    }
    return null;
  }

  private Long parseVmaddrFromPrivateHeaders(Path dwarfPath) throws IOException, InterruptedException {
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
      log.warn("{} llvm-objdump private-headers timed out", LOG_PREFIX);
      return null;
    }
    Matcher m = VMADDR_AFTER_TEXT.matcher(output);
    if (m.find()) {
      return AppleCrashReportParser.parseHexLong(m.group(1));
    }
    return null;
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
