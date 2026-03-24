import type { ReactNode } from "react";

export type PersonaType = "all" | "support" | "product" | "tech";

export interface PersonaConfig {
  label: string;
  color: string;
  icon: string;
  defaultTab: string;
  visibleTabs: string[];
  highlightTypes: string[];
}

export interface PersonaContextType {
  activePersona: PersonaType;
  setActivePersona: (persona: PersonaType) => void;
  getPersonaConfig: (persona: PersonaType) => PersonaConfig;
}

export interface PersonaProviderProps {
  children: ReactNode;
  initialPersona?: PersonaType;
}
