import {
  IconListDetails,
  IconActivityHeartbeat,
  IconDeviceDesktop,
  IconNetwork,
} from "@tabler/icons-react";
import { useNavigate } from "react-router-dom";
import { ROUTES } from "../../constants";
import { HomeProps } from "./Home.interface";
import classes from "./Home.module.css";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { UserExperienceGraphs } from "./components/UserExperienceGraphs";
import { UserEngagementGraph } from "./components/UserEngagementGraph";
import { ActiveSessionsGraph } from "./components/ActiveSessionsGraph";
import { QuickAccessLinks, QuickLink } from "./components/QuickAccessLinks";
import { ScreensHealth } from "./components/ScreensHealth";
import { TopInteractionsHealth } from "./components/TopInteractionsHealth";
import { useAnalytics } from "../../hooks/useAnalytics";

dayjs.extend(utc);

export function Home(_props: HomeProps) {
  const navigate = useNavigate();
  const { trackClick } = useAnalytics("Home");

  const quickLinks: QuickLink[] = [
    {
      title: "Interactions",
      description: "View and manage all configured user interactions",
      route: ROUTES.CRITICAL_INTERACTIONS.basePath,
      icon: IconListDetails,
    },
    {
      title: "App Vitals",
      description: "View and manage all configured app vitals",
      route: ROUTES.APP_VITALS.basePath,
      icon: IconActivityHeartbeat,
    },
    {
      title: "Screens",
      description: "View and manage all configured screens",
      route: ROUTES.SCREENS.basePath,
      icon: IconDeviceDesktop,
    },
    {
      title: "Network APIs",
      description: "View and manage all configured network APIs",
      route: ROUTES.NETWORK_LIST.basePath,
      icon: IconNetwork,
    }
  ];

  const handleQuickLinkClick = (route: string) => {
    const linkTitle = quickLinks.find(l => l.route === route)?.title || route;
    trackClick(`QuickLink: ${linkTitle}`);
    navigate(route);
  };

  const handleViewAllInteractions = () => {
    trackClick("ViewAllInteractions");
    navigate(ROUTES.CRITICAL_INTERACTIONS.basePath);
  };

  const handleInteractionCardClick = (interaction: {
    id: number;
    name: string;
  }) => {
    trackClick(`InteractionCard: ${interaction.name}`);
    navigate(
      `${ROUTES.CRITICAL_INTERACTION_DETAILS.basePath}/${interaction.name}`,
    );
  };

  const handleViewAllScreens = () => {
    trackClick("ViewAllScreens");
    navigate(ROUTES.SCREENS.basePath);
  };

  const handleScreenCardClick = (screenName: string) => {
    trackClick(`ScreenCard: ${screenName}`);
    navigate(
      `${ROUTES.SCREEN_DETAILS.basePath}/${encodeURIComponent(screenName)}`,
    );
  };

  return (
    <div className={classes.homeContainer}>
      {/* Section 1: Overall User Experience - Three Graphs */}
      <div className={classes.section}>
        <h2 className={classes.sectionTitle}>Overall User Experience</h2>
        <UserExperienceGraphs />
      </div>

      {/* Section 2: User Engagement, Active Sessions & Alerts */}
      <div className={classes.section}>
        <h2 className={classes.sectionTitle}>
          User Engagement & Active Sessions
        </h2>
        <div className={classes.sessionsGraphsRow}>
          <UserEngagementGraph />
          <ActiveSessionsGraph />
        </div>
      </div>

      {/* Screens Health Section */}
      <div className={classes.section}>
        <ScreensHealth
          onViewAll={handleViewAllScreens}
          onCardClick={handleScreenCardClick}
        />
      </div>

      {/* Top Interactions Health Section */}
      <div className={classes.section}>
        <TopInteractionsHealth
          onViewAll={handleViewAllInteractions}
          onCardClick={handleInteractionCardClick}
        />
      </div>

      {/* Quick Links Section */}
      <div className={classes.section}>
        <h2 className={classes.sectionTitle}>Quick Access</h2>
        <QuickAccessLinks
          links={quickLinks}
          onLinkClick={handleQuickLinkClick}
        />
      </div>
    </div>
  );
}
