import { PropsWithChildren, useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { getCookies } from "../../helpers/cookies";
import { COOKIES_KEY, ROUTES } from "../../constants";

interface InternalRouteGuardProps extends PropsWithChildren {
  requireSuperadmin?: boolean;
}

export function InternalRouteGuard({
  children,
  requireSuperadmin,
}: InternalRouteGuardProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const accessToken = getCookies(COOKIES_KEY.ACCESS_TOKEN);
  const systemRole = getCookies(COOKIES_KEY.SYSTEM_ROLE);

  useEffect(() => {
    if (!accessToken) {
      navigate(ROUTES.LOGIN.basePath, {
        replace: true,
        state: { from: location },
      });
      return;
    }
    if (!systemRole) {
      const tenantId = getCookies(COOKIES_KEY.TENANT_ID);
      navigate(
        tenantId ? `/${tenantId}/projects` : ROUTES.LOGIN.basePath,
        { replace: true },
      );
      return;
    }
    if (requireSuperadmin && systemRole !== "superadmin") {
      navigate(ROUTES.INTERNAL_TENANT_SELECTOR.path, { replace: true });
    }
  }, [accessToken, systemRole, requireSuperadmin, navigate, location]);

  if (!accessToken || !systemRole) return null;
  if (requireSuperadmin && systemRole !== "superadmin") return null;
  return <>{children}</>;
}
