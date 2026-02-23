package org.dreamhorizon.pulseserver.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class JwtUtils {

  public String jwtIssuer(String idTokenString) {
    if (idTokenString == null || idTokenString.isEmpty()) {
      return null;
    }
    String[] parts = idTokenString.split("\\.");
    if (parts.length != 3) {
      return null;
    }
    try {
      String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
      int issStart = payloadJson.indexOf("\"iss\"");
      if (issStart == -1) {
        return null;
      }
      int colon = payloadJson.indexOf(':', issStart);
      int valueStart = payloadJson.indexOf('"', colon + 1) + 1;
      int valueEnd = payloadJson.indexOf('"', valueStart);
      if (valueStart <= 0 || valueEnd <= valueStart) {
        return null;
      }
      return payloadJson.substring(valueStart, valueEnd);
    } catch (Exception e) {
      log.warn("Unable to identify the jwt issuer - ", e);
      return null;
    }
  }

  public boolean isFirebaseIssuer(String iss) {
    return iss != null && iss.contains("securetoken.google.com");
  }
}
