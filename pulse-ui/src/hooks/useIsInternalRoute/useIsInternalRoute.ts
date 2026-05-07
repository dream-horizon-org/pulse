import { useLocation } from "react-router-dom";
import { INTERNAL_ROUTE_PREFIX } from "../../constants";

export const useIsInternalRoute = (): boolean => {
  const { pathname } = useLocation();
  return pathname.startsWith(INTERNAL_ROUTE_PREFIX);
};
