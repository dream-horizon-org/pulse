package in.horizonos.pulseserver.errorgrouping.utils;

import in.horizonos.pulseserver.errorgrouping.model.Lane;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorGroupingUtils {

  private static final String[] JS_OUT_OF_APP_CONTAINS = {"/node_modules/", "node_modules/"};

  // In-app rules (tweak for Dream11)
  private static final String[] JAVA_INAPP_PREFIXES = {""};

  // OPTIMIZATION: Reuse MessageDigest instances per thread for better performance
  private static final ThreadLocal<MessageDigest> SHA1_DIGEST = ThreadLocal.withInitial(() -> {
    try {
      return MessageDigest.getInstance("SHA-1");
    } catch (Exception e) {
      throw new RuntimeException("SHA-1 algorithm not available", e);
    }
  });

  public static int safeInt(String s) {
    try {
      return Integer.parseInt(s);
    } catch (Exception e) {
      return -1;
    }
  }

  public static String platformTag(Lane lane) {
    return switch (lane) {
      case JS -> "js";
      case JAVA -> "java";
      case NDK -> "android-ndk";
      default -> "unknown";
    };
  }

  public static boolean isJavaInApp(String classFqcn) {
    if (classFqcn == null) {
      return false;
    }
    for (String p : JAVA_INAPP_PREFIXES) {
      if (classFqcn.startsWith(p)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isJsInApp(String file) {
    String f = file.replace('\\', '/');
    for (String s : JS_OUT_OF_APP_CONTAINS) {
      if (f.contains(s)) {
        return false;
      }
    }
    return true;
  }

  public static String shortenJava(String token) {
    if (token == null || token.isBlank()) {
      return "";
    }
    int h = token.indexOf('#');
    String cls = h >= 0 ? token.substring(0, h) : token;
    String m = h >= 0 ? token.substring(h + 1) : "";
    String[] segs = cls.split("\\.");
    String base = (segs.length >= 2)
        ? segs[segs.length - 2] + "." + segs[segs.length - 1]
        : cls;
    return base + (m.isEmpty() ? "" : "#" + m);
  }

  public static String shortenJs(String token) {
    if (token == null || token.isBlank()) {
      return "";
    }
    int h = token.indexOf('#');
    String file = h >= 0 ? token.substring(0, h) : token;
    String fn = h >= 0 ? token.substring(h + 1) : "";
    String[] segs = file.replace('\\', '/').split("/");
    String base = (segs.length >= 2) ? segs[segs.length - 2] + "/" + segs[segs.length - 1] : file;
    return base + (fn.isEmpty() ? "" : "#" + fn);
  }

  public static String sha1Hex(String s) {
    // OPTIMIZATION: Reuse MessageDigest instance from ThreadLocal
    MessageDigest md = SHA1_DIGEST.get();
    md.reset();  // Reset for reuse
    return toHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
  }

  public static String toHex(byte[] b) {
    StringBuilder sb = new StringBuilder(b.length * 2);
    for (byte x : b) {
      sb.append(String.format("%02x", x));
    }
    return sb.toString();
  }

  public static String normalizeJsFunction(String fn) {
    if (fn == null) {
      return "anonymous";
    }
    String f = fn.trim();
    if (f.startsWith("bound ")) {
      f = f.substring("bound ".length());
    }
    if (f.isEmpty() || "<anonymous>".equals(f)) {
      f = "anonymous";
    }
    return f;
  }

  public static String sanitizeJsFile(String file) {
    if (file == null) {
      return "unknown";
    }
    String f = file.trim();
    int q = f.indexOf('?');
    if (q >= 0) {
      f = f.substring(0, q);
    }
    int h = f.indexOf('#');
    if (h >= 0) {
      f = f.substring(0, h);
    }
    return f;
  }
}
