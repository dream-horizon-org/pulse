import type { FilterConfigResponse } from "../../../../services/sessionReplay/types";
import type {
  FilterCategory,
  FilterFieldDefinition,
  FilterOperator,
} from "../../../../services/sessionReplay/filterConfig";

export interface AdaptedSchema {
  categories: Array<{
    key: string;
    label: string;
    fields: FilterFieldDefinition[];
  }>;
}

/**
 * Adapt GET /v1/sessions/filters response to the shape expected by
 * AdvancedFilterBuilder (categories with FilterFieldDefinition[]).
 */
export function adaptSessionsFilterConfig(
  config: FilterConfigResponse,
): AdaptedSchema {
  return {
    categories: config.advanced.map((c) => ({
      key: c.categoryKey,
      label: c.displayName,
      fields: c.fields.map((f) => ({
        key: f.key,
        label: f.displayName,
        category: c.categoryKey as FilterCategory,
        type: (f.dataType === "integer" || f.dataType === "float"
          ? "number"
          : "string") as "string" | "number" | "boolean" | "date" | "enum",
        operators: f.allowedOperators.map((o) => o.key) as FilterOperator[],
      })),
    })),
  };
}

/**
 * Build operator key → label map from sessions filter config (for condition row labels).
 */
export function buildOperatorLabelsFromConfig(
  config: FilterConfigResponse | null | undefined,
): Record<string, string> | undefined {
  if (!config) return undefined;
  const map: Record<string, string> = {};
  for (const c of config.advanced) {
    for (const f of c.fields) {
      for (const o of f.allowedOperators) map[o.key] = o.label;
    }
  }
  return map;
}
