import { Badge } from "@mantine/core";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { CriticalInteractions } from "./all/CriticalInteractions";
import { SessionDetailTabPanel } from "./SessionDetailTabPanel";
import {
  FORMAT_STRINGS,
  TAB_PANEL_DESCRIPTION,
  TAB_PANEL_TITLE,
} from "../constants/strings";

interface InteractionTabProps {
  sessionData: SessionDetailData;
  onCriticalInteractionClick?: (t0: number, t1: number) => void;
}

export function InteractionTab({
  sessionData,
  onCriticalInteractionClick,
}: InteractionTabProps) {
  const { criticalInteractions } = sessionData;
  const successCount = criticalInteractions.filter(
    (i) => i.status === "success",
  ).length;
  const total = criticalInteractions.length;

  const toolbar =
    total > 0 ? (
      <Badge size="sm" variant="light" color="blue">
        {FORMAT_STRINGS.SUCCESSFUL_COUNT.replace(
          "{success}",
          successCount.toString(),
        ).replace("{total}", total.toString())}
      </Badge>
    ) : null;

  return (
    <SessionDetailTabPanel
      title={TAB_PANEL_TITLE.INTERACTION}
      description={TAB_PANEL_DESCRIPTION.INTERACTION}
      toolbar={toolbar}
    >
      <CriticalInteractions
        criticalInteractions={criticalInteractions}
        onCriticalInteractionClick={onCriticalInteractionClick}
        hideSummaryBadge
      />
    </SessionDetailTabPanel>
  );
}
