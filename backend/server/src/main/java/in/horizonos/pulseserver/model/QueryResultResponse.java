package in.horizonos.pulseserver.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QueryResultResponse<T> {
  List<T> rows;
  private Boolean jobComplete;
  private JobReference jobReference;
}
