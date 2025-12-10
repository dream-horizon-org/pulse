/**
 * Google Analytics 4 Integration
 * 
 * Provides comprehensive tracking for user interactions and behavior analysis.
 * 
 * Setup:
 * 1. Set REACT_APP_GA_MEASUREMENT_ID in your environment
 * 2. GA is initialized in App.tsx on mount
 * 3. Page views are automatically tracked via PageTracker component
 * 4. Use logEvent() for custom event tracking
 */

import ReactGA from "react-ga4";
import {
  AnalyticsScreen,
  AnalyticsLabels,
  AnalyticsFilterType,
  AnalyticsLabelFormatter,
  AnalyticsParams,
  AnalyticsTimingCategory,
  AnalyticsErrorType,
  AnalyticsTab, // eslint-disable-line @typescript-eslint/no-unused-vars -- Re-exported for external use
} from "./analyticsConstants";

// Re-export constants for convenience
export {
  AnalyticsScreen,
  AnalyticsLabels,
  AnalyticsFilterType,
  AnalyticsLabelFormatter,
  AnalyticsParams,
  AnalyticsTimingCategory,
  AnalyticsErrorType,
  AnalyticsTab,
} from "./analyticsConstants";

// Check if GA is enabled
const isGAEnabled = () => !!process.env.REACT_APP_GA_MEASUREMENT_ID;

/**
 * Initialize Google Analytics
 */
export const initGA = () => {
  const GA_MEASUREMENT_ID = process.env.REACT_APP_GA_MEASUREMENT_ID ?? "";
  if (GA_MEASUREMENT_ID) {
    ReactGA.initialize(GA_MEASUREMENT_ID, {
      gaOptions: {
        anonymizeIp: true, // GDPR compliance
      },
    });
    console.log("[Analytics] Initialized with ID:", GA_MEASUREMENT_ID);
  }
};

/**
 * Log page view
 */
export const logPageView = (path: string, title?: string) => {
  if (!isGAEnabled()) return;
  
  ReactGA.send({ 
    hitType: "pageview", 
    page: path,
    title: title || document.title,
  });
};

/**
 * Event Categories for organized tracking
 */
export const EventCategory = {
  NAVIGATION: "Navigation",
  INTERACTION: "Interaction", 
  CONFIGURATION: "Configuration",
  FILTER: "Filter",
  SEARCH: "Search",
  ERROR: "Error",
  FEATURE: "Feature",
  USER: "User",
  PERFORMANCE: "Performance",
} as const;

/**
 * Predefined event actions for consistency
 */
export const EventAction = {
  // Navigation events
  PAGE_VIEW: "page_view",
  TAB_SWITCH: "tab_switch",
  MENU_CLICK: "menu_click",
  BREADCRUMB_CLICK: "breadcrumb_click",
  
  // Interaction events
  CLICK: "click",
  EXPAND: "expand",
  COLLAPSE: "collapse",
  HOVER: "hover",
  SCROLL: "scroll",
  
  // CRUD events
  CREATE: "create",
  READ: "read",
  UPDATE: "update",
  DELETE: "delete",
  SAVE: "save",
  CANCEL: "cancel",
  
  // Filter/Search events
  FILTER_APPLY: "filter_apply",
  FILTER_CLEAR: "filter_clear",
  SEARCH: "search",
  SORT: "sort",
  
  // Feature events
  FEATURE_ENABLE: "feature_enable",
  FEATURE_DISABLE: "feature_disable",
  
  // Config events
  CONFIG_VIEW: "config_view",
  CONFIG_CREATE: "config_create",
  CONFIG_UPDATE: "config_update",
  CONFIG_DUPLICATE: "config_duplicate",
  CONFIG_VERSION_SELECT: "config_version_select",
  
  // Error events
  ERROR_OCCURRED: "error_occurred",
  ERROR_DISMISSED: "error_dismissed",
  
  // User events
  LOGIN: "login",
  LOGOUT: "logout",
  SETTINGS_CHANGE: "settings_change",
} as const;

type EventCategoryType = typeof EventCategory[keyof typeof EventCategory];
type EventActionType = typeof EventAction[keyof typeof EventAction];

/**
 * Log custom event with optional parameters
 */
export const logEvent = (
  action: EventActionType | string,
  label?: string,
  category: EventCategoryType | string = EventCategory.USER,
  value?: number,
  additionalParams?: Record<string, string | number | boolean>,
) => {
  if (!isGAEnabled()) return;

  ReactGA.event({
    category,
    action,
    label,
    value,
    ...additionalParams,
  });

  // Debug logging in development
  if (process.env.NODE_ENV === "development") {
    console.log("[Analytics Event]", { category, action, label, value, ...additionalParams });
  }
};

/**
 * Track feature usage
 */
