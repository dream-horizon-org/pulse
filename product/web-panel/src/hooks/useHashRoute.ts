import { useCallback, useEffect, useState } from 'react';

function getRoute(): string {
  const hash = window.location.hash;
  if (!hash || hash === '#') return '';
  // Routes look like "#/frameworks/execution-framework"
  return hash.replace(/^#\/?/, '');
}

export function useHashRoute() {
  const [route, setRoute] = useState<string>(getRoute());

  useEffect(() => {
    const onChange = () => setRoute(getRoute());
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);

  const navigate = useCallback((slug: string) => {
    window.location.hash = `/${slug}`;
  }, []);

  return { route, navigate };
}
