package org.dreamhorizon.pulseserver.dto.v1.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateAndDeleteRelationRequestDto {

  @JsonProperty("write")
  public RelationRequestDto[] write;

  @JsonProperty("delete")
  public RelationRequestDto[] delete;
}
