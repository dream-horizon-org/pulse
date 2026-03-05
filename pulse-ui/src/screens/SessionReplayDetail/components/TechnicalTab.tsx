import { Stack, Alert, Text } from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";
import {
  SessionDetailData,
  DetectedIssue,
} from "../../../services/sessionReplay/mockSessionDetail";
import { MESSAGES } from "../constants/strings";
import { RootCauseAnalysis } from "./technical/RootCauseAnalysis";
import { CodeReferences } from "./technical/CodeReferences";
import { ErrorGroupInfoComponent } from "./technical/ErrorGroupInfo";
import { RelatedIssuesPRs } from "./technical/RelatedIssuesPRs";
import { Reproducibility } from "./technical/Reproducibility";
import { EnvironmentInfoComponent } from "./technical/EnvironmentInfo";
import { QuickActions } from "./technical/QuickActions";

interface TechnicalTabProps {
  sessionData: SessionDetailData;
  detectedIssues: DetectedIssue[];
}

export const TechnicalTab: React.FC<TechnicalTabProps> = ({
  sessionData,
  detectedIssues,
}) => {
  const { technicalContext } = sessionData;

  if (!technicalContext) {
    return (
      <Alert color="gray" icon={<IconAlertCircle size={16} />}>
        <Text size="sm">{MESSAGES.NO_TECHNICAL_CONTEXT}</Text>
      </Alert>
    );
  }

  const hasErrors = detectedIssues.length > 0;
  const rootCause = technicalContext.rootCause;

  return (
    <Stack gap="lg">
      {/* ROOT CAUSE ANALYSIS */}
      {rootCause && (
        <RootCauseAnalysis
          rootCause={rootCause}
          technicalCause={detectedIssues[0]?.technicalCause}
        />
      )}

      {/* CODE REFERENCES */}
      {technicalContext.codeReferences &&
        technicalContext.codeReferences.length > 0 && (
          <CodeReferences
            codeReferences={technicalContext.codeReferences}
          />
        )}

      {/* ERROR GROUP INFO */}
      {technicalContext.errorGroupInfo && (
        <ErrorGroupInfoComponent
          errorGroupInfo={technicalContext.errorGroupInfo}
        />
      )}

      {/* RELATED ISSUES & PRS */}
      <RelatedIssuesPRs
        relatedPRs={technicalContext.relatedPRs}
        relatedJiraIssues={technicalContext.relatedJiraIssues}
      />

      {/* REPRODUCIBILITY */}
      <Reproducibility
        reproducibilityScore={technicalContext.reproducibilityScore}
        reproductionSteps={technicalContext.reproductionSteps}
      />

      {/* ENVIRONMENT INFO */}
      <EnvironmentInfoComponent
        environmentInfo={technicalContext.environmentInfo}
      />

      {/* QUICK ACTIONS */}
      <QuickActions />
    </Stack>
  );
};

