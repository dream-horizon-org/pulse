import {
  AppShellFooterConfiguration,
  AppShellHeaderConfiguration,
  AppShellNavbarConfiguration,
  ComboboxItem,
} from "@mantine/core";
import {
  IconBell,
  IconListDetails,
  IconHome,
  IconActivityHeartbeat,
  IconDeviceDesktop,
  IconNetwork,
  IconUsers,
  IconDatabaseSearch,
} from "@tabler/icons-react";
import {
  CiritcalInteractionDetails,
  CriticalInteractionDetailsFilterValues,
  TimeFilter,
} from "../screens/CriticalInteractionDetails";
import { CriticalInteractionList } from "../screens/CriticalInteractionList";
import { NavbarItems, Routes, StreamverseRoutes } from "./Constants.interface";
import { v4 as uuidV4 } from "uuid";
import { CriticalInteractionDetailsFilterOptionsResponse } from "../helpers/getCriticalInteractionDetailsFilterOptions";
import {
  CriticalInteractionForm,
  CriticalInteractionFormSteps,
  CriticalInteractionFormStepsRecords,
  EventFilters,
  EventSequenceData,
  FormSteps,
} from "../screens/CriticalInteractionForm";
import { Login } from "../screens/Login";
import { UniversalEventQuery } from "../screens/UniversalEventQuery/UniversalEventQuery";
import { Home } from "../screens/Home";
import { AppVitals, IssueDetail, OccurrenceDetail } from "../screens/AppVitals";
import { SessionTimeline } from "../screens/SessionTimeline";
import { ScreenList } from "../screens/ScreenList";
import { ScreenDetail } from "../screens/ScreenDetail";
import { NetworkDetail } from "../screens/NetworkDetail";
import { NetworkList } from "../screens/NetworkList";
import { UserEngagement } from "../screens/UserEngagement";
import { ComingSoon } from "../screens/ComingSoon";
import { AlertListingPage } from "../screens/AlertListingPage";
import { AlertForm } from "../screens/AlertFormWizard";
import { AlertDetail } from "../screens/AlertDetail";
import { OperatorType } from "../screens/AlertForm/AlertForm.interface";
import { RealTimeQuery } from "../screens/RealTimeQuery";

export const APP_NAME: string = "Pulse";

export const HELP_BAR_TEXT: string = "About Critical Interaction";

export const HELP_LINK: string =
  "https://dream11.atlassian.net/wiki/spaces/FE/pages/3222176234/SOP+User+experience+monitoring+dashboard";

export const HEADER_CONFIG: AppShellHeaderConfiguration = {
  height: 60,
};

export const FOOTER_CONFIG: AppShellFooterConfiguration = {
  height: 60,
};

export const NAVBAR_CONFIG: AppShellNavbarConfiguration = {
  width: 240,
  breakpoint: "sm",
};

export const API_BASE_URL: string =
  process.env.REACT_APP_PULSE_SERVER_URL ?? "";

export const PASCAL_CASE_FORM_REGEX: RegExp = /(^[A-Z])\w+[a-z]$/;

export const FORM_REGEX: RegExp = /^[a-z]*$/;

export const REQUEST_TIMEOUT: number = 60000;

// Alerts constants
export const ALERTS_SEARCH_PLACEHOLDER: string = "Search your alert here";

export const CREATE_ALERT: string = "Create alert";

export const NO_ALERT_CONFIGURED: string = "No alerts have been configured";
export const IS_UAT: boolean =
  process.env.REACT_APP_PULSE_SERVER_URL?.includes("-uat") ?? false;

export const ROUTES: Routes = {
  HOME: {
    key: "HOME",
    basePath: "/",
    path: "/",
    element: Home,
  },
  USER_ENGAGEMENT: {
    key: "USER_ENGAGEMENT",
    basePath: "/user-engagement",
    path: "/user-engagement",
    element: UserEngagement,
  },
  CRITICAL_INTERACTIONS: {
    key: "CRITICAL_INTERACTIONS",
    basePath: "/interactions",
    path: "/interactions",
    element: CriticalInteractionList,
  },
  CRITICAL_INTERACTION_FORM: {
    key: "CRITICAL_INTERACTION_FORM",
    basePath: "/critical-interaction-form",
    path: "/critical-interaction-form/*",
    element: CriticalInteractionForm,
  },
  ALL_INTERACTION_DETAILS: {
    key: "ALL_INTERACTION_DETAILS",
    basePath: "/user-experience",
    path: "/user-experience",
    element: CiritcalInteractionDetails,
  },
  CRITICAL_INTERACTION_DETAILS: {
    key: "CRITICAL_INTERACTION_DETAILS",
    basePath: "/interaction-details",
    path: "/interaction-details/*",
    element: CiritcalInteractionDetails,
  },
  LOGIN: {
    key: "LOGIN",
    basePath: "/login",
    path: "/login",
    element: Login,
  },
  UNIVERSAL_QUERYING: {
    key: "UNIVERSAL_QUERYING",
    basePath: "/universal-querying",
    path: "/universal-querying",
    element: UniversalEventQuery,
  },
  APP_VITALS: {
    key: "APP_VITALS",
    basePath: "/app-vitals",
    path: "/app-vitals",
    element: AppVitals,
  },
  APP_VITALS_ISSUE_DETAIL: {
    key: "APP_VITALS_ISSUE_DETAIL",
    basePath: "/app-vitals/:groupId",
    path: "/app-vitals/:groupId",
    element: IssueDetail,
  },
  APP_VITALS_OCCURRENCE_DETAIL: {
    key: "APP_VITALS_OCCURRENCE_DETAIL",
    basePath: "/app-vitals/:issueId/occurrence/:occurrenceId",
    path: "/app-vitals/:issueId/occurrence/:occurrenceId",
    element: OccurrenceDetail,
  },
  SESSION_TIMELINE: {
    key: "SESSION_TIMELINE",
    basePath: "/session/:id",
    path: "/session/:id",
    element: SessionTimeline,
  },
  SCREENS: {
    key: "SCREENS",
    basePath: "/screens",
    path: "/screens",
    element: ScreenList,
  },
  SCREEN_DETAILS: {
    key: "SCREEN_DETAILS",
    basePath: "/screens",
    path: "/screens/:screenName",
    element: ScreenDetail,
  },
  NETWORK_LIST: {
    key: "NETWORK_LIST",
    basePath: "/network-apis",
    path: "/network-apis",
    element: NetworkList,
  },
  NETWORK_DETAIL: {
    key: "NETWORK_DETAIL",
    basePath: "/network-apis",
    path: "/network-apis/:apiId",
    element: NetworkDetail,
  },
  COMING_SOON: {
    key: "COMING_SOON",
    basePath: "/coming-soon",
    path: "/coming-soon",
    element: ComingSoon,
  },
  ALERTS: {
    key: "ALERTS",
    basePath: "/alerts",
    path: "/alerts",
    element: AlertListingPage,
  },
  ALERT_DETAIL: {
    key: "ALERT_DETAIL",
    basePath: "/alerts",
    path: "/alerts/:alertId",
    element: AlertDetail,
  },
  ALERTS_FORM: {
    key: "ALERTS_FORM",
    basePath: "/configure-alert",
    path: "/configure-alert/*",
    element: AlertForm,
  },
  REALTIME_QUERY: {
    key: "REALTIME_QUERY",
    basePath: "/realtime-query",
    path: "/realtime-query",
    element: RealTimeQuery,
  },
};

