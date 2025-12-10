/**
 * Step 6: Severity & Notification
 * API: GET /v1/alert/severity, GET /v1/alert/notificationChannels
 */

import React, { useCallback } from "react";
import { Box, Text, Select, Radio, Group, Card, ThemeIcon } from "@mantine/core";
import { IconAlertTriangle, IconAlertCircle, IconInfoCircle } from "@tabler/icons-react";
import { useAlertFormContext } from "../../../context";
import { useGetAlertSeverities } from "../../../../../hooks/useGetAlertSeverities";
import { useGetAlertNotificationChannels } from "../../../../../hooks/useGetAlertNotificationChannels";
import { StepHeader } from "../StepHeader";
import classes from "./StepSeverityNotification.module.css";

export interface StepSeverityNotificationProps { className?: string; }

// Map severity level (number) to display info
const SEVERITY_LEVEL_MAP: Record<number, { label: string; icon: React.ElementType; color: string }> = {
  1: { label: "Critical", icon: IconAlertTriangle, color: "red" },
  2: { label: "Warning", icon: IconAlertCircle, color: "orange" },
  3: { label: "Info", icon: IconInfoCircle, color: "blue" },
};

export const StepSeverityNotification: React.FC<StepSeverityNotificationProps> = ({ className }) => {
  const { formData, updateStepData } = useAlertFormContext();
  const { severityId, notificationChannelId } = formData.severityNotification;

  const { data: severitiesResponse } = useGetAlertSeverities();
  const { data: channelsResponse } = useGetAlertNotificationChannels();

  const severities = severitiesResponse?.data?.severity || [];
  const channels = channelsResponse?.data || [];

  const handleSeverityChange = useCallback((id: number) => {
    updateStepData("severityNotification", { severityId: id });
  }, [updateStepData]);

  const handleChannelChange = useCallback((value: string | null) => {
    updateStepData("severityNotification", { notificationChannelId: value ? Number(value) : null });
  }, [updateStepData]);

  return (
    <Box className={`${classes.container} ${className || ""}`}>
      <StepHeader title="Severity & Notification" description="Configure alert severity and notification channel" />

      <Box className={classes.section}>
        <Text className={classes.sectionTitle}>Alert Severity</Text>
        <Radio.Group value={severityId?.toString() || ""} onChange={(v) => handleSeverityChange(Number(v))}>
          <Group gap="md">
            {severities.map((sev) => {
              const levelInfo = SEVERITY_LEVEL_MAP[sev.name] || { label: `Level ${sev.name}`, icon: IconInfoCircle, color: "gray" };
              const { label, icon: Icon, color } = levelInfo;
              return (
                <Card key={sev.severity_id} className={`${classes.severityCard} ${severityId === sev.severity_id ? classes.selected : ""}`} onClick={() => handleSeverityChange(sev.severity_id)}>
                  <Group>
                    <ThemeIcon size="lg" variant="light" color={color}><Icon size={20} /></ThemeIcon>
                    <Box>
                      <Text fw={500}>{label}</Text>
                      <Text size="xs" c="dimmed">{sev.description}</Text>
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
        <Select
          placeholder="Select channel"
          data={channels.map((c) => ({
            value: c.notification_channel_id.toString(),
            label: c.name,
          }))}
          value={notificationChannelId?.toString() || null}
          onChange={handleChannelChange}
        />
      </Box>
    </Box>
  );
};
