package org.dreamhorizon.pulseserver.dto.v1.response;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class GetJobDetailsResponseDto {
  private String useCaseName;
  private int upTimeLowerLimit;
  private String eventNameT1;
  private String eventNameT2;
  private Map<String, List<String>> blacklistedEvents;
  private int upTimeUpperLimit;
  private int upTimeMidLimit;
  private List<String> globalBlacklistedEvents;
  private List<String> eventSeq;
  private Map<String, List<Map<String, String>>> propsFilter;
  private Metadata metaData;
  private String jobVersion;
}