export const NAVBAR_ITEMS: NavbarItems = [
  {
    tabName: "Home",
    icon: IconHome,
    routeTo: ROUTES.HOME.basePath,
    path: ROUTES.HOME.path,
    iconSize: 25,
  },
  {
    tabName: "User Engagement",
    icon: IconUsers,
    routeTo: ROUTES.USER_ENGAGEMENT.basePath,
    path: ROUTES.USER_ENGAGEMENT.path,
    iconSize: 25,
  },
  {
    tabName: "Interactions",
    icon: IconListDetails,
    routeTo: ROUTES.CRITICAL_INTERACTIONS.basePath,
    path: ROUTES.CRITICAL_INTERACTIONS.path,
    iconSize: 25,
  },
  {
    tabName: "App Vitals",
    icon: IconActivityHeartbeat,
    routeTo: ROUTES.APP_VITALS.basePath,
    path: ROUTES.APP_VITALS.path,
    iconSize: 25,
  },
  {
    tabName: "Screens",
    icon: IconDeviceDesktop,
    routeTo: ROUTES.SCREENS.basePath,
    path: ROUTES.SCREENS.path,
    iconSize: 25,
  },
  {
    tabName: "Network APIs",
    icon: IconNetwork,
    routeTo: ROUTES.NETWORK_LIST.basePath,
    path: ROUTES.NETWORK_LIST.path,
    iconSize: 25,
  },
  {
    tabName: "Alerts",
    icon: IconBell,
    routeTo: ROUTES.ALERTS.basePath,
    path: ROUTES.ALERTS.path,
    iconSize: 25,
  },
  {
    tabName: "Query Builder",
    icon: IconDatabaseSearch,
    routeTo: ROUTES.REALTIME_QUERY.basePath,
    path: ROUTES.REALTIME_QUERY.path,
    iconSize: 25,
  },
];

export const API_METHODS: Record<string, string> = {
  GET: "GET",
  POST: "POST",
  DELETE: "DELETE",
  PUT: "PUT",
  PATCH: "PATCH",
};

