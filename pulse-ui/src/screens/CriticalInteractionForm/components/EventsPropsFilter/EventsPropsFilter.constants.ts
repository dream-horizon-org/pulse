import { FilterOperator } from "../../CriticalInteractionForm.interface";

export const OPERATOR_OPTIONS: { value: FilterOperator; label: string }[] = [
  { value: "EQUALS", label: "Equals" },
  { value: "NOTEQUALS", label: "Not Equals" },
  { value: "CONTAINS", label: "Contains" },
  { value: "NOTCONTAINS", label: "Not Contains" },
  { value: "STARTSWITH", label: "Starts With" },
  { value: "ENDSWITH", label: "Ends With" },
];
