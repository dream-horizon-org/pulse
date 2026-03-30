import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { TabPanelScrollArea } from "./TabPanelScrollArea";
import { UserJourney } from "./all/UserJourney";

interface UserJourneyTabProps {
  sessionData: SessionDetailData;
}

export function UserJourneyTab({ sessionData }: UserJourneyTabProps) {
  return (
    <TabPanelScrollArea>
      <UserJourney journey={sessionData.journey} showSectionTitle={false} />
    </TabPanelScrollArea>
  );
}
