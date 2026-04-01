export type CreateWizardStepMeta = {
  label: string;
  description: string;
};

/** Vertical stepper labels + descriptions (aligned with Critical Interaction form pattern). */
export const FUNNEL_CREATE_STEPS: CreateWizardStepMeta[] = [
  {
    label: "Basics",
    description:
      "Give your funnel a clear name, optional description, and tags so your team can find it later.",
  },
  {
    label: "Time window",
    description:
      "Choose recurring rolling windows or a one-off range, and when the funnel should stop updating.",
  },
  {
    label: "Steps & conversion",
    description:
      "Pick sequential or any-order steps, map telemetry events to each step, and set the conversion window.",
  },
  {
    label: "Filters & create",
    description:
      "Optionally segment by OS, app version, or other fields, then save your funnel.",
  },
];

export const FUNNEL_CREATE_STEP_ERRORS = {
  NAME: "Enter a funnel name before continuing.",
  SCHEDULE_ONCE: "For a one-off analysis, choose both a start and end date.",
  STEPS: "Add at least two steps and select an event for each step.",
} as const;

export const JOURNEY_CREATE_STEP_ERRORS = {
  NAME: "Enter a journey name before continuing.",
  SCHEDULE_ONCE: "For a one-off analysis, choose both a start and end date.",
  PATH: "Select an anchor event before continuing.",
} as const;

export const JOURNEY_CREATE_STEPS: CreateWizardStepMeta[] = [
  {
    label: "Basics",
    description:
      "Name the journey and add tags so it is easy to discover in lists and dashboards.",
  },
  {
    label: "Time window",
    description:
      "Define how often the journey recomputes and optional expiry for recurring analyses.",
  },
  {
    label: "Path",
    description:
      "Choose an anchor event, whether to explore forward or backward, and graph depth.",
  },
  {
    label: "Filters & create",
    description: "Optionally segment your audience, then create the journey.",
  },
];
