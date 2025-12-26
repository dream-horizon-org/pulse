/**
 * Type Guards
 */

import { AlertScopeType } from "./enums";

export function stepRequiresScopeItems(scopeType: AlertScopeType | null): boolean {
  return scopeType !== null && scopeType !== AlertScopeType.AppVitals;
}

export function isAppVitalsScope(scopeType: AlertScopeType | null): boolean {
  return scopeType === AlertScopeType.AppVitals;
}

