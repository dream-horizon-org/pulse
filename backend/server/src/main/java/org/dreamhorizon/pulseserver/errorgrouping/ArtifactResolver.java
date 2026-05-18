package org.dreamhorizon.pulseserver.errorgrouping;

import java.nio.file.Path;
import java.util.Optional;
import org.dreamhorizon.pulseserver.grouping.model.EventMeta;

public class ArtifactResolver {
  public Optional<Path> findProguardMapping(EventMeta meta) {
    return Optional.empty();
  }

  public Optional<Path> findJsSourceMap(EventMeta meta) {
    return Optional.empty();
  }

  public Optional<Path> findNdkSymbols(EventMeta meta, String lib) {
    return Optional.empty();
  }
}
