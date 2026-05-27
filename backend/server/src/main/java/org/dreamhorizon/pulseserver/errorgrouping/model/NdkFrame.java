package org.dreamhorizon.pulseserver.errorgrouping.model;

import static org.dreamhorizon.pulseserver.errorgrouping.FramesParser.NDK_INAPP_LIBS;

import lombok.Builder;
import org.dreamhorizon.pulseserver.errorgrouping.apple.AppleCrashReportParser;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NdkFrame extends Frame {
  // NDK / iOS native (Mach-O) — same shape: binary name, PC, optional symbol from raw line
  private String ndkLib;
  private String ndkPc;
  private String ndkSymbol;

  @Builder
  public NdkFrame(String ndkLib,
                  String ndkPc,
                  String ndkSymbol,
                  String rawLine,
                  Integer originalPosition,
                  Lane lane,
                  String iosAppBinaryName) {
    super();
    Lane resolvedLane = lane != null ? lane : Lane.NDK;
    this.lane = resolvedLane;
    this.token = String.join("#", ndkLib, ndkSymbol == null ? "addr" : ndkSymbol);
    if (resolvedLane == Lane.IOS_NATIVE && iosAppBinaryName != null) {
      this.inApp = AppleCrashReportParser.frameImageMatchesProcess(iosAppBinaryName, ndkLib);
    } else {
      this.inApp = NDK_INAPP_LIBS.contains(ndkLib);
    }
    this.rawLine = rawLine;
    this.originalPosition = (originalPosition != null) ? originalPosition : -1;
    this.ndkLib = ndkLib;
    this.ndkPc = ndkPc;
    this.ndkSymbol = ndkSymbol;
  }
}
