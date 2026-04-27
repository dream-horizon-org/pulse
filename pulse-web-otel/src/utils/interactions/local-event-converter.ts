import type { InteractionLocalEvent } from "../../types/interactions/interaction-runtime";
import type { PulseAttributes } from "../../types/attributes";

export function toInteractionLocalEvent(
  name: string,
  attrs: PulseAttributes | undefined,
  timeMs: number,
): InteractionLocalEvent {
  const props =
    attrs == null
      ? undefined
      : Object.fromEntries(
          Object.entries(attrs).map(([k, v]) => [
            k,
            v == null ? "" : String(v),
          ]),
        );
  return {
    name,
    timeInNano: Math.round(timeMs * 1_000_000),
    props,
  };
}