export const API_ROUTES: StreamverseRoutes = {
  CREATE_JOB: {
    key: "CREATE_JOB",
    apiPath: `/v1/interactions`,
    method: API_METHODS.POST,
  },
  GET_SCREEN_NAME_EVENTS_MAPPING: {
    key: "GET_SCREEN_NAME_EVENTS_MAPPING",
    apiPath: `/v1/events`,
    method: API_METHODS.GET,
  },
  GET_INTERACTIONS: {
    key: "GET_INTERACTIONS",
    apiPath: `/v1/interactions`,
    method: API_METHODS.GET,
  },
  GET_SESSION_REPLAYS: {
    key: "GET_SESSION_REPLAYS",
    apiPath: `/v1/session-replays`,
    method: API_METHODS.GET,
  },
  DATA_QUERY: {
    key: "DATA_QUERY",
    apiPath: `/v1/interactions/performance-metric/distribution`,
    method: API_METHODS.POST,
  },
  GET_JOB_FILTERS: {
    key: "GET_JOB_FILTERS",
    apiPath: `/v2/getJobFilters`,
    method: API_METHODS.GET,
  },
  UPDATE_JOB_STATUS: {
    key: "UPDATE_JOB_STATUS",
    apiPath: `/v3/updateJobStatus`,
    method: API_METHODS.PUT,
  },
  GET_JOB_DETAILS: {
    key: "GET_JOB_DETAILS",
    apiPath: `/v2/getJobDetails`,
    method: API_METHODS.GET,
  },
  DELETE_JOB: {
    key: "DELETE_JOB",
    apiPath: `/v3/deleteJob`,
    method: API_METHODS.DELETE,
  },
  GET_JOB_STATUS: {
    key: "GET_JOB_STATUS",
    apiPath: `/v3/getJobStatus`,
    method: API_METHODS.GET,
  },
  DELETE_GRAFANA_DASHBOARD: {
    key: "DELETE_GRAFANA_DASHBOARD",
    apiPath: `/deleteGrafanaDashboard`,
    method: API_METHODS.DELETE,
  },
  GET_GRAFANA_DASHBOARD_UID_BY_JOB_NAME: {
    key: "GET_GRAFANA_DASHBOARD_UID_BY_JOB_NAME",
    apiPath: `/getGrafanaDashboardUidByJobName`,
    method: API_METHODS.GET,
  },
  UPDATE_JOB: {
    key: "UPDATE_JOB",
    apiPath: `/v1/interactions`,
    method: API_METHODS.PUT,
  },
  DELETE_NODES: {
    key: "DELETE_NODES",
    apiPath: `/deleteJobNodes`,
    method: API_METHODS.DELETE,
  },
  REFRESH_TOKEN: {
    key: "REFRESH_TOKEN",
    apiPath: `/v1/auth/token/refresh`,
    method: API_METHODS.POST,
  },
  AUTHENTICATE: {
    key: "AUTHENTICATE",
    apiPath: `/v1/auth/social/authenticate`,
    method: API_METHODS.POST,
  },
  GET_APDEX_SCORE: {
    key: "GET_APDEX_SCORE",
    apiPath: `/v2/getApdexScore`,
    method: API_METHODS.POST,
  },
  GET_CACHED_APDEX_SCORE: {
    key: "GET_CACHED_APDEX_SCORE",
    apiPath: `/v3/metric/getApdexScore`,
    method: API_METHODS.POST,
  },
  GET_INTERACTION_TIME_DIFF_PERCENTILE: {
    key: "GET_INTERACTION_TIME_DIFF_PERCENTILE",
    apiPath: `/v2/getInteractionTime`,
    method: API_METHODS.POST,
  },
  GET_CACHED_INTERACTION_TIME_DIFF_PERCENTILE: {
    key: "GET_CACHED_INTERACTION_TIME_DIFF_PERCENTILE",
    apiPath: `/v3/metric/composite/getInteractionTime`,
    method: API_METHODS.POST,
  },
  GET_INTERACTION_CATEGORISATION: {
    key: "GET_INTERACTION_CATEGORISATION",
    apiPath: `/v2/getUserCategorization`,
    method: API_METHODS.POST,
  },
  GET_CACHED_INTERACTION_CATEGORISATION: {
    key: "GET_CACHED_INTERACTION_CATEGORISATION",
    apiPath: `/v3/metric/composite/getInteractionCategory`,
    method: API_METHODS.POST,
  },
  GET_ERROR_RATE: {
    key: "GET_ERROR_RATE",
    apiPath: `/v2/getErrorRate`,
    method: API_METHODS.POST,
  },
  GET_CACHED_ERROR_RATE: {
    key: "GET_CACHED_ERROR_RATE",
    apiPath: `/v3/metric/getErrorRate`,
    method: API_METHODS.POST,
  },
  GET_USER_EVENTS: {
    key: "GET_USER_EVENTS",
    apiPath: `/v2/getUserEvent`,
    method: API_METHODS.POST,
  },
  GET_QUERY_ID: {
    key: "GET_QUERY_ID",
    apiPath: `/v2/getQueryResult`,
    method: API_METHODS.POST,
  },
  GET_VALIDATE_UNIVERSAL_QUERY: {
    key: "GET_VALIDATE_UNIVERSAL_QUERY",
    apiPath: `/v2/validateQuery`,
    method: API_METHODS.POST,
  },
  GET_UNIVERSAL_QUERY_TABLES: {
    key: "GET_UNIVERSAL_QUERY_TABLES",
    apiPath: `/v2/getListOfTables`,
    method: API_METHODS.GET,
  },
  GET_UNIVERSAL_QUERY_COLUMNS: {
    key: "GET_UNIVERSAL_QUERY_COLUMNS",
    apiPath: `/v2/getColumnNamesOfTable`,
    method: API_METHODS.GET,
  },
  GET_QUERY_RESULT_FROM_QUERY_ID: {
    key: "GET_QUERY_RESULT_FROM_QUERY_ID",
    apiPath: `/v2/fetchQueryData`,
    method: API_METHODS.POST,
  },
  GET_GRAPH_DATA_FROM_JOB_ID: {
    key: "GET_QUERY_RESULT_FROM_QUERY_ID",
    apiPath: `{graphEndPoint}/job/{jobId}`,
    method: API_METHODS.POST,
  },
  CANCEL_QUERY: {
    key: "CANCEL_QUERY",
    apiPath: `/v2/cancelQueryRequest`,
    method: API_METHODS.POST,
  },
  GET_QUERY_HISTORY: {
    key: "GET_QUERY_HISTORY",
    apiPath: `/v2/getQuery/user`,
    method: API_METHODS.GET,
  },
  GET_SUGGESTED_QUERIES: {
    key: "GET_SUGGESTED_QUERIES",
    apiPath: `/v2/getQuery/suggested`,
    method: API_METHODS.GET,
  },
  GET_ANALYSIS_REPORT: {
    key: "GET_ANALYSIS_REPORT",
    apiPath: `/analytics-report`,
    method: API_METHODS.GET,
  },
  CREATE_ANALYSIS_REPORT: {
    key: "CREATE_ANALYSIS_REPORT",
    apiPath: `/v2/incident/generateReport`,
    method: API_METHODS.POST,
  },
  GET_INTERACTION_INSIGHTS: {
    key: "GET_INTERACTION_INSIGHTS",
    apiPath: `/api/v1/interaction/insights`,
    method: API_METHODS.POST,
  },
  GET_USER_DETAIL: {
    key: "GET_USER_DETAIL",
    apiPath: `/user`,
    method: API_METHODS.GET,
  },
  GET_USER_EXPERIMENTS: {
    key: "GET_USER_EXPERIMENTS",
    apiPath: `/user/{phoneNo}/experiments`,
    method: API_METHODS.GET,
  },
  GET_USER_LAST_ACTIVE_TODAY: {
    key: "GET_USER_LAST_ACTIVE_TODAY",
    apiPath: `/user/{phoneNo}/active-today`,
    method: API_METHODS.GET,
  },
  GET_REQUEST_ID_BY_TIME: {
    key: "GET_REQUEST_ID_BY_TIME",
    apiPath: `/v2/events/queryRequestId`,
    method: API_METHODS.GET,
  },
  GET_QUERY_RESULT_FROM_ID: {
    key: "GET_QUERY_RESULT_FROM_ID",
    apiPath: `/v2/events/eventsByRequestId`,
    method: API_METHODS.GET,
  },
  CREATE_USER_AI_SESSION: {
    key: "CREATE_USER_AI_SESSION",
    apiPath: `/pulse-ai/session`,
    method: API_METHODS.POST,
  },
  GET_USER_QUERY_PULSE_AI_INSIGHTS: {
    key: "GET_USER_QUERY_PULSE_AI_INSIGHTS",
    apiPath: `/pulse-ai/user-query`,
    method: API_METHODS.POST,
  },
  GET_ANOMALY_DETAILS: {
    key: "GET_ANOMALY_DETAILS",
    apiPath: `/anomaly/details`,
    method: API_METHODS.GET,
  },
  GET_ANOMALY_FOR_APDEX: {
    key: "GET_ANOMALY_FOR_APDEX",
    apiPath: `/anomaly/apdex`,
    method: API_METHODS.GET,
  },
  GET_ANOMALY_FOR_ERROR_RATE: {
    key: "GET_ANOMALY_FOR_ERROR_RATE",
    apiPath: `/anomaly/error-rate`,
    method: API_METHODS.GET,
  },
  GET_DASHBOARD_FILTERS: {
    key: "GET_DASHBOARD_FILTERS",
    apiPath: `/v1/interactions/telemetry-filters`,
    method: API_METHODS.GET,
  },
  GET_INTERACTIONLIST_FILTERS: {
    key: "GET_INTERACTIONLIST_FILTERS",
    apiPath: `/v1/interactions/filter-options`,
    method: API_METHODS.GET,
  },
  // Alert API Routes
  GET_ALERTS: {
    key: "GET_ALERTS",
    apiPath: `/v1/alert`,
    method: API_METHODS.GET,
  },
  GET_ALERT_DETAILS: {
    key: "GET_ALERT_DETAILS",
    apiPath: `/v1/alert`,
    method: API_METHODS.GET,
  },
  CREATE_ALERT: {
    key: "CREATE_ALERT",
    apiPath: `/v1/alert`,
    method: API_METHODS.POST,
  },
  UPDATE_ALERT: {
    key: "UPDATE_ALERT",
    apiPath: `/v1/alert`,
    method: API_METHODS.PUT,
  },
  DELETE_ALERT: {
    key: "DELETE_ALERT",
    apiPath: `/v1/alert`,
    method: API_METHODS.DELETE,
  },
  SNOOZE_ALERT: {
    key: "SNOOZE_ALERT",
    apiPath: `/v1/alert/{id}/snooze`,
    method: API_METHODS.POST,
  },
  RESUME_ALERT: {
    key: "RESUME_ALERT",
    apiPath: `/v1/alert/{id}/snooze`,
    method: API_METHODS.DELETE,
  },
  GET_ALERT_EVALUATION_HISTORY: {
    key: "GET_ALERT_EVALUATION_HISTORY",
    apiPath: `/v1/alert/{id}/evaluationHistory`,
    method: API_METHODS.GET,
  },
  GET_ALERT_FILTERS: {
    key: "GET_ALERT_FILTERS",
    apiPath: `/v1/alert/filters`,
    method: API_METHODS.GET,
  },
  GET_ALERT_SCOPES: {
    key: "GET_ALERT_SCOPES",
    apiPath: `/v1/alert/scopes`,
    method: API_METHODS.GET,
  },
  GET_ALERT_METRICS: {
    key: "GET_ALERT_METRICS",
    apiPath: `/v1/alert/metrics`,
    method: API_METHODS.GET,
  },
  GET_ALERT_SEVERITIES: {
    key: "GET_ALERT_SEVERITIES",
    apiPath: `/v1/alert/severity`,
    method: API_METHODS.GET,
  },
  GET_ALERT_NOTIFICATION_CHANNELS: {
    key: "GET_ALERT_NOTIFICATION_CHANNELS",
    apiPath: `/v1/alert/notificationChannels`,
    method: API_METHODS.GET,
  },
};

