import {ROUTES as ROUTE_PATHS} from "../constants";
import {AiChat} from "../screens/AiChat";
import {AlertDetail} from "../screens/AlertDetail";
import {AlertForm} from "../screens/AlertFormWizard";
import {AlertListingPage} from "../screens/AlertListingPage";
import {AppVitals, IssueDetail, OccurrenceDetail} from "../screens/AppVitals";
import {ComingSoon} from "../screens/ComingSoon";
import {CreateProject} from "../screens/CreateProject";
import {CiritcalInteractionDetails} from "../screens/CriticalInteractionDetails";
import {CriticalInteractionForm} from "../screens/CriticalInteractionForm";
import {CriticalInteractionList} from "../screens/CriticalInteractionList";
import {EventCatalog} from "../screens/EventCatalog";
import {Home} from "../screens/Home";
import {Login} from "../screens/Login";
import {NetworkDetail} from "../screens/NetworkDetail";
import {NetworkList} from "../screens/NetworkList";
import {Onboarding} from "../screens/Onboarding";
import {OnboardingSuccess} from "../screens/OnboardingSuccess";
import {OrganizationDashboard} from "../screens/OrganizationDashboard";
import {OrganizationMembers} from "../screens/OrganizationMembers";
import {OrganizationProjects} from "../screens/OrganizationProjects";
import {OrganizationSettings} from "../screens/OrganizationSettings";
import {Pricing} from "../screens/Pricing";
import {ProjectSettings} from "../screens/ProjectSettings";
import {RealTimeQuery} from "../screens/RealTimeQuery";
import {SamplingConfig} from "../screens/SamplingConfig";
import {ScreenDetail} from "../screens/ScreenDetail";
import {ScreenList} from "../screens/ScreenList";
import {SessionReplay} from "../screens/SessionReplay";

import {SessionReplayInsights} from "../screens/SessionReplayInsights";
import {SessionReplaySessions} from "../screens/SessionReplaySessions";
import {SessionTimeline} from "../screens/SessionTimeline";
import {Settings} from "../screens/Settings";
import {SupportQueries} from "../screens/SupportQueries";
import {UniversalEventQuery} from "../screens/UniversalEventQuery/UniversalEventQuery";
import {UserEngagement} from "../screens/UserEngagement";
import {SessionReplayDetail} from "../screens/SessionReplayDetail";
import {CreateFunnel, CreateJourney} from "../screens/FunnelAnalysis";
import {FunnelsJourneysList} from "../screens/FunnelsJourneysList";
import {FunnelJourneyDetail} from "../screens/FunnelJourneyDetail";

