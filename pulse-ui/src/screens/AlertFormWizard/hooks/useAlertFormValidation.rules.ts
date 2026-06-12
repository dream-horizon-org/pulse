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
  
  // Check for invalid operators (AND, OR should not be used - only && and || are allowed)
  // Use word boundary to avoid matching parts of aliases
  if (/\bAND\b/i.test(expression)) {
    return { isValid: false, error: "Use '&&' instead of 'AND' for logical AND" };
  }
  if (/\bOR\b/i.test(expression)) {
    return { isValid: false, error: "Use '||' instead of 'OR' for logical OR" };
  }
  
  // Remove whitespace for structure validation
  const cleanedExpression = expression.replace(/\s+/g, "");
  
  // Validate that expression only contains valid characters: aliases (A-Z), operators (&&, ||), parentheses
  const validPattern = /^[A-Z()&|]+$/;
  if (!validPattern.test(cleanedExpression)) {
    return { isValid: false, error: "Expression contains invalid characters. Use only aliases (A-Z), &&, || and parentheses" };
  }
  
  // Check for balanced parentheses
  let parenCount = 0;
  for (const char of cleanedExpression) {
    if (char === "(") parenCount++;
    if (char === ")") parenCount--;
    if (parenCount < 0) {
      return { isValid: false, error: "Unbalanced parentheses: unexpected ')'" };
    }
  }
  if (parenCount !== 0) {
    return { isValid: false, error: "Unbalanced parentheses: missing ')'" };
  }
  
  // Replace valid operators with a placeholder to simplify structure validation
  // && -> @, || -> #
  let structureExpr = cleanedExpression.replace(/&&/g, "@").replace(/\|\|/g, "#");
  
  // Check for single & or | (invalid operators)
  if (structureExpr.includes("&")) {
    return { isValid: false, error: "Use '&&' for AND operator, not single '&'" };
  }
  if (structureExpr.includes("|")) {
    return { isValid: false, error: "Use '||' for OR operator, not single '|'" };
  }
  
  // Check expression structure: should not start or end with an operator
  if (/^[@#]/.test(structureExpr)) {
    return { isValid: false, error: "Expression cannot start with an operator" };
  }
  if (/[@#]$/.test(structureExpr)) {
    return { isValid: false, error: "Expression cannot end with an operator" };
  }
  
  // Check for consecutive operators (e.g., && ||, || &&)
  if (/[@#]{2,}/.test(structureExpr)) {
    return { isValid: false, error: "Consecutive operators are not allowed" };
  }
  
  // Check for operator immediately after opening paren or before closing paren
  if (/\([@#]/.test(structureExpr)) {
    return { isValid: false, error: "Operator cannot appear immediately after '('" };
  }
  if (/[@#]\)/.test(structureExpr)) {
    return { isValid: false, error: "Operator cannot appear immediately before ')'" };
  }
  
  // Check for empty parentheses
  if (/\(\)/.test(structureExpr)) {
    return { isValid: false, error: "Empty parentheses are not allowed" };
  }
  
  // Check for missing operator between alias and parenthesis or between aliases
  // e.g., "A(B)" or "(A)B" or "AB" should be invalid
  if (/[A-Z]\(/.test(structureExpr)) {
    return { isValid: false, error: "Missing operator before '('" };
  }
  if (/\)[A-Z]/.test(structureExpr)) {
    return { isValid: false, error: "Missing operator after ')'" };
  }
  if (/[A-Z]{2,}/.test(structureExpr)) {
    return { isValid: false, error: "Missing operator between aliases" };
  }
  
  // Check for adjacent parentheses without operator
  if (/\)\(/.test(structureExpr)) {
    return { isValid: false, error: "Missing operator between ')' and '('" };
  }
  
  // Validate aliases
  const matchResult = expression.match(/[A-Z]/g);
  const exprAliases = matchResult ? Array.from(new Set(matchResult)) : [];
  const invalid = exprAliases.filter((a) => !validAliases.includes(a));
  if (invalid.length) return { isValid: false, error: `Invalid aliases: ${invalid.join(", ")}` };
  const unused = validAliases.filter((a) => exprAliases.indexOf(a) === -1);
  if (unused.length) return { isValid: false, error: `Unused aliases: ${unused.join(", ")}` };
  
  return { isValid: true };
}

export function validateThresholdValue(value: number, scopeName: string): FieldValidationResult {
  if (value < 0) {
    return { isValid: false, error: `Threshold for "${scopeName}" cannot be negative` };
  }
  return { isValid: true };
}

