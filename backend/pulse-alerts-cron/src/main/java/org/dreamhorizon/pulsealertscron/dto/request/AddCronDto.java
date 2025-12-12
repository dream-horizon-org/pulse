package org.dreamhorizon.pulsealertscron.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.constraints.NotNull;

@Data
@Slf4j
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddCronDto {
    @NotNull
    @JsonProperty(value = "id")
    private Integer id;

    @NotNull
    @JsonProperty(value = "url")
    private String url;

    @NotNull
    @JsonProperty(value = "interval")
    private Integer interval;
}
