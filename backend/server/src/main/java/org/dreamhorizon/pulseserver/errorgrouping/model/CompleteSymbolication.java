package org.dreamhorizon.pulseserver.errorgrouping.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Holds the complete symbolication result for all lanes.
 * Used to reconstruct the full stack trace with symbolicated frames.
 */
@Data
@AllArgsConstructor
public class CompleteSymbolication {
  private ParsedFrames originalFrames;
  private List<String> symbolicatedJsFrames;
  private List<String> symbolicatedJavaFrames;
  private List<String> symbolicatedNdkFrames;

  /**
   * Reconstructs the complete stack trace with symbolicated frames.
   * PRESERVES ORIGINAL ORDER by sorting frames by their original position.
   * Note: Java frames from retrace may expand (due to inlining), so they are
   * grouped together at the position of the first original Java frame.
   */
  public String reconstructStackTrace() {
    StringBuilder result = new StringBuilder();

    // Add exception header line (full line with type and message)
    if (originalFrames.getExceptionHeaderLine() != null) {
      result.append(originalFrames.getExceptionHeaderLine()).append("\n");
    } else {
      // Fallback: just use the exception type if header line wasn't captured
      if (!originalFrames.getJsTypes().isEmpty()) {
        result.append(originalFrames.getJsTypes().get(0)).append("\n");
      } else if (!originalFrames.getJavaTypes().isEmpty()) {
        result.append(originalFrames.getJavaTypes().get(0)).append("\n");
      } else if (!originalFrames.getNdkTypes().isEmpty()) {
        result.append(originalFrames.getNdkTypes().get(0)).append("\n");
      }
    }

    // Build list of all frames with their original positions
    List<PositionedFrame> allFrames = new ArrayList<>();

    // JS frames - preserve exact positions (1-to-1 mapping, no expansion)
    for (int i = 0; i < originalFrames.getJsFrames().size(); i++) {
      JsFrame frame = originalFrames.getJsFrames().get(i);
      String symbolicatedFrame = i < symbolicatedJsFrames.size()
          ? symbolicatedJsFrames.get(i)
          : frame.getRawLine();
      allFrames.add(new PositionedFrame(frame.getOriginalPosition(), Lane.JS, symbolicatedFrame));
    }

    // Java frames - group together at first Java frame's position
    // (retrace can expand frames due to inlining, so we can't maintain 1-to-1 mapping)
    if (!originalFrames.getJavaFrames().isEmpty() && !symbolicatedJavaFrames.isEmpty()) {
      int firstJavaPos = originalFrames.getJavaFrames().get(0).getOriginalPosition();

      // Add all symbolicated Java frames at this position
      // They'll appear grouped together when sorted
      for (String javaFrame : symbolicatedJavaFrames) {
        allFrames.add(new PositionedFrame(firstJavaPos, Lane.JAVA, javaFrame));
      }
    } else {
      // Fallback: use raw frames if no symbolication occurred
      for (JavaFrame frame : originalFrames.getJavaFrames()) {
        allFrames.add(new PositionedFrame(
            frame.getOriginalPosition(),
            Lane.JAVA,
            frame.getRawLine()
        ));
      }
    }

    // NDK frames - preserve exact positions (typically 1-to-1, no expansion expected)
    for (int i = 0; i < originalFrames.getNdkFrames().size(); i++) {
      NdkFrame frame = originalFrames.getNdkFrames().get(i);
      String symbolicatedFrame = i < symbolicatedNdkFrames.size()
          ? symbolicatedNdkFrames.get(i)
          : frame.getRawLine();
      allFrames.add(new PositionedFrame(frame.getOriginalPosition(), Lane.NDK, symbolicatedFrame));
    }

    // Sort by original position to preserve order
    allFrames.sort(Comparator.comparingInt(f -> f.position));

    // Append frames in original order
    for (PositionedFrame pf : allFrames) {
      if (pf.lane == Lane.JS) {
        result.append("  at ").append(pf.symbolicatedFrame).append("\n");
      } else {
        result.append("  ").append(pf.symbolicatedFrame).append("\n");
      }
    }

    return result.toString();
  }

  /**
   * Helper class to hold a frame with its symbolicated version and original position.
   */
  private static class PositionedFrame {
    int position;
    Lane lane;
    String symbolicatedFrame;

    PositionedFrame(int position, Lane lane, String symbolicatedFrame) {
      this.position = position;
      this.lane = lane;
      this.symbolicatedFrame = symbolicatedFrame;
    }
  }
}

