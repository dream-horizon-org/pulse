import { AppShell } from "@mantine/core";
import { LayoutProps } from "./Layout.interface";
import {
  COOKIES_KEY,
  HEADER_CONFIG,
  LAYOUT_PAGE_CONSTANTS,
  ROUTES,
} from "../../constants";
import { TENANT_ROLES, TenantRole } from "../../constants/Roles";
import { TIERS, TierType } from "../../constants/Tiers";
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
import { ProjectInitializingModal } from "../ProjectInitializingModal";
import { useTenantContext, useProjectContext } from "../../contexts";
import { useGetTncStatus } from "../../hooks/useGetTncStatus";
import { TncAcceptance } from "../../screens/TncAcceptance";

export function Layout({ children }: LayoutProps) {
  const navigate = useNavigate();
  const [opened, { toggle }] = useDisclosure(false);
  const { pathname } = useLocation();
  const { setTenantInfo, tenantId, userRole } = useTenantContext();
  const { isInitializing } = useProjectContext();
  const [checkingCredentials, setCheckingCredentials] = useState(true);
  const displayMessage = useRef<string>(
    LAYOUT_PAGE_CONSTANTS.CHECKING_CREDENTIALS,
  );

  //   const isProjectRoute = pathname.startsWith('/projects/');
  //   const isOrganizationRoute = pathname.startsWith('/organization/');
  //   const shouldShowHeader = isProjectRoute || isOrganizationRoute;

  // Show header on all authenticated pages except login and initial onboarding
  // This includes: project routes, organization routes (/:orgId/projects, /:orgId/members
  const isLoginPage = pathname === ROUTES.LOGIN.path;
  const isOnboardingPage = pathname === ROUTES.ONBOARDING.basePath;
  const isInitialOnboarding = pathname === ROUTES.ONBOARDING.basePath;
  const isInternalRoute = pathname.startsWith("/internal");
  const shouldShowHeader = !isLoginPage && !isInitialOnboarding && !isInternalRoute;

  useEffect(() => {
    const initializeAuth = async () => {
      const token = getCookies(COOKIES_KEY.ACCESS_TOKEN);
      if (!token || token === "undefined") {
        setCheckingCredentials(false);
        if (!isLoginPage && !isOnboardingPage) {
          navigate(ROUTES.LOGIN.basePath);
        }
        return;
      }

      if (isInternalRoute) {
        setCheckingCredentials(false);
        return;
      }

      const systemRole = getCookies(COOKIES_KEY.SYSTEM_ROLE);
      const cookieTenantId = getCookies(COOKIES_KEY.TENANT_ID);

      if (systemRole && (!cookieTenantId || cookieTenantId === "undefined")) {
        setCheckingCredentials(false);
        navigate(ROUTES.INTERNAL_TENANT_SELECTOR.path, { replace: true });
        return;
      }

      const cookieTenantName = getCookies(COOKIES_KEY.TENANT_NAME);
      const cookieTenantRole = getCookies(COOKIES_KEY.TENANT_ROLE);
      const cookieTier = getCookies(COOKIES_KEY.TIER);
      if (cookieTenantId && cookieTenantId !== "undefined" && !tenantId) {
        try {
          setTenantInfo({
            tenantId: cookieTenantId,
            tenantName: cookieTenantName || "",
            userRole: (cookieTenantRole as TenantRole) || TENANT_ROLES.MEMBER,
            tier: (cookieTier as TierType) || TIERS.FREE,
          });
        } catch (error) {
          console.error("[Layout] Failed to initialize tenant context:", error);
        }
      }

      setCheckingCredentials(false);
    };

    initializeAuth();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname]);

  const tncEnabled =
    !!tenantId &&
    userRole === TENANT_ROLES.ADMIN &&
    !isLoginPage &&
    !isOnboardingPage;
  const { data: tncData, isLoading: tncLoading } = useGetTncStatus(tncEnabled);

  if (checkingCredentials) {
    return <LoaderWithMessage loadingMessage={displayMessage.current} />;
  }

  if (isLoginPage) {
    return <Login />;
  }

  const navbarWidth = opened ? 255 : 95;

  if (isOnboardingPage || isInternalRoute) {
    return <>{children}</>;
  }

  if (tncEnabled && tncLoading) {
    return <LoaderWithMessage loadingMessage="Checking policies..." />;
  }

  const tncStatus = tncData?.data;
  if (tncStatus && !tncStatus.accepted) {
    return (
      <TncAcceptance
        tncStatus={tncStatus}
        onAccepted={() => {
          const redirectPath = tenantId ? `/${tenantId}/projects` : "/";
          navigate(redirectPath);
        }}
      />
    );
  }

  return (
    <>
      <AppShell
        header={shouldShowHeader ? HEADER_CONFIG : undefined}
        navbar={{
          width: navbarWidth,
          breakpoint: "sm",
          collapsed: { mobile: !opened },
        }}
        padding={0}
        styles={{
          navbar: {
            height: "100vh",
            top: 0,
            zIndex: 0,
          },
          header: shouldShowHeader
            ? {
                left: navbarWidth,
                width: `calc(100% - ${navbarWidth}px)`,
                zIndex: 100,
              }
            : undefined,
        }}
      >
        {shouldShowHeader && <Header toggle={toggle} opened={opened} />}
        <Navbar toggle={toggle} opened={opened} />
        <Main>
          <ProjectGuard>{children}</ProjectGuard>
        </Main>
      </AppShell>
      <ProjectInitializingModal opened={isInitializing} />
    </>
  );
}
