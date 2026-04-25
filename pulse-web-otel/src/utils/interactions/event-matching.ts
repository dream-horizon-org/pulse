import type {
  InteractionEvent,
  PropertyFilter,
} from "../../interactions/interaction-models";
import type { InteractionLocalEvent } from "../../types/interactions/interaction-runtime";

function normalizeOperator(op: string): string {
  const u = op.toUpperCase().replace(/_/g, "");
  if (u === "NOTEQUALS" || u === "NE") return "NOTEQUALS";
  if (u === "NOTCONTAINS") return "NOTCONTAINS";
  if (u === "STARTSWITH" || u === "STARTS_WITH") return "STARTSWITH";
  if (u === "ENDSWITH" || u === "ENDS_WITH") return "ENDSWITH";
  if (u === "EQUALS") return "EQUALS";
  if (u === "CONTAINS") return "CONTAINS";
  return op.toUpperCase().replace(/_/g, "");
}

/** Android InteractionUtil.matchPropValue parity. */
export function matchPropValue(
  expectedValue: string,
  operatorRaw: string,
  actualValue: string,
): boolean {
  const operator = normalizeOperator(operatorRaw);
  switch (operator) {
    case "EQUALS":
      return actualValue === expectedValue;
    case "NOTEQUALS":
      return actualValue !== expectedValue;
    case "CONTAINS":
      return actualValue.includes(expectedValue);
    case "NOTCONTAINS":
      return !actualValue.includes(expectedValue);
    case "STARTSWITH":
      return actualValue.startsWith(expectedValue);
    case "ENDSWITH":
      return actualValue.endsWith(expectedValue);
    default:
      return false;
  }
}

function propsMatch(
  localProps: Record<string, string> | undefined,
  filters: PropertyFilter[] | undefined,
): boolean {
  if (filters === undefined || filters.length === 0) return true;
  if (localProps === undefined) return false;
  return filters.every((f) => {
    const actual = localProps[f.key];
    if (actual === undefined) return false;
    return matchPropValue(f.value, f.operator, actual);
  });
}

export function localEventMatchesConfigEvent(
  local: InteractionLocalEvent,
  configEvent: InteractionEvent,
): boolean {
  if (local.name !== configEvent.name) return false;
  return propsMatch(local.props, configEvent.props);
}

export function localMatchesAnyEvent(
  local: InteractionLocalEvent,
  events: readonly InteractionEvent[],
): boolean {
  return events.some((e) => localEventMatchesConfigEvent(local, e));
}
