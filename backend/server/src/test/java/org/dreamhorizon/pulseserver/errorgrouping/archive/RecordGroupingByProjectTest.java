package org.dreamhorizon.pulseserver.errorgrouping.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RecordGroupingByProjectTest {

  private static Schema schema;

  @BeforeAll
  static void loadSchema() throws Exception {
    try (InputStream in = RecordGroupingByProjectTest.class.getClassLoader()
        .getResourceAsStream("schemas/stack_trace_events.avsc")) {
      schema = new Schema.Parser().parse(in);
    }
  }

  private static GenericRecord recordWithProjectId(String projectId) {
    GenericRecord rec = new GenericData.Record(schema);
    rec.put("ProjectId", projectId);
    return rec;
  }

  @Nested
  class PartitionByProjectId {

    @Test
    void shouldGroupRecordsByProjectId() {
      GenericRecord a1 = recordWithProjectId("proj-a");
      GenericRecord a2 = recordWithProjectId("proj-a");
      GenericRecord b1 = recordWithProjectId("proj-b");

      Map<String, List<GenericRecord>> grouped =
          RecordGroupingByProject.partitionByProjectId(List.of(a1, a2, b1));

      assertThat(grouped).hasSize(2);
      assertThat(grouped.get("proj-a")).containsExactly(a1, a2);
      assertThat(grouped.get("proj-b")).containsExactly(b1);
    }

    @Test
    void shouldUseEmptyKeyForNullOrBlankProjectId() {
      GenericRecord nullPid = recordWithProjectId(null);
      GenericRecord blankPid = recordWithProjectId("  ");

      Map<String, List<GenericRecord>> grouped =
          RecordGroupingByProject.partitionByProjectId(List.of(nullPid, blankPid));

      assertThat(grouped).hasSize(1);
      assertThat(grouped.get("")).containsExactly(nullPid, blankPid);
    }

    @Test
    void shouldReturnEmptyMapForEmptyInput() {
      assertThat(RecordGroupingByProject.partitionByProjectId(Collections.emptyList())).isEmpty();
    }
  }
}
