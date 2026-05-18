package org.dreamhorizon.pulseserver.errorgrouping.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Sets {@code hadoop.home.dir} so Parquet/Hadoop can write in unit tests (e.g. CI without HADOOP_HOME). */
final class ParquetTestSupport {

  private static volatile boolean initialized;

  private ParquetTestSupport() {
    throw new UnsupportedOperationException("Utility class");
  }

  static void ensureHadoopHome() throws IOException {
    if (initialized) {
      return;
    }
    synchronized (ParquetTestSupport.class) {
      if (initialized) {
        return;
      }
      Path home = Files.createTempDirectory("pulse-hadoop-home");
      Files.createDirectories(home.resolve("bin"));
      System.setProperty("hadoop.home.dir", home.toAbsolutePath().toString());
      initialized = true;
    }
  }
}
