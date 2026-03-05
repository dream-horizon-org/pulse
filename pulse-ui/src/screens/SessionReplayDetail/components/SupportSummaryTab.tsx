import { Stack, Alert, Text } from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";
import {
  SessionDetailData,
  DetectedIssue,
} from "../../../services/sessionReplay/mockSessionDetail";
import { IssueQuickFacts } from "./support/IssueQuickFacts";
import { CustomerImpact } from "./support/CustomerImpact";
import { SimilarIssues } from "./support/SimilarIssues";
import { KnownIssue } from "./support/KnownIssue";
import { QuickActions } from "./support/QuickActions";
import { UserHistory } from "./support/UserHistory";

interface SupportSummaryTabProps {
  sessionData: SessionDetailData;
  detectedIssues: DetectedIssue[];
}

export const SupportSummaryTab: React.FC<SupportSummaryTabProps> = ({
  sessionData,
  detectedIssues,
}) => {
  const criticalIssues = detectedIssues.filter(
    (i) => i.severity === "critical" || i.severity === "high",
  );
  const hasKnownIssue = sessionData.supportContext?.matchesKnownIssue;

  return (
    <Stack gap="lg">
      <IssueQuickFacts criticalIssues={criticalIssues} />

      <CustomerImpact sessionData={sessionData} />

      {sessionData.businessContext?.similarErrorsToday && (
        <SimilarIssues
          similarErrorsToday={sessionData.businessContext.similarErrorsToday}
        />
      )}

      {hasKnownIssue && <KnownIssue knownIssue={hasKnownIssue} />}

      {sessionData.supportContext?.suggestedActions && (
        <QuickActions
          suggestedActions={sessionData.supportContext.suggestedActions}
        />
      )}

      {sessionData.supportContext?.previousIssues && (
        <UserHistory
          previousIssues={sessionData.supportContext.previousIssues}
        />
      )}
    </Stack>
  );
};
