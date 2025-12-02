package org.dreamhorizon.pulseserver.resources.interaction.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.DefaultValue;
import java.sql.Timestamp;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.resources.interaction.validators.CreateInteractionValidations;
import org.dreamhorizon.pulseserver.resources.interaction.validators.UpdateInteractionValidations;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RestInteractionDetail {

  private Long id;

  @NotBlank(message = "Interaction name cannot be blank", groups = {CreateInteractionValidations.class})
  private String name;

  @NotBlank(message = "Description cannot be blank", groups = {CreateInteractionValidations.class})
  private String description;

  @NotNull(groups = {CreateInteractionValidations.class})
  private Integer uptimeLowerLimitInMs;

  @NotNull(groups = {CreateInteractionValidations.class})
  private Integer uptimeMidLimitInMs;

  @NotNull(groups = {CreateInteractionValidations.class})
  private Integer uptimeUpperLimitInMs;

  @NotNull(groups = {CreateInteractionValidations.class})
  private Integer thresholdInMs;

  private String status;

  @NotNull(message = "Event sequence cannot be null", groups = {CreateInteractionValidations.class})
  @Size(
      min = 2,
      message = "Event Sequence must have at least two element",
      groups = {CreateInteractionValidations.class, UpdateInteractionValidations.class})
  private List<Event> events;

  @NotNull(message = "Global blacklisted events cannot be null", groups = {CreateInteractionValidations.class})
  private List<Event> globalBlacklistedEvents = List.of();

  private Timestamp createdAt;
  private String createdBy;
  private Timestamp updatedAt;
  private String updatedBy;

  @Getter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Event {

    @NotBlank(message = "Event name cannot be blank", groups = {CreateInteractionValidations.class, UpdateInteractionValidations.class})
    private String name;
    private List<Prop> props;

    @DefaultValue(value = "false")
    @Builder.Default
    private @NotNull Boolean isBlacklisted = false;
  }

  @Getter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Prop {
    @NotBlank(message = "prop name cannot be blank", groups = {CreateInteractionValidations.class, UpdateInteractionValidations.class})
    private String name;

    @NotBlank(message = "prov value cannot be blank", groups = {CreateInteractionValidations.class, UpdateInteractionValidations.class})
    private String value;

    @NotBlank(message = "operator cannot be blank", groups = {CreateInteractionValidations.class, UpdateInteractionValidations.class})
    @DefaultValue(value = "EQUALS")
    @Builder.Default
    private String operator = "EQUALS";
  }
}