export const TOOLTIP_LABLES: Record<string, string> = {
  STREAMVERSE_LINK: "Streamverse Link",
  HODOR_LINK: "Hodor Link",
  DATADOG_LINK: "Datadog Link",
  TRACKING_LINK: "Tracking Link",
  STOP_INTERACTION: "Stop interaction",
  START_INTERACTION: "Start interaction",
  INFO_ICON: "Click info icon for help",
  EDIT_FORM: "Edit interaction",
  CLOSE_NAVBAR: "Close Navbar",
  OPEN_NAVBAR: "Open Navbar",
  DELETE_INTERACTION: "Delete Interaction",
  REMOVE_BLACKLIST_EVENT_MESSAGE: "Remove from blacklist",
  REMOVE_FILTER_MESSAGE: "Remove prop filter",
  REFRESH_BUTTON: "Refresh data",
};

export const CRITICAL_INTERACTION_FORM_CONSTANTS: Record<string, string> = {
  INTERACTION_NAME: "Interaction Name",
  INTERACTION_DESCRIPTION_LABEL: "Description",
  INPUT_PLACEHOLDER: "Type here...",
  INTERACTION_DESCRIPTION_TOOLTIP_LABEL:
    "Enter the interaction desciption such that it is easy to understand",
  INTERACTION_DESCRIPTION:
    "Enter your user interaction name. This will uniquely identify your interaction. Once interaction is created, it cannot be changed.",
  INTERACTION_ERROR_MESSAGE: "Interaction name should be in PascalCase",
  INTERACTION_DESCRIPTION_ERROR_MESSAGE: "Desciption is required",
  INTERACTION_LOWER_THRESHOLD: "Lower Threshold (ms)",
  INTERACTION_LOWER_THRESHOLD_DESCRIPTION:
    "Best case scenario for your interaction to be completed",
  INTERACTION_UPPER_THRESHOLD: "Upper Threshold (ms)",
  INTERACTION_UPPER_THRESHOLD_DESCRIPTION:
    "Worst case scenario for your interaction to be completed",
  EVENTS_SEQUENCE_LABEL: "Event sequence to track",
  EVENTS_SEQUENCE_SUB_LABEL:
    "This event sequence will be tracked for a succesful user interaction",
  GLOBAL_EVENTS_SEQUENCE_SUB_LABEL:
    "User interaction will be ignored if events specified in this section are encountered between start and end events",
  BLACKLIST_EVENTS_SEQUENCE_LABEL: "Global event blacklisting",
  BLACKLIST_EVENTS_SEQUENCE_SUB_LABEL:
    "User interaction will be ignored if events specified in this section are encountered between start and end events",
  TEXT_INPUT_SIZE: "md",
  THRESHOLD_ERROR_MESSAGE:
    "Incorrect threshold value. Please specify time in ms: eg: 300",
  BUTTON_SIZE: "md",
  ADD_EVENT_BUTTON_TEXT: "Add Event",
  AUTOCOMPLETE_PLACEHOLDER: "Type to search",
  AUTOCOMPLETE_ERROR_PLACEHOLDER: "Error fetching data",
  AUTOCOMPLETE_FETCHING_PLACEHOLDER: "Fetching",
  ADD_PROPS_FILTER_BUTTON_TEXT: "+ Event property based filter",
  CREATE_BUTTON_TEXT: "Create Interaction",
  UPDATE_BUTTON_TEXT: "Update Interaction",
  DELETE_BUTTON_TEXT: "Delete Interaction",
  TO_EVENT_PLACE_HOLDER_TEXT: "Enter start event",
  T1_EVENT_PLACE_HOLDER_TEXT: "Enter end event",
  WHITELIST_EVENT_PLACEHOLDER_TEXT: "Enter event name to whitelist",
  CANCEL_BUTTON_TEXT: "Cancel",
  PROCEED_BUTTON_TEXT: "Proceed",
  NEXT_BUTTON_TEXT: "Next",
  SELECT_CATEGORIES_MESSAGE: "Recommended thresholds if your interaction...",
  SELECT_CATEGORIES_ERROR_MESSAGE:
    "You must select atleast one category to create an interaction.",
  FETCHING_USER_ACTION_CATEGORY_MESSAGE:
    "Fetching user interaction actions category",
  INTERACTION_NAME_ERROR_MESSAGE:
    "Interaction name is required and must be in PascalCase. Please correct it before proceeding.",
  DEFINE_EVENTS_ERROR_MESSAGE:
    "T0 and T1 events are required. Please add them to proceed.",
  GLOBAL_BLACKLIST_EVENT_ERROR_MESSAGE:
    "Global blacklist event name is required. Please add it before proceeding.",
  CREATE_MODAL_MESSAGE:
    "Are you sure you want to create this user interaction ?",
  UPDATE_MODAL_MESSAGE:
    "Are you sure you want to update this user interaction ?",
  CREATE_JOB_SUCCESS_NOTIFICATION_MESSAGE:
    "Please wait for 5 minutes for data to be reflected. Redirecting to user experiences list.",
  CREATE_INTERACTION_HEADING: "Add a new user interaction",
  UPDATE_INTERACTION_HEADING: "Update user interaction",
  NO_DESCRIPTION_MESSAGE: "No description found",
  UPPER_THRESHOLD_VALUE: "100",
  LOWER_THRESHOLD_VALUE: "16",
  MIDDLE_THRESHOLD_VALUE: "50",
  DEFAULT_INTERACTION_THRESHOLD: "20000",
};

