/**
 * PersonaContext
 * 
 * PRODUCT PURPOSE:
 * Manage which persona view is active (Support, Product, Tech, or All).
 * Each persona sees different tabs, highlights, and default views.
 * 
 * DESIGN PHILOSOPHY:
 * - Support: Focus on actionability (create ticket, send workaround)
 * - Product: Focus on business impact (revenue, conversion, patterns)
 * - Tech: Focus on root cause (code, errors, reproduce)
 * - All: Show everything (default, power user mode)
 */

import { createContext, useContext, useState, ReactNode, useEffect } from 'react';

export type PersonaType = 'all' | 'support' | 'product' | 'tech';

interface PersonaConfig {
  label: string;
  color: string;         // Mantine color for theming
  icon: string;          // Tabler icon name
  defaultTab: string;    // Which tab to show first
  visibleTabs: string[]; // Which tabs to show
  highlightTypes: string[]; // What to emphasize in UI
}

interface PersonaContextType {
  activePersona: PersonaType;
  setActivePersona: (persona: PersonaType) => void;
  getPersonaConfig: (persona: PersonaType) => PersonaConfig;
}

/**
 * Persona Configurations
 * 
 * Defines behavior for each persona view
 */
const PERSONA_CONFIGS: Record<PersonaType, PersonaConfig> = {
  // ALL: Power user view, see everything
  all: {
    label: 'All Data',
    color: 'gray',
    icon: 'IconUsers',
    defaultTab: 'session-info',
    visibleTabs: ['session-info', 'events', 'console', 'network', 'performance', 'technical'],
    highlightTypes: [] // Show everything equally
  },
  
  // SUPPORT: Customer-facing view
  support: {
    label: 'Support View',
    color: 'blue',
    icon: 'IconHeadset',
    defaultTab: 'support-summary',
    visibleTabs: ['support-summary', 'console', 'events', 'session-info'],
    highlightTypes: ['error', 'user_impact', 'workaround'] // Highlight what matters to support
  },
  
  // PRODUCT: Business impact view
  product: {
    label: 'Product View',
    color: 'violet',
    icon: 'IconChartLine',
    defaultTab: 'business-impact',
    visibleTabs: ['business-impact', 'events', 'session-info', 'experiments'],
    highlightTypes: ['conversion', 'abandonment', 'feature_usage', 'revenue'] // Highlight business metrics
  },
  
  // TECH: Engineering/debugging view
  tech: {
    label: 'Tech View',
    color: 'orange',
    icon: 'IconCode',
    defaultTab: 'technical',
    visibleTabs: ['technical', 'console', 'network', 'performance', 'session-info'],
    highlightTypes: ['error', 'performance', 'root_cause', 'code_ref'] // Highlight technical details
  }
};

/**
 * Context
 */
const PersonaContext = createContext<PersonaContextType | undefined>(undefined);

/**
 * Provider Component
 * 
 * Manages persona state and provides config access
 */
interface PersonaProviderProps {
  children: ReactNode;
  initialPersona?: PersonaType;
}

export const PersonaProvider = ({ children, initialPersona = 'all' }: PersonaProviderProps) => {
  const [activePersona, setActivePersona] = useState<PersonaType>(initialPersona);
  
  // Optionally persist to localStorage
  useEffect(() => {
    const saved = localStorage.getItem('pulse_session_replay_persona');
    if (saved && (saved === 'all' || saved === 'support' || saved === 'product' || saved === 'tech')) {
      setActivePersona(saved as PersonaType);
    }
  }, []);
  
  useEffect(() => {
    localStorage.setItem('pulse_session_replay_persona', activePersona);
  }, [activePersona]);
  
  const getPersonaConfig = (persona: PersonaType) => PERSONA_CONFIGS[persona];
  
  const value: PersonaContextType = {
    activePersona,
    setActivePersona,
    getPersonaConfig
  };
  
  return (
    <PersonaContext.Provider value={value}>
      {children}
    </PersonaContext.Provider>
  );
};

/**
 * Hook to use persona context
 * 
 * Usage:
 * ```tsx
 * const { activePersona, setActivePersona, getPersonaConfig } = usePersona();
 * 
 * // Switch persona
 * setActivePersona('support');
 * 
 * // Get config
 * const config = getPersonaConfig('product');
 * console.log(config.defaultTab); // 'business-impact'
 * ```
 */
export const usePersona = (): PersonaContextType => {
  const context = useContext(PersonaContext);
  if (!context) {
    throw new Error('usePersona must be used within PersonaProvider');
  }
  return context;
};

/**
 * Helper: Check if tab should be visible for current persona
 */
export const useIsTabVisible = (tabId: string): boolean => {
  const { activePersona, getPersonaConfig } = usePersona();
  const config = getPersonaConfig(activePersona);
  return config.visibleTabs.includes(tabId);
};

/**
 * Helper: Check if highlight type should be shown for current persona
 */
export const useShouldHighlight = (type: string): boolean => {
  const { activePersona, getPersonaConfig } = usePersona();
  
  // 'all' shows everything
  if (activePersona === 'all') return true;
  
  const config = getPersonaConfig(activePersona);
  return config.highlightTypes.includes(type);
};
