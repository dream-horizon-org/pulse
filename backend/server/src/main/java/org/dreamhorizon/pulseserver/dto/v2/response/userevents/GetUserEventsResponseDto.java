package org.dreamhorizon.pulseserver.dto.v2.response.userevents;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetUserEventsResponseDto {
    private List<ResponseEvent> events;
    private long count;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseEvent {
        private String eventId;
        private String eventName;
        private String eventTimestamp; // EPOCH as string or long
        private Map<String, Object> globalProps;
        private Map<String, Object> eventProps;
    }

}