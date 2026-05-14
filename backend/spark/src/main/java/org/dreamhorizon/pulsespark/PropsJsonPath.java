package org.dreamhorizon.pulsespark;

/**
 * Builds Spark {@code get_json_object} JSON paths for top-level keys on a JSON object column (e.g.
 * {@code props}).
 */
public final class PropsJsonPath {

  private PropsJsonPath() {}

  /**
   * Returns a path suitable for {@code get_json_object(col("props"), path)} for a single top-level
   * key. Uses {@code $.identifier} when safe; otherwise {@code $['…']} with SQL-style single-quote
   * escaping.
   */
  public static String forTopLevelKey(String key) {
    if (key == null || key.isEmpty()) {
      return "$";
    }
    boolean simple = key.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_')
        && (Character.isLetter(key.charAt(0)) || key.charAt(0) == '_');
    if (simple) {
      return "$." + key;
    }
    return "$['" + key.replace("\\", "\\\\").replace("'", "''") + "']";
  }
}
