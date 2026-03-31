// Filter Configuration Types
export type FilterCategory = 
  | 'ui_interaction'
  | 'event'
  | 'rum'
  | 'session_property'
  | 'user_property'
  | 'device'
  | 'performance';

export type FilterOperator = 
  | 'equals'
  | 'not_equals'
  | 'contains'
  | 'not_contains'
  | 'greater_than'
  | 'less_than'
  | 'greater_than_or_equal'
  | 'less_than_or_equal'
  | 'in'
  | 'not_in'
  | 'exists'
  | 'not_exists';

export interface FilterCondition {
  id: string;
  category: FilterCategory;
  field: string;
  operator: FilterOperator;
  value: any;
  // For composite filters
  subConditions?: FilterCondition[];
}

export interface FilterGroup {
  id: string;
  operator: 'AND' | 'OR';
  conditions: FilterCondition[];
  subGroups?: FilterGroup[];
}

// Filter Field Definitions
export interface FilterFieldDefinition {
  key: string;
  label: string;
  category: FilterCategory;
  type: 'string' | 'number' | 'boolean' | 'date' | 'enum';
  operators: FilterOperator[];
  enumValues?: Array<{ value: string; label: string }>;
  description?: string;
  unit?: string; // For display (e.g., "ms", "px", "%")
}

