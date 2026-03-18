import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { CriticalInteractions } from "./all/CriticalInteractions";
import { TabPanelScrollArea } from "./TabPanelScrollArea";

interface InteractionTabProps {
  sessionData: SessionDetailData;
  onCriticalInteractionClick?: (t0: number, t1: number) => void;
}

export function InteractionTab({
  sessionData,
  onCriticalInteractionClick,
}: InteractionTabProps) {
  return (
    <TabPanelScrollArea>
      <CriticalInteractions
        criticalInteractions={sessionData.criticalInteractions}
        onCriticalInteractionClick={onCriticalInteractionClick}
      />
    </TabPanelScrollArea>
  );
}
