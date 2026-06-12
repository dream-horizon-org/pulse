import { Text, Stack, Group, Badge, Box, Divider } from "@mantine/core";
import {
  IconCircleCheckFilled,
  IconAlertCircleFilled,
} from "@tabler/icons-react";
import { EventSequenceItem } from "../../../../../../hooks/useGetInteractionDetails/useGetInteractionDetails.interface";
import { InteractionEventSequenceProps } from "./InteractionEventSequence.interface";
import { EventProp } from "../../../../../../hooks/useGetInteractionDetails/useGetInteractionDetails.interface";
import classes from "./InteractionEventSequence.module.css";

export function InteractionEventSequence({
  eventSequence,
  globalBlacklistedEvents,
  lineVariant,
  lineWidth = 1,
}: InteractionEventSequenceProps) {
  const renderEventProps = (props: EventProp[]) => {
    if (!props || props.length === 0) return null;

    return (
      <Stack gap={4} mt={6}>
        {props.map((prop, idx) => (
          <Group key={`${prop.name}-${idx}`} gap={6} wrap="nowrap">
            <Text size="xs" c="dimmed" fw={500} className={classes.propName}>
              {prop.name}:
            </Text>
            <Text size="xs" c="dark" className={classes.propValue}>
              {prop.value}
            </Text>
          </Group>
        ))}
      </Stack>
    );
  };

  const renderEvent = (
    event: EventSequenceItem,
    index: number,
    isBlacklisted: boolean,
  ) => {
    const Icon = isBlacklisted ? IconAlertCircleFilled : IconCircleCheckFilled;
    const iconColor = isBlacklisted
      ? "var(--mantine-color-red-6)"
      : "var(--mantine-color-blue-6)";

    return (
      <div key={`${event.name}-${index}`} className={classes.eventItem}>
        <Group gap={8} wrap="nowrap" align="flex-start">
          {/* Step Number & Line */}
          <div className={classes.stepContainer}>
            <div className={classes.stepNumber}>{index + 1}</div>
            {index < eventSequence.length - 1 && (
              <div className={classes.stepLine} />
            )}
          </div>

          {/* Event Content */}
          <Box className={classes.eventContent}>
            <Group gap={8} wrap="nowrap" align="center" mb={4}>
              <Icon size={14} color={iconColor} className={classes.eventIcon} />
              <Text size="sm" fw={600} c="dark" className={classes.eventName}>
                {event.name}
              </Text>
              {isBlacklisted && (
                <Badge size="xs" color="red" variant="light">
                  Blacklisted
                </Badge>
              )}
            </Group>
            {event.props && renderEventProps(event.props)}
          </Box>
        </Group>
      </div>
    );
  };

  return (
    <div className={classes.EventSequenceContainer}>
      {eventSequence.map((event, index) =>
        renderEvent(event, index, event.isBlacklisted || false),
      )}
      <Divider className={classes.EventInfoDivider} mb={15} mt={20}/>
      <Text size="sm" fw={600} c="dark" mb={10}>Global Blacklisted Events</Text>
      {globalBlacklistedEvents.map((event, index) =>
        renderEvent(event, eventSequence.length + index, true),
      )}
    </div>
  );
}
