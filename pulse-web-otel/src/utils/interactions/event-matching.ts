import type {
  InteractionEvent,
  PropertyFilter,
} from "../../interactions/interaction-models";
import type { InteractionLocalEvent } from "../../types/interactions/interaction-runtime";

function normalizeOperator(op: string): string {
  return op.toUpperCase();
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
      return actualValue
        .toLowerCase()
        .includes(expectedValue.toLowerCase());
    case "NOTCONTAINS":
      return !actualValue
        .toLowerCase()
        .includes(expectedValue.toLowerCase());
    case "STARTSWITH":
      return actualValue
        .toLowerCase()
        .startsWith(expectedValue.toLowerCase());
    case "ENDSWITH":
      return actualValue
        .toLowerCase()
        .endsWith(expectedValue.toLowerCase());
    default:
      return false;
  }
}

function propsMatch(
  localProps: Record<string, string> | undefined,
  filters: PropertyFilter[] | null | undefined,
): boolean {
  if (filters == null || filters.length === 0) return true;
  if (localProps === undefined) return false;
  return filters.every((f) => {
    const actual = localProps[f.name];
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
