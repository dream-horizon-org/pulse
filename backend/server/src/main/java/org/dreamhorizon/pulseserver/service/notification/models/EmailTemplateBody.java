package org.dreamhorizon.pulseserver.service.notification.models;

import jakarta.validation.constraints.NotBlank;
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
public class EmailTemplateBody extends TemplateBody {

  @NotBlank(message = "subject is required for email templates")
  private String subject;

  private String html;

  private String text;
}
