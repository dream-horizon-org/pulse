import { Stack } from "@mantine/core";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { UserJourney } from "./all/UserJourney";
import { CriticalInteractions } from "./all/CriticalInteractions";
import { NetworkRequests } from "./all/NetworkRequests";

interface AllTabProps {
  sessionData: SessionDetailData;
  onCriticalInteractionClick?: (t0: number, t1: number) => void;
}

export function AllTab({
  sessionData,
  onCriticalInteractionClick,
}: AllTabProps) {
  return (
    <Stack gap="lg">
      <UserJourney journey={sessionData.journey} />

      <CriticalInteractions
        criticalInteractions={sessionData.criticalInteractions}
        onCriticalInteractionClick={onCriticalInteractionClick}
      />

      <NetworkRequests networkRequests={sessionData.networkRequests} />
    </Stack>
  );
}
