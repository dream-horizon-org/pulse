/**
 * Validation Rules
 */

import { AlertFormWizardData } from "../types";
import { ValidationRule, FieldValidationResult } from "./useAlertFormValidation.interface";

export const NAME_RULES: ValidationRule<string>[] = [
  { validate: (v) => v.trim().length >= 4, message: "Name must be at least 4 characters" },
  { validate: (v) => v.trim().length <= 100, message: "Name must not exceed 100 characters" },
  { validate: (v) => /^[a-zA-Z0-9\s_-]+$/.test(v.trim()), message: "Invalid characters in name" },
];

export const DESCRIPTION_RULES: ValidationRule<string>[] = [
  { validate: (v) => v.trim().length >= 10, message: "Description must be at least 10 characters" },
  { validate: (v) => v.trim().length <= 500, message: "Description must not exceed 500 characters" },
];

export const EVAL_PERIOD_RULES: ValidationRule<number>[] = [
  { validate: (v) => v >= 30, message: "Period must be at least 30 seconds" },
  { validate: (v) => v <= 3600, message: "Period must not exceed 3600 seconds" },
];

export const EVAL_INTERVAL_RULES: ValidationRule<number>[] = [
  { validate: (v) => v >= 30, message: "Interval must be at least 30 seconds" },
  { validate: (v) => v <= 3600, message: "Interval must not exceed 3600 seconds" },
  { validate: (v, fd) => v <= fd.evaluationConfig.evaluationPeriod, message: "Interval cannot exceed period" },
];

export function runRules<T>(
  value: T,
  rules: ValidationRule<T>[],
  formData: AlertFormWizardData
): FieldValidationResult {
  for (const rule of rules) {
    if (!rule.validate(value, formData)) {
      return { isValid: false, error: rule.message };
    }
  }
  return { isValid: true };
}

export function validateExpressionSyntax(expression: string, validAliases: string[]): FieldValidationResult {
  if (!expression.trim()) return { isValid: false, error: "Expression is required" };
  const matchResult = expression.match(/[A-Z]/g);
  const exprAliases = matchResult ? Array.from(matchResult) : [];
  const invalid = exprAliases.filter((a) => !validAliases.includes(a));
  if (invalid.length) return { isValid: false, error: `Invalid aliases: ${invalid.join(", ")}` };
  const unused = validAliases.filter((a) => exprAliases.indexOf(a) === -1);
  if (unused.length) return { isValid: false, error: `Unused aliases: ${unused.join(", ")}` };
  return { isValid: true };
}

