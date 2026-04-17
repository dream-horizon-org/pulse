package org.dreamhorizon.pulseserver.service.rootcause;

import java.util.List;

/**
 * ClickHouse SELECT for root-cause analysis with named R2DBC parameters ({@code :param}) as required by
 * {@code clickhouse-r2dbc}.
 */
public record RootCauseQuerySpec(String sql, List<String> bindNames, List<Object> bindValues) {

  public RootCauseQuerySpec {
    bindNames = bindNames == null ? List.of() : List.copyOf(bindNames);
    bindValues = bindValues == null ? List.of() : List.copyOf(bindValues);
    boolean sizesMismatch = bindNames.size() != bindValues.size();
    if (sizesMismatch) {
      throw new IllegalArgumentException("bindNames and bindValues must have the same size");
    }
  }
}
