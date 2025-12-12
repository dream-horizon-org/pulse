package org.dreamhorizon.pulseserver.dto.v1.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class EventsMetadata {
    private String eventName;
    private String description;
    private List<String> screenNames;
    private Boolean archived;
    private Boolean isActive;
}
