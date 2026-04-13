export interface NavigationRoute {
  name: string;
  key: string;
  params?: Record<string, any>;
}

export interface NavigationContainer {
  addListener: (
    type: string,
    listener: (event?: unknown) => void
  ) => { remove: () => void } | void;
  getCurrentRoute: () => NavigationRoute | undefined;
}

export interface NavigationIntegrationOptions {
  screenSessionTracking?: boolean;
  screenNavigationTracking?: boolean;
  screenInteractiveTracking?: boolean;
  /**
   * When `true`, registers once the container ref reports ready (e.g. Expo Router’s
   * `useNavigationContainerRef()`), without needing `NavigationContainer`’s `onReady`.
   * Requires a ref that implements `isReady()` (React Navigation / Expo Router).
   * If you must defer until bootstrap finishes, mount the navigator only after that
   * (e.g. return a splash screen until `ready`, then render `Stack`).
   */
  registerWhenContainerReady?: boolean;
}

export const DEFAULT_NAVIGATION_OPTIONS: Readonly<
  Required<NavigationIntegrationOptions>
> = {
  screenSessionTracking: true,
  screenNavigationTracking: true,
  screenInteractiveTracking: false,
  registerWhenContainerReady: false,
};

export interface ReactNavigationIntegration {
  registerNavigationContainer: (
    maybeNavigationContainer: unknown
  ) => () => void;
  markContentReady: () => void;
}
