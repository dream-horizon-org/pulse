package org.dreamhorizon.pulseserver.service.notification.models;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EmailTemplateBody.class, name = "EMAIL"),
  @JsonSubTypes.Type(value = SlackTemplateBody.class, name = "SLACK"),
  @JsonSubTypes.Type(value = SlackTemplateBody.class, name = "SLACK_WEBHOOK"),
  @JsonSubTypes.Type(value = TeamsTemplateBody.class, name = "TEAMS")
})
public abstract class TemplateBody {}
