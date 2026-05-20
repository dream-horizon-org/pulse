package org.dreamhorizon.pulseserver.grouping.model;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class JsFrame extends Frame {
  private String jsFile;
  private String jsFunction;
  private Integer jsLine;
  private Integer jsColumn;

  @Builder
  public JsFrame(String jsFile,
                 String jsFunction,
                 Integer jsLine,
                 Integer jsColumn,
                 String rawLine,
                 Integer originalPosition) {
    super();
    this.lane = Lane.JS;
    this.token = String.join("#", jsFile, jsFunction);
    // FrameClassifier (Phase 2) sets the real category; legacy inApp defaults to false.
    this.inApp = false;
    this.rawLine = rawLine;
    this.originalPosition = (originalPosition != null) ? originalPosition : -1;
    this.jsFile = jsFile;
    this.jsFunction = jsFunction;
    this.jsLine = jsLine;
    this.jsColumn = jsColumn;
  }
}
