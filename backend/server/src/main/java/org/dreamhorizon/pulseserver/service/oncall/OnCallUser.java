package org.dreamhorizon.pulseserver.service.oncall;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnCallUser {
  private String name;
  private String email;
}