export const USER_ACTION_CATEGORIES_SELECTION: Record<string, boolean> = {
  IS_APP_LAUNCH_INTERACTION_ACTION: false,
  IS_TRIGGER_NETWORK_REQUEST_ACTION: false,
  IS_TRIGGER_ANIMATION_ACTION: false,
  IS_IN_PLACE_UPDATE_ACTION: true,
};

export const USER_ACTION_CATEGORIES: Record<string, string> = {
  APP_LAUNCH: "isAppLaunchInteractionAction",
  IN_PLACE_UPDATE: "isInPlaceUpdateAction",
  NETWORK: "isTriggerNetworkRequestAction",
  ANIMATION: "isTriggerAnimationAction",
};

export const USER_ACTION_CATEGORIES_KEY_VALUE: Record<string, string> = {
  isAppLaunchInteractionAction: "Has app launch interaction",
  isInPlaceUpdateAction: "Has in-place update action",
  isTriggerNetworkRequestAction: "Has trigger network request action",
  isTriggerAnimationAction: "Has trigger animation action",
};

export const RADIO_LABLES: Record<string, string> = {
  STATUS: "Status",
};

export const COOKIES_KEY: Record<string, string> = {
  ACCESS_TOKEN: "accessToken",
  REFRESH_TOKEN: "refreshToken",
  USER_NAME: "userName",
  USER_PICTURE: "userPicture",
  USER_EMAIL: "userEmail",
  ID_TOKEN: "idToken",
  TOKEN_TYPE: "tokenType",
  EXPIRES_IN: "expiresIn",
};

export const LAYOUT_PAGE_CONSTANTS: Record<string, string> = {
  CHECKING_CREDENTIALS: "Checking credentials",
};

export const COMMON_CONSTANTS: Record<string, string> = {
  APP_NAME: "Pulse",
  COMING_SOON: "Coming Soon",
  REQUEST_TIMEOUT_MESSAGE: "Request Timeout",
  PAGE_NOT_FOUND_MESSAGE: "Oops! Page not found :(",
  DEFAULT_ERROR_MESSAGE: "Something went wrong :(",
  INFO_NOTIFICATION_TITLE: "Info",
  ERROR_NOTIFICATION_TITLE: "Error",
  SUCCESS_NOTIFICATION_TITLE: "Success!",
  DEFAULT_BADGE_TEXT: "Unknown",
  USER_EMAIL_NOT_FOUND: "User email not found",
  USER_PHONE_NUMBER_NOT_VALID: "User phone number not valid",
};

export const CRITICAL_INTERACTION_DETAILS_PAGE_CONSTANTS: Record<
  string,
  string
> = {
  GRAPHANA_DASHBOARD_TITLE: "Grafana Dashboard",
  CRITICAL_INTERACTION_DETAILS_HEADER: "Critical interaction details",
  ALL_INTERACTIONS_TITLE: "User Experience Overview",
  ALL_INTERACTION_IFRAME_HEIGHT: "500",
  ALL_INTERACTION_IFRAME_WIDTH: "80%",
  ALL_INTERACTION_CATEGORY_IFRAME_WIDTH: "200",
  APPLY_FILTERS_BUTTON_TEXT: "Apply Filters",
  TIME_RANGE_LABEL_TEXT: "Time Range",
  IFRAME_LOAD_ERROR_MESSAGE: "An error occured while loading dashboard.",
  ALL_INTERACTION_IFRAME_URL:
    "https://hodor.dream11.com/d-solo/ee2c5914-7a74-4323-b202-a3cb9507fc50/tv-dashboard?orgId=1&refresh=1m&panelId=4&from=now-30m&to=now",
  ALL_INTERACTION_CATEGORY_IFRAME_URL:
    "https://hodor.dream11.com/d-solo/ee2c5914-7a74-4323-b202-a3cb9507fc50/tv-dashboard?orgId=1&refresh=1m&panelId=2",
};

export const ANALYTICS_REPORT_CONSTANTS: Record<string, string> = {
  ANALYTICS_REPORT_TITLE: "Analytics Report",
};