export const ROUTES = {
  // Organization-level routes
  ORGANIZATION_DASHBOARD: {
    ...ROUTE_PATHS.ORGANIZATION_DASHBOARD,
    element: OrganizationDashboard,
  },
  ORGANIZATION_SETTINGS: {
    ...ROUTE_PATHS.ORGANIZATION_SETTINGS,
    element: OrganizationSettings,
  },
  ORGANIZATION_MEMBERS: {
    ...ROUTE_PATHS.ORGANIZATION_MEMBERS,
    element: OrganizationMembers,
  },
  ORGANIZATION_PROJECTS: {
    ...ROUTE_PATHS.ORGANIZATION_PROJECTS,
    element: OrganizationProjects,
  },
  CREATE_PROJECT: {
    ...ROUTE_PATHS.CREATE_PROJECT,
    element: CreateProject,
  },

  // Project-scoped routes
  PROJECT_DASHBOARD: {
    ...ROUTE_PATHS.PROJECT_DASHBOARD,
    element: Home,
  },
  PROJECT_ONBOARDING_SUCCESS: {
    ...ROUTE_PATHS.PROJECT_ONBOARDING_SUCCESS,
    element: OnboardingSuccess,
  },
  PROJECT_USER_ENGAGEMENT: {
    ...ROUTE_PATHS.PROJECT_USER_ENGAGEMENT,
    element: UserEngagement,
  },
  PROJECT_INTERACTIONS: {
    ...ROUTE_PATHS.PROJECT_INTERACTIONS,
    element: CriticalInteractionList,
  },
  PROJECT_INTERACTION_FORM: {
    ...ROUTE_PATHS.PROJECT_INTERACTION_FORM,
    element: CriticalInteractionForm,
  },
  PROJECT_ALL_INTERACTION_DETAILS: {
    ...ROUTE_PATHS.PROJECT_ALL_INTERACTION_DETAILS,
    element: CiritcalInteractionDetails,
  },
  PROJECT_INTERACTION_DETAILS: {
    ...ROUTE_PATHS.PROJECT_INTERACTION_DETAILS,
    element: CiritcalInteractionDetails,
  },
  PROJECT_UNIVERSAL_QUERYING: {
    ...ROUTE_PATHS.PROJECT_UNIVERSAL_QUERYING,
    element: UniversalEventQuery,
  },
  PROJECT_APP_VITALS: {
    ...ROUTE_PATHS.PROJECT_APP_VITALS,
    element: AppVitals,
  },
  PROJECT_APP_VITALS_ISSUE_DETAIL: {
    ...ROUTE_PATHS.PROJECT_APP_VITALS_ISSUE_DETAIL,
    element: IssueDetail,
  },
  PROJECT_APP_VITALS_OCCURRENCE_DETAIL: {
    ...ROUTE_PATHS.PROJECT_APP_VITALS_OCCURRENCE_DETAIL,
    element: OccurrenceDetail,
  },
  PROJECT_SESSION_TIMELINE: {
    ...ROUTE_PATHS.PROJECT_SESSION_TIMELINE,
    element: SessionTimeline,
  },
  PROJECT_SCREENS: {
    ...ROUTE_PATHS.PROJECT_SCREENS,
    element: ScreenList,
  },
  PROJECT_SCREEN_DETAILS: {
    ...ROUTE_PATHS.PROJECT_SCREEN_DETAILS,
    element: ScreenDetail,
  },
  PROJECT_NETWORK_LIST: {
    ...ROUTE_PATHS.PROJECT_NETWORK_LIST,
    element: NetworkList,
  },
  PROJECT_NETWORK_DETAIL: {
    ...ROUTE_PATHS.PROJECT_NETWORK_DETAIL,
    element: NetworkDetail,
  },
  PROJECT_SDK_CONFIG: {
    ...ROUTE_PATHS.PROJECT_SDK_CONFIG,
    element: SamplingConfig,
  },
  PROJECT_SETTINGS_ROUTE: {
    ...ROUTE_PATHS.PROJECT_SETTINGS_ROUTE,
    element: Settings,
  },
  PROJECT_ALERTS: {
    ...ROUTE_PATHS.PROJECT_ALERTS,
    element: AlertListingPage,
  },
  PROJECT_ALERT_DETAIL: {
    ...ROUTE_PATHS.PROJECT_ALERT_DETAIL,
    element: AlertDetail,
  },
  PROJECT_ALERTS_FORM: {
    ...ROUTE_PATHS.PROJECT_ALERTS_FORM,
    element: AlertForm,
  },
  PROJECT_QUERY_BUILDER: {
    ...ROUTE_PATHS.PROJECT_QUERY_BUILDER,
    element: RealTimeQuery,
  },

  // Standalone routes
  LOGIN: {
    ...ROUTE_PATHS.LOGIN,
    element: Login,
  },
  ONBOARDING: {
    ...ROUTE_PATHS.ONBOARDING,
    element: Onboarding,
  },
  PRICING: {
    ...ROUTE_PATHS.PRICING,
    element: Pricing,
  },
  COMING_SOON: {
    ...ROUTE_PATHS.COMING_SOON,
    element: ComingSoon,
  },
  PROJECT_SETTINGS: {
    ...ROUTE_PATHS.PROJECT_SETTINGS,
    element: ProjectSettings,
  },
  PROJECT_EVENT_CATALOG: {
    ...ROUTE_PATHS.PROJECT_EVENT_CATALOG,
    element: EventCatalog,
  },

  PROJECT_SESSION_REPLAY_SESSIONS: {
    ...ROUTE_PATHS.PROJECT_SESSION_REPLAY_SESSIONS,
    element: SessionReplaySessions,
  },
  PROJECT_SESSION_REPLAY_DETAIL: {
    ...ROUTE_PATHS.PROJECT_SESSION_REPLAY_DETAIL,
    element: SessionReplayDetail,
  },
  PROJECT_SESSION_REPLAY: {
    ...ROUTE_PATHS.PROJECT_SESSION_REPLAY,
    element: SessionReplay,
  },

  SUPPORT_QUERIES: {
    ...ROUTE_PATHS.SUPPORT_QUERIES,
    element: SupportQueries,
  },
  SESSION_REPLAY: {
    ...ROUTE_PATHS.SESSION_REPLAY,
    element: SessionReplay,
  },
  SESSION_REPLAY_INSIGHTS: {
    ...ROUTE_PATHS.SESSION_REPLAY_INSIGHTS,
    element: SessionReplayInsights,
  },
  SESSION_REPLAY_SESSIONS: {
    ...ROUTE_PATHS.SESSION_REPLAY_SESSIONS,
    element: SessionReplaySessions,
  },
  SESSION_REPLAY_DETAIL: {
    ...ROUTE_PATHS.SESSION_REPLAY_DETAIL,
    element: SessionReplayDetail,
  },

  // AI Chat (only when REACT_APP_ENABLE_AI_CHAT=true)
  ...(ROUTE_PATHS.AI_CHAT
    ? { AI_CHAT: { ...ROUTE_PATHS.AI_CHAT, element: AiChat } }
    : {}),
  FUNNEL_ANALYSIS: {
    ...ROUTE_PATHS.FUNNEL_ANALYSIS,
    element: FunnelsJourneysList,
  },
  FUNNEL_ANALYSIS_CREATE_FUNNEL: {
    ...ROUTE_PATHS.FUNNEL_ANALYSIS_CREATE_FUNNEL,
    element: CreateFunnel,
  },
  FUNNEL_ANALYSIS_CREATE_JOURNEY: {
    ...ROUTE_PATHS.FUNNEL_ANALYSIS_CREATE_JOURNEY,
    element: CreateJourney,
  },
  FUNNEL_JOURNEY_DETAIL: {
    ...ROUTE_PATHS.FUNNEL_JOURNEY_DETAIL,
    element: FunnelJourneyDetail,
  },
};