export const trackFeatureUsage = (featureName: string, action: string, details?: string) => {
  logEvent(
    action,
    details || featureName,
    EventCategory.FEATURE,
    undefined,
    { [AnalyticsParams.FEATURE_NAME]: featureName }
  );
};

/**
 * Track navigation
 */
export const trackNavigation = (from: string, to: string, method: string = "click") => {
  logEvent(
    EventAction.PAGE_VIEW,
    `${from} -> ${to}`,
    EventCategory.NAVIGATION,
    undefined,
    { [AnalyticsParams.NAVIGATION_METHOD]: method }
  );
};

/**
 * Track filter/search actions
 */
export const trackFilter = (
  filterType: string, 
  filterValue: string, 
  screenName: string
) => {
  logEvent(
    EventAction.FILTER_APPLY,
    `${filterType}: ${filterValue}`,
    EventCategory.FILTER,
    undefined,
    { [AnalyticsParams.SCREEN_NAME]: screenName, [AnalyticsParams.FILTER_TYPE]: filterType }
  );
};

/**
 * Track search queries
 */
export const trackSearch = (query: string, resultsCount: number, screenName: string) => {
  logEvent(
    EventAction.SEARCH,
    query.substring(0, 100), // Limit query length
    EventCategory.SEARCH,
    resultsCount,
    { [AnalyticsParams.SCREEN_NAME]: screenName }
  );
};

/**
 * Track errors
 */
export const trackError = (
  errorType: string, 
  errorMessage: string, 
  screenName?: string
) => {
  logEvent(
    EventAction.ERROR_OCCURRED,
    `${errorType}: ${errorMessage.substring(0, 100)}`,
    EventCategory.ERROR,
    undefined,
    { [AnalyticsParams.SCREEN_NAME]: screenName || AnalyticsLabels.UNKNOWN }
  );
};

/**
 * Track timing/performance
 */
export const trackTiming = (
  category: string,
  variable: string,
  timeMs: number,
  label?: string
) => {
  if (!isGAEnabled()) return;

  ReactGA.send({
    hitType: "timing",
    timingCategory: category,
    timingVar: variable,
    timingValue: timeMs,
    timingLabel: label,
  });
};

/**
 * Set user properties for better segmentation
 */
export const setUserProperties = (properties: Record<string, string | number | boolean>) => {
  if (!isGAEnabled()) return;
  
  ReactGA.gtag("set", "user_properties", properties);
};

/**
 * Track screen/section time spent
 */
export const createTimeTracker = (screenName: string) => {
  const startTime = Date.now();
  
  return {
    end: () => {
      const duration = Date.now() - startTime;
      trackTiming(AnalyticsTimingCategory.SCREEN_TIME, screenName, duration);
      return duration;
    }
  };
};

// ==========================================
// Specific Event Tracking Functions
// ==========================================

/**
 * Track Interaction List events
 */
export const trackInteractionList = {
  view: () => logEvent(EventAction.PAGE_VIEW, AnalyticsLabels.INTERACTION_LIST, EventCategory.NAVIGATION),
  itemClick: (interactionName: string) => 
    logEvent(EventAction.CLICK, interactionName, EventCategory.INTERACTION),
  filterApply: (filters: Record<string, string>) => 
    logEvent(EventAction.FILTER_APPLY, JSON.stringify(filters), EventCategory.FILTER),
  search: (query: string, count: number) => 
    trackSearch(query, count, AnalyticsScreen.INTERACTION_LIST),
};

/**
 * Track App Vitals events
 */
export const trackAppVitals = {
  view: () => logEvent(EventAction.PAGE_VIEW, AnalyticsLabels.APP_VITALS, EventCategory.NAVIGATION),
  tabSwitch: (tab: string) => logEvent(EventAction.TAB_SWITCH, tab, EventCategory.NAVIGATION),
  crashClick: (crashId: string) => 
    logEvent(EventAction.CLICK, AnalyticsLabelFormatter.crash(crashId), EventCategory.INTERACTION),
  anrClick: (anrId: string) => 
    logEvent(EventAction.CLICK, AnalyticsLabelFormatter.anr(anrId), EventCategory.INTERACTION),
  filterChange: (filterType: string, value: string) => 
    trackFilter(filterType, value, AnalyticsScreen.APP_VITALS),
};

/**
 * Track SDK Configuration events
 */
