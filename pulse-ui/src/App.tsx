import { MantineProvider } from "@mantine/core";
import { ModalsProvider } from "@mantine/modals";
import { Notifications } from "@mantine/notifications";
import {
  BrowserRouter as Router,
  Route,
  Routes,
  useLocation,
} from "react-router-dom";
import { theme } from "./theme";
import { Layout } from "./components/Layout";
import { ROUTES } from "./routes";
import { NotFound } from "./components/NotFound";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "./clients/react-query";
import { AppContextProvider } from "./contexts";
import "@mantine/dates/styles.css";
import { Suspense, useEffect, type FC } from "react";
import { initGA, logPageView } from "./helpers/googleAnalytics";
import { SessionReplayFilterProvider } from "./contexts/SessionReplayFilterContext";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { ScrollToTop } from "./components/ScrollToTop/ScrollToTop";
import { PulseRumProvider, PulseRumRouterEvents } from "./pulse-web-rum";

export default function App() {
  useEffect(() => {
    initGA();
  }, []);

  const routedApp = (
    <Router basename={process.env.PUBLIC_URL || "/"}>
      <PulseRumRouterEvents />
      <ScrollToTop />
      <PageTracker />
      <QueryClientProvider client={queryClient}>
        <SessionReplayFilterProvider>
          <AppContextProvider>
            <Layout>
              <ErrorBoundary>
                <Suspense fallback={null}>
                  <Routes>
                    {Object.entries(ROUTES).map(([_, value]) => {
                      const Component = value.element;
                      return (
                        <Route
                          key={value.key}
                          path={value.path}
                          element={<Component />}
                        />
                      );
                    })}
                    <Route path="*" element={<NotFound />} />
                  </Routes>
                </Suspense>
              </ErrorBoundary>
            </Layout>
          </AppContextProvider>
        </SessionReplayFilterProvider>
      </QueryClientProvider>
    </Router>
  );

  return (
    <MantineProvider theme={theme}>
      <ModalsProvider>
        <Notifications position="top-center" />
        <PulseRumProvider>{routedApp}</PulseRumProvider>
      </ModalsProvider>
    </MantineProvider>
  );
}

// Track page views on route change
const PageTracker: FC = () => {
  const location = useLocation();

  useEffect(() => {
    logPageView(location.pathname);
  }, [location]);

  return null;
};
