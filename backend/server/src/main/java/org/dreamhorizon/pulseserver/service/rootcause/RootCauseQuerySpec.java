package org.dreamhorizon.pulseserver.service.rootcause;

import java.util.List;

/** ClickHouse SELECT with positional parameters ({@code ?}) for R2DBC binding (1-based bind indices). */
public record RootCauseQuerySpec(String sql, List<Object> bindParameters) {

  public RootCauseQuerySpec {
    bindParameters = bindParameters == null ? List.of() : List.copyOf(bindParameters);
  }
}
