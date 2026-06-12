const NAVIGATION_HISTORY_MAX_SIZE = 200;

export const LOG_TAGS = {
  NAVIGATION: '[Pulse Navigation]',
  SCREEN_LOAD: '[Pulse Screen Load]',
  SCREEN_SESSION: '[Pulse Screen Session]',
  SCREEN_INTERACTIVE: '[Pulse Screen Interactive]',
} as const;

export function pushRecentRouteKey(
  recentRouteKeys: string[],
  key: string
): string[] {
  const updated = [...recentRouteKeys, key];
  if (updated.length > NAVIGATION_HISTORY_MAX_SIZE) {
    return updated.slice(updated.length - NAVIGATION_HISTORY_MAX_SIZE);
  }
  return updated;
}
