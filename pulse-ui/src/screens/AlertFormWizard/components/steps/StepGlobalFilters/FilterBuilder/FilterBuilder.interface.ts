export type FilterOperator = "=" | "!=" | "LIKE";
export type LogicalOperator = "AND" | "OR";

export interface FilterCondition {
  id: string;
  field: string;
  operator: FilterOperator;
  value: string;
}

export interface FilterGroup {
  id: string;
  conditions: FilterCondition[];
  logicalOperator: LogicalOperator;
}

export interface FilterBuilderData {
  groups: FilterGroup[];
  groupOperator: LogicalOperator;
}

export interface FilterBuilderProps {
  value: FilterBuilderData;
  onChange: (value: FilterBuilderData) => void;
  availableFields: { value: string; label: string; options: string[] }[];
  isLoading?: boolean;
}

export const FILTER_OPERATORS: { value: FilterOperator; label: string }[] = [
  { value: "=", label: "equals" },
  { value: "!=", label: "not equals" },
  { value: "LIKE", label: "contains" },
];

export const LOGICAL_OPERATORS: { value: LogicalOperator; label: string }[] = [
  { value: "AND", label: "AND" },
  { value: "OR", label: "OR" },
];

export const createEmptyCondition = (): FilterCondition => ({
  id: `cond_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
  field: "",
  operator: "=",
  value: "",
});

export const createEmptyGroup = (): FilterGroup => ({
  id: `group_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
  conditions: [createEmptyCondition()],
  logicalOperator: "AND",
});

export const createEmptyFilterData = (): FilterBuilderData => ({
  groups: [createEmptyGroup()],
  groupOperator: "AND",
});

