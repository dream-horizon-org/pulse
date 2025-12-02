package in.horizonos.pulseserver.errorgrouping;

import in.horizonos.pulseserver.errorgrouping.model.EventMeta;
import java.nio.file.Path;
import java.util.Optional;

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
