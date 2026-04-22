import type { RefObject } from 'react';

type NavigationContainerRef = RefObject<unknown> & {
  isReady?: () => boolean;
  current?: unknown;
  addListener?: (event: string, listener: () => void) => () => void;
};

/**
 * Calls `onReady` once the navigation container ref is usable (mirrors
 * {@link NavigationContainer} `onReady` without requiring that prop).
 * `onReady` should be the same callback you would pass to `onReady`.
 */
export function subscribeNavigationContainerReady(
  navigationRef: RefObject<any>,
  onReady: () => void
): () => void {
  const tryRegister = (): boolean => {
    const r = navigationRef as NavigationContainerRef;
    if (typeof r.isReady === 'function' && !r.isReady()) {
      return false;
    }
    if (!r.current) {
      return false;
    }
    onReady();
    return true;
  };

  if (tryRegister()) {
    return () => {};
  }

  // `isReady()` is not an event — our caller's effect won't re-run when it flips to true.
  // Re-check after navigation emits (initial state counts).
  const refWithListener = navigationRef as NavigationContainerRef;
  if (typeof refWithListener.addListener !== 'function') {
    return () => {};
  }

  const remove = refWithListener.addListener('state', () => {
    if (tryRegister()) {
      remove();
    }
  });

  return () => {
    remove();
  };
}
