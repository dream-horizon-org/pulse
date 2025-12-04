package org.dreamhorizon.pulseserver.dto.v2.response.userevents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetUserEventsByTimeRangeDto {
    private List<ResponseEvent> userEvents;
    private String pageToken;
    private String totalRows;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseEvent {
        private String eventName;
        private String eventTimeStamp;
    }
}
