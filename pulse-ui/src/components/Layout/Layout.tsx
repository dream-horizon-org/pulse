import { AppShell } from "@mantine/core";
import { LayoutProps } from "./Layout.interface";
import { COOKIES_KEY, HEADER_CONFIG, LAYOUT_PAGE_CONSTANTS, ROUTES } from "../../constants";
import { useDisclosure } from "@mantine/hooks";
import { Header } from "../Header";
import { Navbar } from "../Navbar";
import { Main } from "../Main";
import { useLocation, useNavigate } from "react-router-dom";
import { Login } from "../../screens/Login";
import { useEffect, useRef, useState } from "react";
import { LoaderWithMessage } from "../LoaderWithMessage";
import { getCookies } from "../../helpers/cookies";
import { ProjectGuard } from "../ProjectGuard";
import { useTenantContext } from "../../contexts";

export function Layout({ children }: LayoutProps) {
  const navigate = useNavigate();
  const [opened, { toggle }] = useDisclosure(false);
  const { pathname } = useLocation();
  const { setTenantInfo, tenantId } = useTenantContext();
  const [checkingCredentials, setCheckingCredentials] = useState(true);
  const displayMessage = useRef<string>(
    LAYOUT_PAGE_CONSTANTS.CHECKING_CREDENTIALS,
  );

  // Check if we're on a project route or organization route (both need header)
  const isProjectRoute = pathname.startsWith('/projects/');
  const isOrganizationRoute = pathname.startsWith('/organization/');
  const shouldShowHeader = isProjectRoute || isOrganizationRoute;

  useEffect(() => {
    const initializeAuth = async () => {
      const token = getCookies(COOKIES_KEY.ACCESS_TOKEN);
      if (!token || token === "undefined") {
        setCheckingCredentials(false);
        navigate(ROUTES.LOGIN.basePath);
        return;
      }

      // Initialize tenant context if tenantId exists in cookies but not in context
      const cookieTenantId = getCookies(COOKIES_KEY.TENANT_ID);
      if (cookieTenantId && cookieTenantId !== 'undefined' && !tenantId) {
        console.log('[Layout] Initializing tenant context from cookies');
        try {
          // Set tenant info (which will automatically trigger project fetch)
          setTenantInfo({
            tenantId: cookieTenantId,
            tenantName: '', // Will be populated from projects API
            userRole: 'member', // Default role, will be updated from projects API
            tier: 'free', // Default tier, will be updated from login/projects API
          });
        } catch (error) {
          console.error('[Layout] Failed to initialize tenant context:', error);
        }
      }

      setCheckingCredentials(false);
    };

    initializeAuth();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (checkingCredentials) {
    return <LoaderWithMessage loadingMessage={displayMessage.current} />;
  }

  if (pathname === ROUTES.LOGIN.path) {
    return <Login />;
  }

  return (
    <AppShell
      header={shouldShowHeader ? HEADER_CONFIG : undefined}
      navbar={{
        width: opened ? 255 : 95,
        breakpoint: "sm",
        collapsed: { mobile: !opened },
      }}
      padding="md"
    >
      {shouldShowHeader && <Header toggle={toggle} opened={opened} />}
      <Navbar toggle={toggle} opened={opened} />
      <Main>
        <ProjectGuard>
          {children}
        </ProjectGuard>
      </Main>
    </AppShell>
  );
}
