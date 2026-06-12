import { Divider, Text } from "@mantine/core";
import { InteractionEventSequence } from "../InteractionEventSequence";
import classes from "./InteractionDetailsEventsInfo.module.css";
import { UserCategorisationDetails } from "../UserCategorisationDetails";
import { InteractionDetailsMainContentProps } from "../../InteractionDetailsMainContentProps.interface";

export function InteractionDetailsEventsInfo({
  jobDetails,
}: InteractionDetailsMainContentProps) {
  if (!jobDetails) return <></>;

  return (
    <div className={classes.EventSequenceDetailsConatiner}>
      {/* Event Sequence Section */}
      <div className={classes.sectionHeader}>
        <Text size="sm" fw={600} c="dark">
          Event Sequence
        </Text>
        <Text size="xs" c="dimmed">
          {jobDetails.events?.length || 0} events
        </Text>
      </div>

      <InteractionEventSequence
        eventSequence={jobDetails.events || []}
        globalBlacklistedEvents={jobDetails.globalBlacklistedEvents || []}
        lineVariant="solid"
      />

      {/* User Categorization */}
      <Divider className={classes.EventInfoDivider} />
      <UserCategorisationDetails
        highUptime={jobDetails.uptimeUpperLimitInMs}
        lowUptime={jobDetails.uptimeLowerLimitInMs}
        midUptime={jobDetails.uptimeMidLimitInMs}
      />
    </div>
  );
}
