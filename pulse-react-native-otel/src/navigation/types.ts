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
}