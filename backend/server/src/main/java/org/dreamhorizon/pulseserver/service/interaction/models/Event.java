package org.dreamhorizon.pulseserver.service.interaction.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.DefaultValue;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Event {
  @NotBlank(message = "Event name cannot be blank")
  private String name;
  private List<Prop> props;

  @Builder.Default
  @DefaultValue(value = "false")
  private Boolean isBlacklisted = false;

  @Getter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Prop {
    @NotBlank(message = "prop name cannot be blank")
    private String name;

    @NotBlank(message = "prov value cannot be blank")
    private String value;

    @Builder.Default
    @DefaultValue(value = "EQUALS")
    private Operator operator = Operator.EQUALS;
  }

  public enum Operator {
    EQUALS, NOTCONTAINS, STARTSWITH, ENDSWITH, NOTEQUALS, CONTAINS;

    public static Operator fromString(String name) {
      if (name == null) {
        return null;
      }

      for (Operator s : values()) {
        if (s.name().equalsIgnoreCase(name.trim())) {
          return s;
        }
      }
      throw new IllegalArgumentException("No enum constant InteractionStatus." + name);
    }

    @JsonCreator
    public static Operator fromJson(String name) {
      return fromString(name);
    }
  }
}