export const CRITICAL_INTERACTION_LISTING_PAGE_CONSTANTS: Record<
  string,
  string
> = {
  CREATE_USER_EXPERIENCE_BUTTON_TEXT: "Add new interaction",
  NO_INTERACTIONS_MESSAGE: "No interactions to show",
  USER_EXPERIENCE_LIST_SEARCH_BAR_PLACEHOLDER_TEXT:
    "Search your interaction here",
  STOP_NOTIFICATION_MESSAGE:
    "Please stop this job first to delete this interaction",
  EDIT_NOTIFICATION_MESSAGE:
    "Please stop this job first to edit this interaction",
  APPLY_BUTTON_TEXT: "Apply",
  FILTER_RESOURCE_SEARCH_BAR_LABEL_TEXT: "Resource name",
  FILTER_SEARCH_BAR_LABEL_TEXT: "User email",
  FILTER_SEARCH_BAR_PLACEHOLDER_TEXT: "Start typing to get suggestions",
  LOADING_TEXT: "Loading",
  RESET_BUTTON_TEXT: "Reset",
  SWITCH_TEXT: "My interactions",
};

export const USERS_SUPPORT_PAGE_CONSTANTS: Record<string, string> = {
  USER_SEARCH_BAR_PLACEHOLDER_TEXT: "Enter phone number here",
  USER_SERACH_BUTTON: "Search",
  USER_SEARCH_DEFAULT_MESSAGE:
    "Please enter a user's phone number to analyse the data...",
  NO_EXPERIMENTS_FOUND_MESSAGE: "No experiments found",
};

export const ALERT_EVALUATION_HISTORY_CONSTANTS: Record<string, string> = {
  NO_EVALUATION_HISTORY_MESSAGE: "No evaluation history found",
};

export const FOOTER_CONSTANTS: Record<string, string> = {
  FOOTER_MESSAGE: "Facing problems? Ping us on #pulse-feedback",
};

export const NAVBAR_CONSTANTS: Record<string, string> = {
  HELP_BAR_TEXT: "About Pulse",
  HELP_LINK:
    "https://dream11.atlassian.net/wiki/spaces/FE/pages/3583705154/Pulse+Demo",
};

export const HEADER_CONSTANTS: Record<string, string> = {
  LOGOUT_TEXT: "Logout",
};

export const LOGIN_PAGE_CONSTANTS: Record<string, string> = {
  SIGNING_IN_MESSAGE: "Authenticating your credentials",
};

export const CRITICAL_INTERACTION_FORM_STEPS: CriticalInteractionFormSteps = [
  {
    label: "Name",
    description: "to easily recall",
  },
  {
    label: "Select events",
    description: "to set the interaction",
  },
  {
    label: "Global exclusion",
    description: "to add events to global blacklist",
  },
  {
    label: "Interaction breakdown",
    description: "for categorisation",
  },
];

export const EVENTS_THRESHOLDS_PAGE_CONSTANTS: Record<string, string> = {
  IN_PLACE_DESELECTION_ERROR_MESSAGE:
    "In-place update action is mandatory for interaction",
};

export const DEFAULT_EVENT_WHITELISTING_DATA: Array<EventSequenceData> = [
  {
    draggableId: uuidV4(),
    name: "",
    props: [],
    isBlacklisted: false,
  },
  {
    draggableId: uuidV4(),
    name: "",
    props: [],
    isBlacklisted: false,
  },
];

export const DEFAULT_EVENT_BLACKLISTING_DATA: Array<EventFilters> = [];

export const DEFAULT_CRITICAL_INTERACTION_FORM_STEPS_RECORD: CriticalInteractionFormStepsRecords =
  {
    0: {
      errorMessage:
        CRITICAL_INTERACTION_FORM_CONSTANTS.INTERACTION_NAME_ERROR_MESSAGE,
      isCompleted: false,
    },
    1: {
      errorMessage:
        CRITICAL_INTERACTION_FORM_CONSTANTS.DEFINE_EVENTS_ERROR_MESSAGE,
      isCompleted: false,
    },
    2: {
      errorMessage:
        CRITICAL_INTERACTION_FORM_CONSTANTS.GLOBAL_BLACKLIST_EVENT_ERROR_MESSAGE,
      isCompleted: true,
    },
    3: {
      errorMessage: CRITICAL_INTERACTION_FORM_CONSTANTS.THRESHOLD_ERROR_MESSAGE,
      isCompleted: false,
    },
  };

