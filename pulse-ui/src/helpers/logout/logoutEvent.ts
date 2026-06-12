/**
 * Custom event for app-wide logout
 * This allows non-React code to trigger logout actions
 */
export const LOGOUT_EVENT = 'app:logout';

/**
 * Dispatches a logout event that should be handled by the App component
 */
export const dispatchLogoutEvent = (): void => {
  window.dispatchEvent(new CustomEvent(LOGOUT_EVENT));
};
