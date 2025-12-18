/**
 * Scope Type Constants
 */

import { AlertScopeType } from "../types";

export interface ScopeTypeConfig {
  id: AlertScopeType;
  label: string;
  description: string;
  icon: string;
  color: string;
  features: string[];
}

export const SCOPE_TYPE_CONFIGS: ScopeTypeConfig[] = [
  { id: AlertScopeType.Interaction, label: "Interaction", description: "Monitor user interactions", icon: "IconClick", color: "#0ec9c2", features: ["Track journeys", "Monitor conversions", "Performance alerts"] },
  { id: AlertScopeType.Screen, label: "Screen", description: "Monitor screen performance", icon: "IconDeviceMobile", color: "#4c6ef5", features: ["Load duration", "Rendering metrics", "UX monitoring"] },
  { id: AlertScopeType.AppVitals, label: "App Vitals", description: "Monitor app health", icon: "IconHeartRateMonitor", color: "#f03e3e", features: ["Crash rate", "ANR detection", "Stability alerts"] },
  { id: AlertScopeType.NetworkAPI, label: "Network API", description: "Monitor API performance", icon: "IconApi", color: "#7950f2", features: ["Response time", "Error tracking", "Throughput alerts"] },
];

