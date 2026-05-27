import type {
  FilterCondition,
  FilterGroup,
  FilterOperator,
} from "../../../../services/sessionReplay/filterConfig";

/**
 * Operators that don't require a value input from the user
 */
const NO_VALUE_OPERATORS: FilterOperator[] = ["exists", "not_exists"];

/**
 * Backend operators that don't require a value (for when operators come from API)
 */
const NO_VALUE_OPERATOR_KEYS = ["EMPTY", "NOT_EMPTY", "EXISTS", "NOT_EXISTS"];

/**
 * Operators that require array/multiple values
 */
const ARRAY_VALUE_OPERATORS: FilterOperator[] = ["in", "not_in"];

/**
 * Check if an operator requires a value
 */
export function operatorRequiresValue(operator: FilterOperator): boolean {
  return !NO_VALUE_OPERATORS.includes(operator);
}

/**
 * Check if a value is empty
 */
function isValueEmpty(value: any): boolean {
  if (value === null || value === undefined) {
    return true;
  }
  if (typeof value === "string" && value.trim() === "") {
    return true;
  }
  if (Array.isArray(value) && value.length === 0) {
    return true;
  }
  return false;
}

/**
 * Validate a single filter condition
 * Returns error message if invalid, empty string if valid
 */
export function validateCondition(condition: FilterCondition): string {
  const { operator, value } = condition;

  // Check if operator requires a value
  // Handle both frontend operators (lowercase) and backend operators (UPPERCASE)
  const operatorKey = String(operator).toUpperCase();
  const isNoValueOperator =
    NO_VALUE_OPERATORS.includes(operator as FilterOperator) ||
    NO_VALUE_OPERATOR_KEYS.includes(operatorKey);

  if (!isNoValueOperator) {
    if (isValueEmpty(value)) {
      return "Value is required";
    }
  }

  // For array operators, ensure it's actually an array
  if (ARRAY_VALUE_OPERATORS.includes(operator as FilterOperator)) {
    if (!Array.isArray(value)) {
      return "Value is required";
    }
  }

  return ""; // Valid
}

/**
 * Check if all conditions in a filter group are valid
 * Returns an object with validation status and error details
 */
export function validateFilterGroup(filterGroup: FilterGroup | null): {
  isValid: boolean;
  invalidConditionIds: string[];
  errorsByConditionId: Record<string, string>;
} {
  if (!filterGroup || filterGroup.conditions.length === 0) {
    return {
      isValid: false,
      invalidConditionIds: [],
      errorsByConditionId: {},
    };
  }

  const invalidConditionIds: string[] = [];
  const errorsByConditionId: Record<string, string> = {};

  for (const condition of filterGroup.conditions) {
    const error = validateCondition(condition);
    if (error) {
      invalidConditionIds.push(condition.id);
      errorsByConditionId[condition.id] = error;
    }
  }

  return {
    isValid: invalidConditionIds.length === 0,
    invalidConditionIds,
    errorsByConditionId,
  };
}
