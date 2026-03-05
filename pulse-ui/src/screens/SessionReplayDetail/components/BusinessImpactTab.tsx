import { Stack, Alert, Text } from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";
import { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { MESSAGES, DEFAULTS } from "../constants/strings";
import { ConversionStatus } from "./business/ConversionStatus";
import { JourneyTiming } from "./business/JourneyTiming";
import { PatternDetection } from "./business/PatternDetection";
import { UserSegmentation } from "./business/UserSegmentation";
import { ABTests } from "./business/ABTests";
import { FeatureEngagement } from "./business/FeatureEngagement";
import { ProductActions } from "./business/ProductActions";

interface BusinessImpactTabProps {
  sessionData: SessionDetailData;
}

export const BusinessImpactTab: React.FC<BusinessImpactTabProps> = ({ sessionData }) => {
  const { businessContext, sessionIntent } = sessionData;
  
  if (!businessContext) {
    return (
      <Alert color="gray" icon={<IconAlertCircle size={16} />}>
        <Text size="sm">{MESSAGES.NO_BUSINESS_CONTEXT}</Text>
      </Alert>
    );
  }

  const expectedDuration =
    sessionIntent?.expectedDuration || DEFAULTS.EXPECTED_DURATION_MS;
  const actualDuration = sessionIntent?.actualDuration || sessionData.duration;

  return (
    <Stack gap="lg">
      <ConversionStatus
        businessContext={businessContext}
        sessionIntent={sessionIntent}
      />

      <JourneyTiming
        actualDuration={actualDuration}
        expectedDuration={expectedDuration}
        journey={sessionData.journey}
      />

      <PatternDetection businessContext={businessContext} />

      <UserSegmentation businessContext={businessContext} />

      <ABTests businessContext={businessContext} />

      <FeatureEngagement
        businessContext={businessContext}
        sessionDuration={sessionData.duration}
      />

      <ProductActions />
    </Stack>
  );
};
