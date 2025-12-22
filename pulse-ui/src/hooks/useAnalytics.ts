/**
 * Analytics Hook
 * 
 * Provides convenient methods for tracking user interactions within components.
 * Automatically includes screen context for all events.
 * 
 * Usage:
 * const { trackClick, trackAction, trackTime } = useAnalytics('ScreenName');
 */

import { useCallback, useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import {
  logEvent,
  trackTiming,
  trackError,
  EventCategory,
  EventAction,
  createTimeTracker,
} from '../helpers/googleAnalytics';
import {
  AnalyticsParams,
  AnalyticsTimingCategory,
} from '../helpers/googleAnalytics/analyticsConstants';

interface UseAnalyticsOptions {
  /** Auto-track time spent on screen when unmounting */
  trackTimeSpent?: boolean;
  /** Additional properties to include with all events */
  defaultProperties?: Record<string, string | number | boolean>;
}

export function useAnalytics(screenName: string, options: UseAnalyticsOptions = {}) {
  const location = useLocation();
  const timeTrackerRef = useRef<ReturnType<typeof createTimeTracker> | null>(null);
  const { trackTimeSpent = true, defaultProperties = {} } = options;

  // Start time tracking on mount
  useEffect(() => {
    if (trackTimeSpent) {
      timeTrackerRef.current = createTimeTracker(screenName);
    }

    // Track screen view
    logEvent(
      EventAction.PAGE_VIEW,
      screenName,
      EventCategory.NAVIGATION,
      undefined,
      { [AnalyticsParams.PATH]: location.pathname, ...defaultProperties }
    );

    return () => {
      // Track time spent when leaving screen
      if (trackTimeSpent && timeTrackerRef.current) {
        const duration = timeTrackerRef.current.end();
        // Only track if user spent more than 1 second
        if (duration > 1000) {
          trackTiming(AnalyticsTimingCategory.SCREEN_TIME, screenName, duration);
        }
      }
    };
  }, [screenName, location.pathname, trackTimeSpent, defaultProperties]);

  /**
   * Track a click event
   */
  const trackClick = useCallback((elementName: string, additionalData?: Record<string, string | number>) => {
    logEvent(
      EventAction.CLICK,
      elementName,
      EventCategory.INTERACTION,
      undefined,
      { [AnalyticsParams.SCREEN]: screenName, ...defaultProperties, ...additionalData }
    );
  }, [screenName, defaultProperties]);

  /**
   * Track a custom action
   */
  const trackAction = useCallback((
    action: string,
    label: string,
    category: string = EventCategory.INTERACTION,
    value?: number,
    additionalData?: Record<string, string | number | boolean>
  ) => {
    logEvent(
      action,
      label,
      category,
      value,
      { [AnalyticsParams.SCREEN]: screenName, ...defaultProperties, ...additionalData }
    );
  }, [screenName, defaultProperties]);

  /**
   * Track a filter change
   */
  const trackFilter = useCallback((filterType: string, filterValue: string) => {
    logEvent(
      EventAction.FILTER_APPLY,
      `${filterType}: ${filterValue}`,
      EventCategory.FILTER,
      undefined,
      { [AnalyticsParams.SCREEN]: screenName, [AnalyticsParams.FILTER_TYPE]: filterType, ...defaultProperties }
    );
  }, [screenName, defaultProperties]);

  /**
   * Track a search
   */
  const trackSearch = useCallback((query: string, resultsCount?: number) => {
    logEvent(
      EventAction.SEARCH,
      query.substring(0, 100),
      EventCategory.SEARCH,
      resultsCount,
      { [AnalyticsParams.SCREEN]: screenName, ...defaultProperties }
    );
  }, [screenName, defaultProperties]);

  /**
   * Track an error
   */
  const trackScreenError = useCallback((errorType: string, message: string) => {
    trackError(errorType, message, screenName);
  }, [screenName]);

  /**
   * Track expand/collapse
   */
  const trackExpand = useCallback((itemName: string, isExpanded: boolean) => {
    logEvent(
      isExpanded ? EventAction.EXPAND : EventAction.COLLAPSE,
      itemName,
      EventCategory.INTERACTION,
      undefined,
      { [AnalyticsParams.SCREEN]: screenName, ...defaultProperties }
    );
  }, [screenName, defaultProperties]);

  /**
   * Track tab switch
   */
  const trackTabSwitch = useCallback((tabName: string) => {
    logEvent(
      EventAction.TAB_SWITCH,
      tabName,
      EventCategory.NAVIGATION,
      undefined,
      { [AnalyticsParams.SCREEN]: screenName, ...defaultProperties }
    );
  }, [screenName, defaultProperties]);

  /**
   * Track create/save/delete operations
   */
  const trackCrud = useCallback((
    operation: 'create' | 'read' | 'update' | 'delete' | 'save',
    itemType: string,
    itemId?: string
  ) => {
    const actionMap = {
      create: EventAction.CREATE,
      read: EventAction.READ,
      update: EventAction.UPDATE,
      delete: EventAction.DELETE,
      save: EventAction.SAVE,
    };

    logEvent(
      actionMap[operation],
      itemId ? `${itemType}: ${itemId}` : itemType,
      EventCategory.INTERACTION,
      undefined,
      { [AnalyticsParams.SCREEN]: screenName, [AnalyticsParams.ITEM_TYPE]: itemType, ...defaultProperties }
    );
  }, [screenName, defaultProperties]);

  /**
   * Create a time tracker for measuring operation duration
   */
  const measureTime = useCallback((operationName: string) => {
    const startTime = Date.now();
    return {
      end: () => {
        const duration = Date.now() - startTime;
        trackTiming(screenName, operationName, duration);
        return duration;
      }
    };
  }, [screenName]);

  return {
    trackClick,
    trackAction,
    trackFilter,
    trackSearch,
    trackError: trackScreenError,
    trackExpand,
    trackTabSwitch,
    trackCrud,
    measureTime,
    // Expose raw logEvent for custom needs
    logEvent,
    // Constants for consistency
    EventCategory,
    EventAction,
  };
}

export default useAnalytics;