export const ALERT_FORM_CONSTANTS = {
  CREATE_ALERT_HEADING: "Add a new alert",
  UPDATE_ALERT_HEADING: "Update alert",
  DELETE_ALERT_BUTTON: "Delete Alert",
  ALERT_NAME_LABEL: "Alert Name",
  ALERT_NAME_LABEL_INFO: "Min 4 characters are required",
  ALERT_NAME_PLACEHOLDER: "Enter your alert name here...",
  ALERT_NAME_FIELD_KEY: "name",
  ALERT_DESCRIPTION_LABEL: "Description",
  ALERT_DESCRIPTION_PLACEHOLDER: "Enter your alert description here...",
  ALERT_DESCRIPTION_FIELD_KEY: "description",
  ALERT_DESCRIPTION_LABEL_INFO: "Min 10 characters are required",
  ALERT_SEVERITY_LABEL: "Severity",
  ALERT_SEVERITY_LABEL_INFO:
    "Severity 1-3 will create SPM alert and 4-5 will create an operator alert",
  ALERT_SCOPE_LABEL: "Alert Scope",
  ALERT_SCOPE_LABEL_INFO:
    "Select the scope for which this alert should be evaluated",
  ALERT_USER_INTERACTION_LABEL: "User Interaction",
  ALERT_USER_INTERACTION_LABEL_INFO:
    "User interaction list will be fetched once you start typing.",
  ALERT_THRESHOLD_LABEL: "Threshold value",
  ALERT_THRESHOLD_LABEL_INFO:
    "Threshold value against which the metric will be compared.",
  ALERT_MONITORING_PERIOD_LABEL: "Evaluation period in seconds",
  ALERT_MONITORING_PERIOD_LABEL_INFO:
    "Time window in seconds over which the metric is evaluated. It should be in the range of 30 to 3600 seconds.",
  ALERT_EVALUATION_INTERVAL_LABEL: "Evaluation interval in seconds",
  ALERT_EVALUATION_INTERVAL_LABEL_INFO:
    "How often the alert condition is checked. It should be in the range of 30 to 3600 seconds.",
  ALERT_CONDITIONS_LOGIC_LABEL: "Condition Logic",
  ALERT_CONDITIONS_LOGIC_LABEL_INFO:
    "If you choose AND, all conditions should be met to trigger the alert. If you choose OR, any one condition should be met to trigger the alert.",
  ALERT_CONDITIONS_LOGIC_PLACEHOLDER: "Select condition logic",
  ALERT_CONDITIONS_PARAMETER_LABEL: "Parameter",
  ALERT_CONDITIONS_PARAMETER_LABEL_INFO:
    "Parameter on which the condition should be evaluated",
  ALERT_CONDITIONS_PARAMETER_PLACEHOLDER: "Select parameter",
  ALERT_CONDITIONS_OPERATOR_LABEL: "Operator",
  ALERT_CONDITIONS_OPERATOR_LABEL_INFO: "Operator to compare the value",
  ALERT_CONDITIONS_OPERATOR_PLACEHOLDER: "Select operator",
  ALERT_CONDITIONS_VALUE_LABEL: "Value",
  ALERT_CONDITIONS_VALUE_LABEL_INFO: "Value to compare with the parameter",
  ALERT_CONDITIONS_VALUE_PLACEHOLDER: "Enter value",
  ALERT_CONDITIONS_ADD_BUTTON: "Add Condition",
  ALERT_CONDITIONS_REMOVE_BUTTON: "Remove Condition",
  ALERT_UPDATE_MODAL_TITLE: "Are you sure you want to update this alert?",
  ALERT_CREATE_MODAL_TITLE: "Are you sure you want to create this alert?",
  ALERT_DELETE_MODAL_TITLE: "Are you sure you want to delete this alert?",
  ALERT_SERVICE_NAME_LABEL: "Service name",
  ALERT_ROSTER_NAME_LABEL: "Roster name",
  ALERT_SERVICE_NAME_LABEL_INFO:
    "Service name to which the alert belongs for eg. 'contest-join'. ",
  ALERT_ROSTER_NAME_LABEL_INFO:
    "Roster name to which the alert belongs for eg. 'contest_backend_schedule'. ",
  ALERT_SERVICE_COMMON_TOOLTIP:
    "Please do make sure your team and roster is onboarded on Observability Team. Reach out to @observability-oncall for confirmation",
  ALERT_METRIC_LABEL: "Metric",
  ALERT_METRIC_LABEL_INFO: "Select the metric to monitor for this alert",
  ALERT_METRIC_OPERATOR_LABEL: "Operator",
  ALERT_METRIC_OPERATOR_LABEL_INFO: "Comparison operator for the threshold",
  ALERT_CONDITION_EXPRESSION_LABEL: "Condition Expression",
  ALERT_CONDITION_EXPRESSION_LABEL_INFO:
    "Expression combining alert conditions (e.g., 'A AND B', 'A OR B')",
  ALERT_DIMENSION_FILTERS_LABEL: "Dimension Filters",
  ALERT_DIMENSION_FILTERS_LABEL_INFO:
    "JSON filter conditions for dimensions (optional)",
};

export const ALERT_CONDITIONS_PARAMETER_OPTIONS: Array<ComboboxItem> = [
  {
    label: "App Version",
    value: "app_version",
    disabled: false,
  },
  {
    label: "Os",
    value: "os",
    disabled: false,
  },
  {
    label: "Device",
    value: "device",
    disabled: false,
  },
  {
    label: "State",
    value: "state",
    disabled: false,
  },
  {
    label: "Os Version",
    value: "os_version",
    disabled: false,
  },
  {
    label: "Mobile",
    value: "mobile",
    disabled: false,
  },
  {
    label: "Model",
    value: "model",
    disabled: false,
  },
  {
    label: "Network Provider",
    value: "network_provider",
    disabled: false,
  },
];

export const ALERT_SEVERITY_OPTIONS: Array<ComboboxItem> = [
  {
    label: "3",
    value: "3",
    disabled: false,
  },
  {
    label: "4",
    value: "4",
    disabled: false,
  },
  {
    label: "5",
    value: "5",
    disabled: false,
  },
];

export const ALERT_CONDITION_OPERATOR_OPTIONS: Array<ComboboxItem> = [
  { value: OperatorType.EQUAL_TO.toString(), label: "Equal to" },
  { value: OperatorType.GREATER_THAN.toString(), label: "Greater than" },
  {
    value: OperatorType.GREATER_THAN_OR_EQUAL_TO.toString(),
    label: "Greater than or equal to",
  },
  { value: OperatorType.LESS_THAN.toString(), label: "Less than" },
  {
    value: OperatorType.LESS_THAN_OR_EQUAL_TO.toString(),
    label: "Less than or equal to",
  },
  { value: OperatorType.NOT_EQUAL_TO.toString(), label: "Not equal to" },
];

export const ALERT_FORM_STEPS: Array<FormSteps> = [
  {
    label: "Add Name",
    description: "to easily recall",
  },
  {
    label: "Choose scope and metric",
    description: "select what to monitor",
  },
  {
    label: "Define thresholds",
    description: "set alert conditions",
  },
  {
    label: "Configure notification",
    description: "choose severity and channel",
  },
];

export const JOB_VERSIONS: Record<string, string> = {
  V0: "V0",
  V1: "V1",
};

export const CRITICAL_INTERACTION_DETAILS_FILTER_OPTIONS: CriticalInteractionDetailsFilterOptionsResponse =
  {
    PLATFORM: [],
    APP_VERSION: [],
    NETWORK_PROVIDER: [],
    STATE: [],
    OS_VERSION: [],
  };

