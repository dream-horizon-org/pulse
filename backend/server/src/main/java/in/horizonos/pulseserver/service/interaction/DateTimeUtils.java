package in.horizonos.pulseserver.service.interaction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DateTimeUtils {

  private static final Pattern ONE_TOKEN = Pattern.compile("^(\\d+)\\s*([smhdwM])$");

  /**
   * Converts "1m", "5s", "1h", "1d" to seconds.
   */
  public static long toSeconds(String input) {
    if (input == null) {
      throw new IllegalArgumentException("Interval is null");
    }
    Matcher m = ONE_TOKEN.matcher(input.trim());
    if (!m.matches()) {
      throw new IllegalArgumentException("Invalid interval: " + input);
    }

    long value = Long.parseLong(m.group(1));
    char unit = m.group(2).charAt(0);

    return switch (unit) {
      case 's' -> value;
      case 'm' -> Math.multiplyExact(value, 60L);
      case 'h' -> Math.multiplyExact(value, 3_600L);
      case 'd' -> Math.multiplyExact(value, 86_400L);
      case 'w' -> Math.multiplyExact(value, 604_800L);
      case 'M' -> Math.multiplyExact(value, 2_629_746L);
      default -> throw new IllegalArgumentException("Unknown unit: " + unit);
    };
  }
}
