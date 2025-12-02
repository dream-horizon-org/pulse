package in.horizonos.pulseserver.errorgrouping.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public abstract class Frame {
  protected Lane lane;
  // Common normalized token after (optional) symbolication
  protected String token;     // java: pkg.Class#method ; js: file#function ; ndk: lib.so#symbol
  protected boolean inApp;
  protected String rawLine;
  // Track original position in stack trace for reconstruction after symbolication
  protected int originalPosition = -1;
}
