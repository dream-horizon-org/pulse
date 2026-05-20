package org.dreamhorizon.pulseserver.grouping.model;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class JavaFrame extends Frame {
  private String javaClass;
  private String javaMethod;
  private String javaFile;
  private Integer javaLine;

  @Builder
  public JavaFrame(String javaClass,
                   String javaMethod,
                   String javaFile,
                   Integer javaLine,
                   String rawLine,
                   Integer originalPosition) {
    super();
    this.lane = Lane.JAVA;
    this.token = String.join("#", javaClass, javaMethod);
    // FrameClassifier (Phase 2) sets the real category; legacy inApp defaults to false.
    this.inApp = false;
    this.rawLine = rawLine;
    this.originalPosition = (originalPosition != null) ? originalPosition : -1;
    this.javaClass = javaClass;
    this.javaMethod = javaMethod;
    this.javaFile = javaFile;
    this.javaLine = javaLine;
  }
}
