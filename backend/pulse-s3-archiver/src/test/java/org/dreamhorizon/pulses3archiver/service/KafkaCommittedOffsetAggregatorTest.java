package org.dreamhorizon.pulses3archiver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KafkaCommittedOffsetAggregatorTest {

  @Test
  void mergeShouldStallUntilAllTouchesHaveCommittedSamePartition() {
    ParquetBatchWriter wa = mock(ParquetBatchWriter.class);
    ParquetBatchWriter wb = mock(ParquetBatchWriter.class);

    when(wa.getPartitionsTracked()).thenReturn(Set.of(0));
    when(wb.getPartitionsTracked()).thenReturn(Set.of(0));

    when(wa.tracksPartition(0)).thenReturn(true);
    when(wb.tracksPartition(0)).thenReturn(true);

    when(wa.getCommittedOffsets()).thenReturn(Map.of(0, 5L));
    when(wb.getCommittedOffsets()).thenReturn(Map.of());

    assertThat(KafkaCommittedOffsetAggregator.mergeAcrossWriters(List.of(wa, wb))).isEmpty();

    when(wb.getCommittedOffsets()).thenReturn(Map.of(0, 7L));

    assertThat(KafkaCommittedOffsetAggregator.mergeAcrossWriters(List.of(wa, wb)))
        .containsEntry(0, 5L);
  }

  @Test
  void mergeShouldNotRequireWritersThatNeverSeenPartitionZero() {

    ParquetBatchWriter wa = mock(ParquetBatchWriter.class);
    ParquetBatchWriter wb = mock(ParquetBatchWriter.class);

    when(wa.getPartitionsTracked()).thenReturn(Set.of(0));
    when(wb.getPartitionsTracked()).thenReturn(Set.of(1));

    when(wa.tracksPartition(0)).thenReturn(true);
    when(wb.tracksPartition(0)).thenReturn(false);
    when(wa.getCommittedOffsets()).thenReturn(Map.of(0, 12L));

    assertThat(KafkaCommittedOffsetAggregator.mergeAcrossWriters(List.of(wa, wb)))
        .containsEntry(0, 12L);
  }
}
