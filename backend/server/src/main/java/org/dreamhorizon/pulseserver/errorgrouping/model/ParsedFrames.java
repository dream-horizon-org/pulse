package org.dreamhorizon.pulseserver.errorgrouping.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ParsedFrames {
  @Builder.Default
  private List<String> javaTypes = new ArrayList<>();
  @Builder.Default
  private List<String> jsTypes = new ArrayList<>();
  @Builder.Default
  private List<String> ndkTypes = new ArrayList<>();
  @Builder.Default
  private List<JavaFrame> javaFrames = new ArrayList<>();
  @Builder.Default
  private List<JSFrame> jsFrames = new ArrayList<>();
  @Builder.Default
  private List<NDKFrame> ndkFrames = new ArrayList<>();
  // Flag to indicate if this is a React Native JavaScript exception
  @Builder.Default
  private boolean isReactNativeJsException = false;
  // Track which lane's exception type was seen first (topmost in stack trace)
  @Builder.Default
  private Lane primaryExceptionLane = null;
  // Store the original exception header line (first line with exception type and message)
  @Builder.Default
  private String exceptionHeaderLine = null;
}
