import { useEffect } from "react";
import { useLocation } from "react-router-dom";

/**
 * Resets scroll to the top when the pathname changes. React Router (BrowserRouter + Routes)
 * does not scroll the document on client-side navigation by default.
 */
export function ScrollToTop() {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: "auto" });
    document.documentElement.scrollTop = 0;
    document.body.scrollTop = 0;
    document.querySelector("main")?.scrollTo({ top: 0, left: 0, behavior: "auto" });
  }, [pathname]);

  return null;
}
