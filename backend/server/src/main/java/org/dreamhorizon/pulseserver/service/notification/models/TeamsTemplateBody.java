package org.dreamhorizon.pulseserver.service.notification.models;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TeamsTemplateBody extends TemplateBody {

  private String title;

  private String text;

  private JsonNode body;

  private JsonNode actions;
}
