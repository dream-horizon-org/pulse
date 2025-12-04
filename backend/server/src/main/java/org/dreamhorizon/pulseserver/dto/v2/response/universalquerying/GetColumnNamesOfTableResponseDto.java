package org.dreamhorizon.pulseserver.dto.v2.response.universalquerying;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetColumnNamesOfTableResponseDto {
    private String name;
    private String type;
}
