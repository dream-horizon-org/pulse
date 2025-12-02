package in.horizonos.pulseserver.errorgrouping.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class Group {
  private String platform;     // js | java | android-ndk | unknown
  private String signature;    // v1|platform:...|exc:...|frames:...
  private String fingerprint;  // SHA-1 hex
  private String groupId;      // EXC-<first10>
  private String displayName;
}
