import { Pulse, type Span } from '../index';
import { Platform } from 'react-native';
import { SPAN_NAMES, ATTRIBUTE_KEYS, PULSE_TYPES } from '../pulse.constants';
import type { NavigationRoute, NavigationContainer } from './types';

export interface ScreenInteractiveState {
  screenInteractiveSpan: Span | undefined;
  currentInteractiveRouteKey: string | undefined;
}

let globalMarkContentReady: (() => void) | undefined;

export function createScreenInteractiveTracker(
  enabled: boolean,
  state: ScreenInteractiveState,
  navigationContainer: NavigationContainer | undefined
) {
  const nullScreenInteractive = (reason: string): void => {
    if (state.screenInteractiveSpan) {
      console.log(
        `[Pulse Navigation] [DEBUG] Nulling screen_interactive span (${reason}) for routeKey: ${state.currentInteractiveRouteKey}`
      );
      state.screenInteractiveSpan = undefined;
      state.currentInteractiveRouteKey = undefined;
    }
  };

  const startScreenInteractive = (route: NavigationRoute): void => {
    if (!enabled) {
      return;
    }

    if (state.screenInteractiveSpan) {
      nullScreenInteractive('starting new span');
    }

    state.screenInteractiveSpan = Pulse.startSpan(
      SPAN_NAMES.SCREEN_INTERACTIVE,
      {
        attributes: {
          [ATTRIBUTE_KEYS.PULSE_TYPE]: PULSE_TYPES.SCREEN_INTERACTIVE,
          [ATTRIBUTE_KEYS.SCREEN_NAME]: route.name,
          [ATTRIBUTE_KEYS.ROUTE_KEY]: route.key,
          [ATTRIBUTE_KEYS.PLATFORM]: Platform.OS as 'android' | 'ios',
        },
      }
    );
    state.currentInteractiveRouteKey = route.key;
    console.log(
      `[Pulse Navigation] [TEST] Started screen_interactive span for screen: ${route.name}, routeKey: ${route.key}`
    );
  };

  const endScreenInteractive = (): void => {
    if (state.screenInteractiveSpan) {
      state.screenInteractiveSpan.end();
      console.log(
        `[Pulse Navigation] [TEST] Ended screen_interactive span for routeKey: ${state.currentInteractiveRouteKey}`
      );
      state.screenInteractiveSpan = undefined;
      state.currentInteractiveRouteKey = undefined;
    }
  };

  const markContentReady = (): void => {
    try {
      if (!enabled) {
        console.warn(
          '[Pulse Navigation] [DEBUG] markContentReady called but screenInteractiveTracking is disabled'
        );
        return;
      }

      if (!state.screenInteractiveSpan) {
        console.log(
          '[Pulse Navigation] [TEST] markContentReady called but no active screen_interactive span (may have been nulled or not started yet)'
        );
        return;
      }

      const currentRoute = navigationContainer?.getCurrentRoute();
      if (!currentRoute) {
        console.warn(
          '[Pulse Navigation] [DEBUG] markContentReady called but no current route found'
        );
        nullScreenInteractive('no current route');
        return;
      }

      if (currentRoute.key !== state.currentInteractiveRouteKey) {
        console.warn(
          `[Pulse Navigation] [DEBUG] markContentReady called for wrong screen. Expected: ${state.currentInteractiveRouteKey}, Current: ${currentRoute.key}`
        );
        nullScreenInteractive('route mismatch');
        return;
      }

      endScreenInteractive();
    } catch (error) {
      console.error(
        '[Pulse Navigation] [DEBUG] Error in markContentReady:',
        error
      );
    }
  };

  globalMarkContentReady = markContentReady;

  return {
    startScreenInteractive,
    endScreenInteractive,
    nullScreenInteractive,
    markContentReady,
  };
}

export function markContentReady(): void {
  if (globalMarkContentReady) {
    globalMarkContentReady();
  } else {
    console.warn(
      '[Pulse Navigation] markContentReady called but navigation integration not initialized'
    );
  }
}

export function clearGlobalMarkContentReady(
  markContentReadyFn: () => void
): void {
  if (globalMarkContentReady === markContentReadyFn) {
    globalMarkContentReady = undefined;
  }
}