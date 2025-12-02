package org.dreamhorizon.pulseserver.errorgrouping.model;

import static org.dreamhorizon.pulseserver.errorgrouping.FramesParser.NDK_INAPP_LIBS;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NDKFrame extends Frame {
  // NDK
  private String ndkLib;
  private String ndkPc;
  private String ndkSymbol;

  @Builder
  public NDKFrame(String ndkLib,
                  String ndkPc,
                  String ndkSymbol,
                  String rawLine,
                  Integer originalPosition) {
    super();
    this.lane = Lane.NDK;
    this.token = String.join("#", ndkLib, ndkSymbol == null ? "addr" : ndkSymbol);
    this.inApp = NDK_INAPP_LIBS.contains(ndkLib);
    this.rawLine = rawLine;
    this.originalPosition = (originalPosition != null) ? originalPosition : -1;
    this.ndkLib = ndkLib;
    this.ndkPc = ndkPc;
    this.ndkSymbol = ndkSymbol;
  }
}