// Predefined Filter Fields
export const FILTER_FIELD_DEFINITIONS: FilterFieldDefinition[] = [
  // UI Interaction Filters
  {
    key: 'interaction.type',
    label: 'Interaction Type',
    category: 'ui_interaction',
    type: 'enum',
    operators: ['equals', 'not_equals', 'in', 'not_in'],
    enumValues: [
      { value: 'tap', label: 'Tap' },
      { value: 'swipe', label: 'Swipe' },
      { value: 'scroll', label: 'Scroll' },
      { value: 'long_press', label: 'Long Press' },
      { value: 'pinch', label: 'Pinch' },
      { value: 'input', label: 'Text Input' },
    ],
    description: 'Type of user interaction',
  },
  {
    key: 'interaction.screen',
    label: 'Screen Name',
    category: 'ui_interaction',
    type: 'string',
    operators: ['equals', 'not_equals', 'contains', 'not_contains', 'in', 'not_in'],
    description: 'Screen where interaction occurred',
  },
  {
    key: 'interaction.element',
    label: 'Element ID',
    category: 'ui_interaction',
    type: 'string',
    operators: ['equals', 'not_equals', 'contains', 'not_contains'],
    description: 'UI element identifier',
  },
  {
    key: 'interaction.count',
    label: 'Interaction Count',
    category: 'ui_interaction',
    type: 'number',
    operators: ['equals', 'greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
    description: 'Number of times interaction occurred',
  },
  {
    key: 'interaction.rage_click',
    label: 'Has Rage Clicks',
    category: 'ui_interaction',
    type: 'boolean',
    operators: ['equals'],
    description: 'Session contains rage click behavior',
  },
  
  // Event Filters
  {
    key: 'event.name',
    label: 'Event Name',
    category: 'event',
    type: 'string',
    operators: ['equals', 'not_equals', 'contains', 'not_contains', 'in', 'not_in'],
    description: 'Custom event name',
  },
  {
    key: 'event.count',
    label: 'Event Count',
    category: 'event',
    type: 'number',
    operators: ['equals', 'greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
    description: 'Number of times event occurred',
  },
  {
    key: 'event.property',
    label: 'Event Property',
    category: 'event',
    type: 'string',
    operators: ['equals', 'not_equals', 'contains', 'exists', 'not_exists'],
    description: 'Custom event property (key:value format)',
  },
  
  // RUM Metrics
  {
    key: 'rum.duration',
    label: 'Session Duration',
    category: 'rum',
    type: 'number',
    operators: ['equals', 'greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
    unit: 'seconds',
    description: 'Total session duration',
  },
  {
    key: 'rum.page_load_time',
    label: 'Page Load Time',
    category: 'rum',
    type: 'number',
    operators: ['greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
    unit: 'ms',
    description: 'Average page load time',
  },
  {
    key: 'rum.ttfb',
    label: 'Time to First Byte',
    category: 'rum',
    type: 'number',
    operators: ['greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
    unit: 'ms',
    description: 'Time to first byte',
  },
  {
    key: 'rum.fcp',
    label: 'First Contentful Paint',
    category: 'rum',
    type: 'number',
    operators: ['greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
    unit: 'ms',
    description: 'First contentful paint time',
  },
  {
    key: 'rum.lcp',
    label: 'Largest Contentful Paint',
    category: 'rum',
    type: 'number',
    operators: ['greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
    unit: 'ms',
    description: 'Largest contentful paint time',
  },
  {
    key: 'rum.cls',
    label: 'Cumulative Layout Shift',
    category: 'rum',
    type: 'number',
    operators: ['greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
    description: 'Cumulative layout shift score',
  },
  {
    key: 'rum.memory_usage',
    label: 'Memory Usage',
    category: 'rum',
    type: 'number',
    operators: ['greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
    unit: 'MB',
    description: 'Peak memory usage',
  },
  
  // Session Properties
  {
    key: 'session.error_count',
    label: 'Error Count',
    category: 'session_property',
    type: 'number',
    operators: ['equals', 'greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
    description: 'Number of errors in session',
  },
  {
    key: 'session.page_count',
    label: 'Page Count',
    category: 'session_property',
    type: 'number',
    operators: ['equals', 'greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
    description: 'Number of pages viewed',
  },
  {
    key: 'session.journey',
    label: 'Journey Path',
    category: 'session_property',
    type: 'string',
    operators: ['contains', 'not_contains'],
    description: 'User journey path',
  },
  {
    key: 'session.environment',
    label: 'Environment',
    category: 'session_property',
    type: 'enum',
    operators: ['equals', 'not_equals', 'in', 'not_in'],
    enumValues: [
      { value: 'production', label: 'Production' },
      { value: 'staging', label: 'Staging' },
      { value: 'development', label: 'Development' },
    ],
    description: 'Deployment environment',
  },
  
  // User Properties
  {
    key: 'user.id',
    label: 'User ID',
    category: 'user_property',
    type: 'string',
    operators: ['equals', 'not_equals', 'contains', 'exists', 'not_exists'],
    description: 'User identifier',
  },
  {
    key: 'user.is_anonymous',
    label: 'Is Anonymous',
    category: 'user_property',
    type: 'boolean',
    operators: ['equals'],
    description: 'Anonymous user session',
  },
  {
    key: 'user.is_new',
    label: 'Is New User',
    category: 'user_property',
    type: 'boolean',
    operators: ['equals'],
    description: 'First-time user',
  },
  {
    key: 'user.country',
    label: 'Country',
    category: 'user_property',
    type: 'string',
    operators: ['equals', 'not_equals', 'in', 'not_in'],
    description: 'User country code',
  },
  
  // Device Properties
  {
    key: 'device.type',
    label: 'Device Type',
    category: 'device',
    type: 'enum',
    operators: ['equals', 'not_equals', 'in', 'not_in'],
    enumValues: [
      { value: 'android', label: 'Android' },
      { value: 'ios', label: 'iOS' },
      { value: 'web', label: 'Web' },
    ],
    description: 'Device platform',
  },
  {
    key: 'device.os_version',
    label: 'OS Version',
    category: 'device',
    type: 'string',
    operators: ['equals', 'contains', 'greater_than', 'less_than'],
    description: 'Operating system version',
  },
  {
    key: 'device.browser',
    label: 'Browser',
    category: 'device',
    type: 'string',
    operators: ['equals', 'not_equals', 'contains', 'in', 'not_in'],
    description: 'Browser name and version',
  },
  {
    key: 'device.build_version',
    label: 'Build Version',
    category: 'device',
    type: 'string',
    operators: ['equals', 'not_equals', 'contains'],
    description: 'App build version',
  },
  
  // Performance
  {
    key: 'performance.slow_session',
    label: 'Is Slow Session',
    category: 'performance',
    type: 'boolean',
    operators: ['equals'],
    description: 'Session marked as slow',
  },
  {
    key: 'performance.network_failures',
    label: 'Network Failure Count',
    category: 'performance',
    type: 'number',
    operators: ['equals', 'greater_than', 'less_than', 'greater_than_or_equal', 'less_than_or_equal'],
    description: 'Number of network failures',
  },
];

// Helper function to get fields by category
export const getFieldsByCategory = (category: FilterCategory): FilterFieldDefinition[] => {
  return FILTER_FIELD_DEFINITIONS.filter(field => field.category === category);
};

// Helper function to get field definition
export const getFieldDefinition = (key: string): FilterFieldDefinition | undefined => {
  return FILTER_FIELD_DEFINITIONS.find(field => field.key === key);
};

// Operator labels
export const OPERATOR_LABELS: Record<FilterOperator, string> = {
  equals: 'equals',
  not_equals: 'does not equal',
  contains: 'contains',
  not_contains: 'does not contain',
  greater_than: 'greater than',
  less_than: 'less than',
  greater_than_or_equal: 'greater than or equal to',
  less_than_or_equal: 'less than or equal to',
  in: 'is one of',
  not_in: 'is not one of',
  exists: 'exists',
  not_exists: 'does not exist',
};

// Category labels
export const CATEGORY_LABELS: Record<FilterCategory, string> = {
  ui_interaction: 'UI Interaction',
  event: 'Custom Event',
  rum: 'RUM Metrics',
  session_property: 'Session Property',
  user_property: 'User Property',
  device: 'Device',
  performance: 'Performance',
};
