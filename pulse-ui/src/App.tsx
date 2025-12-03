import { MantineProvider } from "@mantine/core";
import { Notifications } from "@mantine/notifications";
import {
  BrowserRouter as Router,
  Route,
  Routes,
  useLocation,
} from "react-router-dom";
import { theme } from "./theme";
import { Layout } from "./components/Layout";
import { ROUTES } from "./constants";
import { NotFound } from "./components/NotFound";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "./clients/react-query";
import "@mantine/dates/styles.css";
import { useEffect } from "react";
import { initGA, logPageView } from "./helpers/googleAnalytics";

export default function App() {
  useEffect(() => {
    initGA();
  }, []);

  return (
    <MantineProvider theme={theme}>
      <Notifications position="top-center" />
      <Router basename={process.env.PUBLIC_URL || '/'}>
        <PageTracker />
        <QueryClientProvider client={queryClient}>
          <Layout>
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
          </Layout>
        </QueryClientProvider>
      </Router>
    </MantineProvider>
  );
}

// Track page views on route change
const PageTracker: React.FC = () => {
  const location = useLocation();

  useEffect(() => {
    logPageView(location.pathname);
  }, [location]);

  return null;
};
