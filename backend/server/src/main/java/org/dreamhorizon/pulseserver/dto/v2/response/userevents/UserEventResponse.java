package org.dreamhorizon.pulseserver.dto.v2.response.userevents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserEventResponse {
    private String eventName;
    private String eventTimestamp;
    private Map<String, Object> props;
} 