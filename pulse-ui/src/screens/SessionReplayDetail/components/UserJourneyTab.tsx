import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { UserJourney } from "./all/UserJourney";
import { SessionDetailTabPanel } from "./SessionDetailTabPanel";
import { TAB_PANEL_DESCRIPTION, TAB_PANEL_TITLE } from "../constants/strings";

interface UserJourneyTabProps {
  sessionData: SessionDetailData;
}

export function UserJourneyTab({ sessionData }: UserJourneyTabProps) {
  return (
    <SessionDetailTabPanel
      title={TAB_PANEL_TITLE.USER_JOURNEY}
      description={TAB_PANEL_DESCRIPTION.USER_JOURNEY}
    >
      <UserJourney journey={sessionData.journey} showSectionTitle={false} />
    </SessionDetailTabPanel>
  );
}