export const CRITICAL_INTERACTION_QUICK_TIME_FILTERS = {
  LAST_5_MINUTES: "LAST_5_MINUTES",
  LAST_15_MINUTES: "LAST_15_MINUTES",
  LAST_30_MINUTES: "LAST_30_MINUTES",
  LAST_1_HOUR: "LAST_1_HOUR",
  LAST_3_HOURS: "LAST_3_HOURS",
  LAST_6_HOURS: "LAST_6_HOURS",
  LAST_12_HOURS: "LAST_12_HOURS",
  LAST_24_HOURS: "LAST_24_HOURS",
  LAST_2_DAYS: "LAST_2_DAYS",
  LAST_7_DAYS: "LAST_7_DAYS",
  LAST_30_DAYS: "LAST_30_DAYS",
  LAST_90_DAYS: "LAST_90_DAYS",
  YESTERDAY: "YESTERDAY",
  PREVIOUS_WEEK: "PREVIOUS_WEEK",
  PREVIOUS_MONTH: "PREVIOUS_MONTH",
  TODAY_SO_FAR: "TODAY_SO_FAR",
  THIS_WEEK: "THIS_WEEK",
  THIS_MONTH_SO_FAR: "THIS_MONTH_SO_FAR",
};

// Default time filter for the dashboard (Last 24 hours)
export const DEFAULT_QUICK_TIME_FILTER = CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_24_HOURS;
export const DEFAULT_QUICK_TIME_FILTER_INDEX = 7; // Index of LAST_24_HOURS in CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS

export const SNOOZE_ALERT_QUICK_TIME_FILTERS = {
  NEXT_1_HOUR: "NEXT_1_HOUR",
  NEXT_3_HOURS: "NEXT_3_HOURS",
  NEXT_6_HOURS: "NEXT_6_HOURS",
  NEXT_12_HOURS: "NEXT_12_HOURS",
  NEXT_24_HOURS: "NEXT_24_HOURS",
  NEXT_2_DAYS: "NEXT_2_DAYS",
  NEXT_7_DAYS: "NEXT_7_DAYS",
  NEXT_30_DAYS: "NEXT_30_DAYS",
  NEXT_90_DAYS: "NEXT_90_DAYS",
};

export const USER_EVENTS_TIME_FILTERS_OPTIONS: Array<TimeFilter> = [
  {
    label: "Last 5 minutes",
    value: "LAST_5_MINUTES",
  },
  {
    label: "Last 15 minutes",
    value: "LAST_15_MINUTES",
  },
  {
    label: "Last 30 minutes",
    value: "LAST_30_MINUTES",
  },
  {
    label: "Last 1 hour",
    value: "LAST_1_HOUR",
  },
  {
    label: "Last 3 hours",
    value: "LAST_3_HOURS",
  },
  {
    label: "Last 6 hours",
    value: "LAST_6_HOURS",
  },
  {
    label: "Last 12 hours",
    value: "LAST_12_HOURS",
  },
  {
    label: "Last 24 hours",
    value: "LAST_24_HOURS",
  },
];

export const SNOOZE_ALERT_TIME_FILTERS_OPTIONS: Array<TimeFilter> = [
  {
    label: "Next 1 hour",
    value: "NEXT_1_HOUR",
  },
  {
    label: "Next 3 hours",
    value: "NEXT_3_HOURS",
  },
  {
    label: "Next 6 hours",
    value: "NEXT_6_HOURS",
  },
  {
    label: "Next 12 hours",
    value: "NEXT_12_HOURS",
  },
  {
    label: "Next 24 hours",
    value: "NEXT_24_HOURS",
  },
  {
    label: "Next 2 days",
    value: "NEXT_2_DAYS",
  },
  {
    label: "Next 7 days",
    value: "NEXT_7_DAYS",
  },
  {
    label: "Next 30 days",
    value: "NEXT_30_DAYS",
  },
  {
    label: "Next 90 days",
    value: "NEXT_90_DAYS",
  },
];

export const CRITICAL_INTERACTION_DETAILS_TIME_FILTERS_OPTIONS: Array<TimeFilter> =
  [
    {
      label: "Last 5 minutes",
      value: "LAST_5_MINUTES",
    },
    {
      label: "Last 15 minutes",
      value: "LAST_15_MINUTES",
    },
    {
      label: "Last 30 minutes",
      value: "LAST_30_MINUTES",
    },
    {
      label: "Last 1 hour",
      value: "LAST_1_HOUR",
    },
    {
      label: "Last 3 hours",
      value: "LAST_3_HOURS",
    },
    {
      label: "Last 6 hours",
      value: "LAST_6_HOURS",
    },
    {
      label: "Last 12 hours",
      value: "LAST_12_HOURS",
    },
    {
      label: "Last 24 hours",
      value: "LAST_24_HOURS",
    },
    {
      label: "Last 2 days",
      value: "LAST_2_DAYS",
    },
    {
      label: "Last 7 days",
      value: "LAST_7_DAYS",
    },
    {
      label: "Last 30 days",
      value: "LAST_30_DAYS",
    },
    {
      label: "Last 90 days",
      value: "LAST_90_DAYS",
    },
    {
      label: "Yesterday",
      value: "YESTERDAY",
    },
    {
      label: "Previous week",
      value: "PREVIOUS_WEEK",
    },
    {
      label: "Previous month",
      value: "PREVIOUS_MONTH",
    },
    {
      label: "Today so far",
      value: "TODAY_SO_FAR",
    },
    {
      label: "This week",
      value: "THIS_WEEK",
    },
    {
      label: "This month so far",
      value: "THIS_MONTH_SO_FAR",
    },
  ];

export const CRITICAL_INTERACTION_DETAILS_FILTER_VALUES: CriticalInteractionDetailsFilterValues =
  {
    PLATFORM: "",
    APP_VERSION: "",
    NETWORK_PROVIDER: "",
    STATE: "",
    OS_VERSION: "",
  };

export const CRITICAL_INTERACTION_DETAILS_FILTER_LABELS: CriticalInteractionDetailsFilterValues =
  {
    PLATFORM: "Platform",
    APP_VERSION: "App Version",
    NETWORK_PROVIDER: "Network Provider",
    STATE: "State",
    OS_VERSION: "OS Version",
  };

export const CRITICAL_INTERACTION_DETAILS_FILTER_KEYS: CriticalInteractionDetailsFilterValues =
  {
    PLATFORM: "PLATFORM",
    APP_VERSION: "APP_VERSION",
    NETWORK_PROVIDER: "NETWORK_PROVIDER",
    STATE: "STATE",
    OS_VERSION: "OS_VERSION",
  };

export const DATE_FORMAT = "MMM D, YY HH:mm";

export const STATUS_CODE_ERROR = "Error";
