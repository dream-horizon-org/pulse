export const ADVANCED_FILTER_LABELS = {
  modalTitle: "Advanced Filters",
  modalTooltip: "Build complex queries with multiple conditions",
  matchLabel: "Match",
  ofTheFollowing: "of the following:",
  addCondition: "Add Condition",
  clearAll: "Clear All",
  cancel: "Cancel",
  apply: "Apply",
  loadingMessage: "Loading filter options...",
  errorMessage: "Failed to load filter schema. Please try again.",
  emptyTitle: "No conditions added yet",
  emptyDescription: 'Click "Add Condition" above to start building your filter',
} as const;

export const OPERATOR_OPTIONS = [
  { value: "AND", label: "ALL" },
  { value: "OR", label: "ANY" },
] as const;

export const MODAL_STYLES = {
  bodyMaxHeight: "calc(100vh - 200px)",
  emptyStateBg: "#f8f9fa",
  errorStateBg: "#fff3cd",
} as const;

export const DEFAULT_FIRST_CATEGORY = "ui_interaction";
export const DEFAULT_FIRST_FIELD = "interaction.type";
export const DEFAULT_OPERATOR = "equals";
