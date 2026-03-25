import type { FilterConfigResponse } from "../../../services/sessionReplay/types";

export function getInteractionFilterFieldFromConfig(
  config: FilterConfigResponse | null | undefined,
): { fieldKey: string; categoryKey: string } | null {
  if (!config?.advanced?.length) return null;

  const criticalInteractionKey = "critical_interaction.name";
  const displayNameLower = "critical interaction";

  for (const category of config.advanced) {
    for (const field of category.fields) {
      const keyMatch =
        field.key === criticalInteractionKey ||
        field.key.includes("critical_interaction");
      const labelMatch = field.displayName
        ?.toLowerCase()
        .includes(displayNameLower);
      if (keyMatch || labelMatch) {
        return {
          fieldKey: field.key,
          categoryKey: category.categoryKey,
        };
      }
    }
  }
  return null;
}
