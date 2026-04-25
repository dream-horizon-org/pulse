import type {
  InteractionEvent,
  InteractionConfig,
} from "../../interactions/interaction-models";
import type { InteractionLocalEvent } from "../../types/interactions/interaction-runtime";
import { localEventMatchesConfigEvent } from "./event-matching";

export function globalBlacklistAsEvents(
  names: readonly string[],
): InteractionEvent[] {
  return names.map((name) => ({
    name,
    required: false,
    isBlacklisted: false,
  }));
}

export function sortedInsertLocalEvent(
  list: InteractionLocalEvent[],
  ev: InteractionLocalEvent,
): void {
  let lo = 0;
  let hi = list.length;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (list[mid]!.timeInNano <= ev.timeInNano) {
      lo = mid + 1;
    } else {
      hi = mid;
    }
  }
  list.splice(lo, 0, ev);
}

export function localEventMatchesFirstConfigEvent(
  local: InteractionLocalEvent,
  config: InteractionConfig,
): boolean {
  const first = config.events.find(
    (event) => event.required && !event.isBlacklisted,
  );
  if (first == null) return false;
  return localEventMatchesConfigEvent(local, first);
}
