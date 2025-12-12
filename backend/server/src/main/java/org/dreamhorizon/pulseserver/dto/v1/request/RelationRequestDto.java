package org.dreamhorizon.pulseserver.dto.v1.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RelationRequestDto {

  @NotNull
  @JsonProperty("entity")
  public EntityRequestDto entity;

  @NotNull
  @JsonProperty("subject")
  public SubjectRequestDto subject;

  // Relations can be following: owner, org, manager, superadmin, write, delete
  @NotNull
  @JsonProperty("relation")
  public String relation;
}
