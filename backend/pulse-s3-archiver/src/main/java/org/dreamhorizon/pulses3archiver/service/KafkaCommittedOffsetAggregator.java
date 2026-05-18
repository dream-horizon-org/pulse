package org.dreamhorizon.pulses3archiver.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Kafka commit positions when combining many sinks. A partition commits only once every sink that
 * has seen records for it has flushed at least through that Kafka offset (per Parquet flush).
 */
public final class KafkaCommittedOffsetAggregator {

  private KafkaCommittedOffsetAggregator() {
    throw new UnsupportedOperationException("Utility class");
  }

  /** Union of partitions; min committed among writers {@link ParquetBatchWriter#tracksPartition}. */
  public static Map<Integer, Long> mergeAcrossWriters(Collection<ParquetBatchWriter> writers) {
    Set<Integer> partitions = new HashSet<>();
    for (ParquetBatchWriter w : writers) {
      partitions.addAll(w.getPartitionsTracked());
    }
    Map<Integer, Long> commit = new HashMap<>();
    for (int partition : partitions) {
      Long minCommitted = null;
      for (ParquetBatchWriter w : writers) {
        if (!w.tracksPartition(partition)) {
          continue;
        }
        Long c = w.getCommittedOffsets().get(partition);
        if (c == null) {
          minCommitted = null;
          break;
        }
        minCommitted = minCommitted == null ? c : Math.min(minCommitted, c);
      }
      if (minCommitted != null) {
        commit.put(partition, minCommitted);
      }
    }
    return commit;
  }
}
