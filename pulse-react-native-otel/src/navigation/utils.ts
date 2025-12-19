const NAVIGATION_HISTORY_MAX_SIZE = 200;

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

