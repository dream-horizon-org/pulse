import { SessionTimelineEvent } from "../../../SessionTimeline.interface";

export const extractResourceAttributes = (
  events: SessionTimelineEvent[],
): Record<string, any> => {
  const resourceAttrs: Record<string, any> = {};

  for (const event of events) {
    if (event.attributes?.resource) {
      Object.assign(resourceAttrs, event.attributes.resource);
    }
  }

  return resourceAttrs;
};
