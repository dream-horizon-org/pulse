export type Routes = Record<string, Route>;

export type Route = {
  path: string;
  key: string;
  basePath: string;
  element?: React.ElementType;
};

export type RouteConfig = {
  path: string;
  key: string;
  basePath: string;
  element: React.ElementType;
};

export type NavbarItems = Array<NavbarItem>;

export type NavbarItem = {
  tabName: string;
  routeTo: string;
  path: string;
  iconSize: number;
  icon: React.ElementType;
};

export type ApiRoutesType = Record<string, ApiRoute>;

export type ApiRoute = {
  key: string;
  apiPath: string;
  method: string;
};

export type StreamverseRoutes = Record<string, StreamverseRouteRecordData>;

export type StreamverseRouteRecordData = {
  key: string;
  apiPath: string;
  method: string;
};

export enum INTERACTION_STATUS {
  STOPPED = "STOPPED",
  RUNNING = "RUNNING",
}