export const trackSdkConfig = {
  listView: () => logEvent(EventAction.PAGE_VIEW, AnalyticsLabels.SDK_CONFIG_LIST, EventCategory.CONFIGURATION),
  versionView: (version: number) => 
    logEvent(EventAction.CONFIG_VIEW, AnalyticsLabelFormatter.version(version), EventCategory.CONFIGURATION),
  versionCreate: (baseVersion?: number) => 
    logEvent(EventAction.CONFIG_CREATE, baseVersion ? AnalyticsLabelFormatter.basedOnVersion(baseVersion) : AnalyticsLabels.NEW, EventCategory.CONFIGURATION),
  versionDuplicate: (version: number) => 
    logEvent(EventAction.CONFIG_DUPLICATE, AnalyticsLabelFormatter.version(version), EventCategory.CONFIGURATION),
  versionSave: (version: number) => 
    logEvent(EventAction.SAVE, AnalyticsLabelFormatter.version(version), EventCategory.CONFIGURATION),
  filterModeChange: (mode: string) => 
    logEvent(EventAction.UPDATE, AnalyticsLabelFormatter.filterMode(mode), EventCategory.CONFIGURATION),
  samplingRateChange: (rate: number) => 
    logEvent(EventAction.UPDATE, AnalyticsLabelFormatter.samplingRate(rate), EventCategory.CONFIGURATION),
  featureToggle: (feature: string, enabled: boolean) => 
    logEvent(enabled ? EventAction.FEATURE_ENABLE : EventAction.FEATURE_DISABLE, feature, EventCategory.FEATURE),
  jsonView: () => logEvent(EventAction.CLICK, AnalyticsLabels.VIEW_JSON, EventCategory.CONFIGURATION),
};

/**
 * Track Network monitoring events
 */
export const trackNetwork = {
  view: () => logEvent(EventAction.PAGE_VIEW, AnalyticsLabels.NETWORK_APIS, EventCategory.NAVIGATION),
  apiClick: (endpoint: string) => 
    logEvent(EventAction.CLICK, endpoint, EventCategory.INTERACTION),
  filterApply: (filterType: string, value: string) => 
    trackFilter(filterType, value, AnalyticsScreen.NETWORK_LIST),
};

/**
 * Track Screen List events
 */
export const trackScreenList = {
  view: () => logEvent(EventAction.PAGE_VIEW, AnalyticsLabels.SCREENS, EventCategory.NAVIGATION),
  screenClick: (screenName: string) => 
    logEvent(EventAction.CLICK, screenName, EventCategory.INTERACTION),
  sortChange: (sortBy: string) => 
    logEvent(EventAction.SORT, sortBy, EventCategory.FILTER),
};

/**
 * Track User Engagement events
 */
export const trackUserEngagement = {
  view: () => logEvent(EventAction.PAGE_VIEW, AnalyticsLabels.USER_ENGAGEMENT, EventCategory.NAVIGATION),
  graphInteraction: (graphType: string) => 
    logEvent(EventAction.CLICK, graphType, EventCategory.INTERACTION),
  dateRangeChange: (range: string) => 
    trackFilter(AnalyticsFilterType.DATE_RANGE, range, AnalyticsScreen.USER_ENGAGEMENT),
};

/**
 * Track Universal Query events
 */
export const trackUniversalQuery = {
  view: () => logEvent(EventAction.PAGE_VIEW, AnalyticsLabels.UNIVERSAL_QUERY, EventCategory.NAVIGATION),
  queryExecute: (queryType: string) => 
    logEvent(EventAction.SEARCH, queryType, EventCategory.SEARCH),
  queryError: (errorMessage: string) => 
    trackError(AnalyticsErrorType.QUERY_ERROR, errorMessage, AnalyticsScreen.UNIVERSAL_QUERY),
};

/**
 * Track Home Dashboard events
 */
export const trackHome = {
  view: () => logEvent(EventAction.PAGE_VIEW, AnalyticsLabels.HOME, EventCategory.NAVIGATION),
  widgetInteraction: (widgetName: string) => 
    logEvent(EventAction.CLICK, widgetName, EventCategory.INTERACTION),
  quickActionClick: (action: string) => 
    logEvent(EventAction.CLICK, AnalyticsLabelFormatter.quickAction(action), EventCategory.NAVIGATION),
};

/**
 * Track Settings events
 */
export const trackSettings = {
  view: () => logEvent(EventAction.PAGE_VIEW, AnalyticsLabels.SETTINGS, EventCategory.NAVIGATION),
  tabSwitch: (tab: string) => logEvent(EventAction.TAB_SWITCH, tab, EventCategory.NAVIGATION),
  settingChange: (setting: string, value: string) => 
    logEvent(EventAction.SETTINGS_CHANGE, AnalyticsLabelFormatter.settingChange(setting, value), EventCategory.USER),
};

/**
 * Track Login events
 */
export const trackLogin = {
  attempt: (method: string) => logEvent(EventAction.LOGIN, AnalyticsLabelFormatter.attempt(method), EventCategory.USER),
  success: (method: string) => logEvent(EventAction.LOGIN, AnalyticsLabelFormatter.success(method), EventCategory.USER),
  failure: (method: string, reason: string) => 
    logEvent(EventAction.LOGIN, AnalyticsLabelFormatter.failed(method, reason), EventCategory.USER),
  logout: () => logEvent(EventAction.LOGOUT, AnalyticsLabels.USER_LOGGED_OUT, EventCategory.USER),
};
