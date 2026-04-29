/**
 * Step 6: Severity & Notification
 * API: GET /v1/alert/severity, GET channel event mappings (alert firing)
 */

import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Box,
  Card,
  Group,
  Radio,
  Select,
  Stack,
  Text,
  ThemeIcon,
} from "@mantine/core";
import {
  IconAlertCircle,
  IconAlertTriangle,
  IconInfoCircle,
} from "@tabler/icons-react";
import { useAlertFormContext } from "../../../context";
import { useGetAlertSeverities } from "../../../../../hooks/useGetAlertSeverities";
import { AlertSeverityItem } from "../../../../../hooks/useGetAlertSeverities/useGetAlertSeverities.interface";
import { useGetChannelMappings } from "../../../../../hooks/useChannelMappings";
import { NOTIFICATION_EVENT_NAMES } from "../../../../../constants/Constants";
import type { ChannelType } from "../../../../../types";
import { StepHeader } from "../StepHeader";
import classes from "./StepSeverityNotification.module.css";

export interface StepSeverityNotificationProps {
  className?: string;
}

const CHANNEL_TYPE_LABELS: Record<ChannelType, string> = {
  EMAIL: "Email",
  SLACK: "Slack",
  SLACK_WEBHOOK: "Slack Webhook",
  TEAMS: "Microsoft Teams",
};

const CHANNEL_TYPE_ORDER: ChannelType[] = [
  "EMAIL",
  "SLACK",
  "SLACK_WEBHOOK",
  "TEAMS",
];

// Map severity level (number) to display info
const SEVERITY_LEVEL_MAP: Record<
  number,
  { label: string; icon: React.ElementType; color: string }
> = {
  1: { label: "Critical", icon: IconAlertTriangle, color: "red" },
  2: { label: "Warning", icon: IconAlertCircle, color: "orange" },
  3: { label: "Info", icon: IconInfoCircle, color: "blue" },
};

export const StepSeverityNotification: React.FC<
  StepSeverityNotificationProps
> = ({ className }) => {
  const { formData, updateStepData } = useAlertFormContext();
  const { severityId, channelEventMappingId } = formData.severityNotification;

  const { data: severitiesResponse } = useGetAlertSeverities();
  const { data: mappingsResponse, isLoading: isLoadingMappings } =
    useGetChannelMappings({
      eventName: NOTIFICATION_EVENT_NAMES.PULSE_ALERT_FIRING,
    });

  const severities: AlertSeverityItem[] =
    severitiesResponse?.data && Array.isArray(severitiesResponse.data)
      ? severitiesResponse.data
      : [];

  const channelMappings =
    mappingsResponse?.data && Array.isArray(mappingsResponse.data)
      ? mappingsResponse.data
      : [];

  const [selectedType, setSelectedType] = useState<ChannelType | "">("");

  useEffect(() => {
    if (!channelEventMappingId || channelMappings.length === 0) {
      return;
    }
    const mapping = channelMappings.find((m) => m.id === channelEventMappingId);
    if (mapping) {
      setSelectedType(mapping.channelType);
    }
  }, [channelEventMappingId, channelMappings]);

  const typeOptions = useMemo(() => {
    const present = new Set(channelMappings.map((m) => m.channelType));
    return CHANNEL_TYPE_ORDER.filter((t) => present.has(t)).map((t) => ({
      value: t,
      label: CHANNEL_TYPE_LABELS[t],
    }));
  }, [channelMappings]);

  const channelOptionsFiltered = useMemo(() => {
    if (!selectedType) {
      return [];
    }
    return channelMappings
      .filter((m) => m.channelType === selectedType)
      .map((m) => ({
        value: String(m.id),
        label: m.recipientName || m.channelName || `Channel mapping #${m.id}`,
      }));
  }, [channelMappings, selectedType]);

  const handleSeverityChange = useCallback(
    (id: number) => {
      updateStepData("severityNotification", { severityId: id });
    },
    [updateStepData],
  );

  const handleNotificationTypeChange = useCallback(
    (value: string | null) => {
      const next = (value || "") as ChannelType | "";
      setSelectedType(next);
      updateStepData("severityNotification", { channelEventMappingId: null });
    },
    [updateStepData],
  );

  const handleChannelChange = useCallback(
    (value: string | null) => {
      const id = value ? Number(value) : null;
      updateStepData("severityNotification", { channelEventMappingId: id });
    },
    [updateStepData],
  );

  return (
    <Box className={`${classes.container} ${className || ""}`}>
      <StepHeader
        title="Severity & Notification"
        description="Configure alert severity and notification channel"
      />

      <Box className={classes.section}>
        <Text className={classes.sectionTitle}>Alert Severity</Text>
        <Radio.Group
          value={severityId?.toString() || ""}
          onChange={(v) => handleSeverityChange(Number(v))}
        >
          <Group gap="md">
            {severities.map((sev) => {
              const levelInfo = SEVERITY_LEVEL_MAP[sev.name] || {
                label: `Level ${sev.name}`,
                icon: IconInfoCircle,
                color: "gray",
              };
              const { label, icon: Icon, color } = levelInfo;
              return (
                <Card
                  key={sev.severity_id}
                  className={`${classes.severityCard} ${
                    severityId === sev.severity_id ? classes.selected : ""
                  }`}
                  onClick={() => handleSeverityChange(sev.severity_id)}
                >
                  <Group>
                    <ThemeIcon size="lg" variant="light" color={color}>
                      <Icon size={20} />
                    </ThemeIcon>
                    <Box>
                      <Text fw={500}>{label}</Text>
                      <Text size="xs" c="dimmed">
                        {sev.description}
                      </Text>
                    </Box>
                    <Radio value={sev.severity_id.toString()} ml="auto" />
                  </Group>
                </Card>
              );
            })}
          </Group>
        </Radio.Group>
      </Box>

      <Box className={classes.section}>
        <Text className={classes.sectionTitle}>Notification Channel</Text>
        <Stack gap="md">
          <Select
            label="Notification type"
            placeholder={
              isLoadingMappings ? "Loading…" : "Select type (email, Slack, …)"
            }
            data={typeOptions}
            value={selectedType || null}
            onChange={handleNotificationTypeChange}
            disabled={isLoadingMappings || typeOptions.length === 0}
            searchable
          />
          <Select
            label="Channel"
            placeholder={
              !selectedType
                ? "Select a notification type first"
                : channelOptionsFiltered.length === 0
                  ? "No channels for this type"
                  : "Select channel"
            }
            data={channelOptionsFiltered}
            value={
              channelEventMappingId != null
                ? String(channelEventMappingId)
                : null
            }
            onChange={handleChannelChange}
            disabled={
              isLoadingMappings ||
              !selectedType ||
              channelOptionsFiltered.length === 0
            }
            searchable
            clearable
          />
          {!isLoadingMappings && channelMappings.length === 0 && (
            <Alert
              icon={<IconAlertTriangle size={16} />}
              color="yellow"
              variant="light"
            >
              No notification channels configured. Add channels in Settings →
              Notification Channels.
            </Alert>
          )}
        </Stack>
      </Box>
    </Box>
  );
};
