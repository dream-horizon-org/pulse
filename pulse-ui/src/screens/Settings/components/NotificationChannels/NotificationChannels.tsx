import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useSearchParams } from "react-router-dom";
import {
  ActionIcon,
  Badge,
  Box,
  Button,
  Group,
  Loader,
  Modal,
  MultiSelect,
  Stack,
  Table,
  TagsInput,
  Text,
  TextInput,
  Tooltip,
  useMantineTheme,
} from "@mantine/core";
import { useDisclosure } from "@mantine/hooks";
import {
  IconBell,
  IconBrandSlack,
  IconCircleCheckFilled,
  IconLink,
  IconMail,
  IconPlus,
  IconRefresh,
  IconSquareRoundedX,
  IconTrash,
} from "@tabler/icons-react";
import {
  COMMON_CONSTANTS,
  NOTIFICATION_CHANNELS_UPDATED_MESSAGE,
  NOTIFICATION_EVENT_NAMES,
} from "../../../../constants";
import { showNotification } from "../../../../helpers/showNotification";
import { useCreateNotificationChannel } from "../../../../hooks/useCreateNotificationChannel";
import { useGetNotificationChannels } from "../../../../hooks/useGetNotificationChannels";
import { useUpdateNotificationChannel } from "../../../../hooks/useUpdateNotificationChannel";
import {
  useCreateChannelMapping,
  useCreateChannelMappingsBatch,
  useDeleteChannelMapping,
  useGetChannelMappings,
  useGetSlackChannels,
  useGetSlackInstallUrl,
  useUpdateChannelMapping,
} from "../../../../hooks/useChannelMappings";
import {
  ChannelType,
  SlackWorkspaceChannel,
  NotificationChannel,
  ChannelEventMapping,
} from "../../../../types";
import classes from "./NotificationChannels.module.css";

type ChannelFormType = "SLACK" | "SLACK_WEBHOOK" | "EMAIL";
type NotificationChannelRow = {
  id: string;
  mappingId: number | null;
  name: string;
  type: ChannelType;
  configuration: string;
};

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CHANNEL_TYPE_LABELS: Record<ChannelType, string> = {
  SLACK: "Slack Connect",
  SLACK_WEBHOOK: "Slack Webhook",
  EMAIL: "Email",
  TEAMS: "Microsoft Teams",
};

const CHANNEL_TYPE_SECTION_SUBTITLES: Partial<Record<ChannelType, string>> = {
  SLACK: "Workspace channels wired to alert firing",
  SLACK_WEBHOOK: "Incoming webhooks",
  EMAIL: "Email recipients",
};
const CHANNEL_TYPE_SECTIONS: ChannelType[] = [
  "SLACK",
  "SLACK_WEBHOOK",
  "EMAIL",
  "TEAMS",
];
const WEBHOOK_DISPLAY_MAX_LENGTH = 56;
const SESSION_FROM_ALERT_WIZARD = "pulse_notification_from_alert_wizard";
const SESSION_OPEN_SLACK_MODAL_ON_RETURN =
  "pulse_notification_open_slack_modal_on_return";
const SESSION_SLACK_CALLBACK_PROCESSED =
  "pulse_notification_slack_callback_processed";

export function NotificationChannels() {
  const theme = useMantineTheme();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [formOpened, { open: openForm, close: closeForm }] =
    useDisclosure(false);
  const [
    disconnectConfirmOpened,
    { open: openDisconnectConfirm, close: closeDisconnectConfirm },
  ] = useDisclosure(false);
  const openAddFromWizardHandled = useRef(false);

  const [channelType, setChannelType] =
    useState<ChannelFormType>("SLACK_WEBHOOK");
  const [channelName, setChannelName] = useState("");
  const [webhookUrl, setWebhookUrl] = useState("");
  const [emails, setEmails] = useState<string[]>([]);
  const [selectedSlackChannelIds, setSelectedSlackChannelIds] = useState<
    string[]
  >([]);

  const {
    data: channelsResponse,
    isLoading: isChannelsLoading,
    refetch: refetchChannels,
  } = useGetNotificationChannels();
  const {
    data: mappingsResponse,
    isLoading: isMappingsLoading,
    refetch: refetchMappings,
  } = useGetChannelMappings({
    eventName: NOTIFICATION_EVENT_NAMES.PULSE_ALERT_FIRING,
  });
  const { data: slackChannelsResponse, refetch: refetchSlackChannels } =
    useGetSlackChannels();
  const slackInstallMutation = useGetSlackInstallUrl();

  const createChannelMutation = useCreateNotificationChannel();
  const updateChannelMutation = useUpdateNotificationChannel();
  const createMappingMutation = useCreateChannelMapping();
  const createMappingsBatchMutation = useCreateChannelMappingsBatch();
  const deleteMappingMutation = useDeleteChannelMapping();
  const updateMappingMutation = useUpdateChannelMapping();

  const channels = channelsResponse?.data ?? [];
  const mappings = mappingsResponse?.data ?? [];
  const slackChannels = slackChannelsResponse?.data ?? [];

  const mappingsByChannelId = useMemo(() => {
    return mappings.reduce<Record<number, ChannelEventMapping[]>>(
      (acc, mapping) => {
        if (!acc[mapping.channelId]) {
          acc[mapping.channelId] = [];
        }
        acc[mapping.channelId].push(mapping);
        return acc;
      },
      {},
    );
  }, [mappings]);

  const slackConnectedChannel = useMemo(
    () => channels.find((channel) => channel.channelType === "SLACK") || null,
    [channels],
  );

  const mappedSlackChannelIds = useMemo(() => {
    if (!slackConnectedChannel) {
      return new Set<string>();
    }
    return new Set(
      (mappingsByChannelId[slackConnectedChannel.id] ?? [])
        .map((mapping) => mapping.recipient)
        .filter((recipient): recipient is string => Boolean(recipient)),
    );
  }, [mappingsByChannelId, slackConnectedChannel]);

  const slackMappings = useMemo(
    () =>
      slackConnectedChannel
        ? (mappingsByChannelId[slackConnectedChannel.id] ?? [])
        : [],
    [mappingsByChannelId, slackConnectedChannel],
  );

  const slackChannelOptions = useMemo(
    () =>
      slackChannels
        .filter(
          (channel: SlackWorkspaceChannel) =>
            !mappedSlackChannelIds.has(channel.id),
        )
        .map((channel: SlackWorkspaceChannel) => ({
          value: channel.id,
          label: channel.name,
        })),
    [mappedSlackChannelIds, slackChannels],
  );

  const resetForm = useCallback(() => {
    setChannelType("SLACK_WEBHOOK");
    setChannelName("");
    setWebhookUrl("");
    setEmails([]);
    setSelectedSlackChannelIds([]);
  }, []);

  const refreshAll = useCallback(async () => {
    await Promise.all([refetchChannels(), refetchMappings()]);
  }, [refetchChannels, refetchMappings]);

  useEffect(() => {
    if (searchParams.get("fromAlertWizard") === "1") {
      sessionStorage.setItem(SESSION_FROM_ALERT_WIZARD, "1");
    }
  }, [searchParams]);

  const notifyReturnToAlertWizard = useCallback(() => {
    const shouldReturnToAlertWizard =
      searchParams.get("fromAlertWizard") === "1" ||
      sessionStorage.getItem(SESSION_FROM_ALERT_WIZARD) === "1";
    if (!shouldReturnToAlertWizard) {
      return;
    }
    try {
      if (window.opener && !window.opener.closed) {
        window.opener.postMessage(
          { type: NOTIFICATION_CHANNELS_UPDATED_MESSAGE },
          window.location.origin,
        );
      }
    } catch {
      /* ignore */
    }
    sessionStorage.removeItem(SESSION_FROM_ALERT_WIZARD);
    sessionStorage.removeItem(SESSION_OPEN_SLACK_MODAL_ON_RETURN);
    window.close();
  }, [searchParams]);

  const showError = useCallback(
    (message: string) => {
      showNotification(
        COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
        message,
        <IconSquareRoundedX />,
        theme.colors.red[6],
      );
    },
    [theme.colors.red],
  );

  const showSuccess = useCallback(
    (message: string) => {
      showNotification(
        COMMON_CONSTANTS.SUCCESS_NOTIFICATION_TITLE,
        message,
        <IconCircleCheckFilled />,
        theme.colors.teal[6],
      );
    },
    [theme.colors.teal],
  );

  const handleSlackCallback = useCallback(async () => {
    const status = searchParams.get("slack");
    const message = searchParams.get("message");
    if (!status) {
      sessionStorage.removeItem(SESSION_SLACK_CALLBACK_PROCESSED);
      return;
    }

    const callbackSignature = [
      location.pathname,
      status,
      message || "",
      searchParams.get("fromAlertWizard") || "",
    ].join("|");
    if (
      sessionStorage.getItem(SESSION_SLACK_CALLBACK_PROCESSED) ===
      callbackSignature
    ) {
      const nextParams = new URLSearchParams(searchParams);
      nextParams.delete("slack");
      nextParams.delete("message");
      setSearchParams(nextParams, { replace: true });
      return;
    }
    sessionStorage.setItem(SESSION_SLACK_CALLBACK_PROCESSED, callbackSignature);

    if (status === "success") {
      showSuccess(`Slack connected${message ? `: ${message}` : ""}`);
      await refreshAll();
      setChannelType("SLACK");
      openForm();
      sessionStorage.removeItem(SESSION_OPEN_SLACK_MODAL_ON_RETURN);
    } else {
      showError(message || "Slack connection failed");
    }
    const nextParams = new URLSearchParams(searchParams);
    nextParams.delete("slack");
    nextParams.delete("message");
    setSearchParams(nextParams, { replace: true });
  }, [
    location.pathname,
    openForm,
    refreshAll,
    searchParams,
    setSearchParams,
    showError,
    showSuccess,
  ]);

  useEffect(() => {
    void handleSlackCallback();
  }, [handleSlackCallback]);

  useEffect(() => {
    if (openAddFromWizardHandled.current) {
      return;
    }
    if (
      searchParams.get("fromAlertWizard") === "1" &&
      searchParams.get("openAdd") === "1"
    ) {
      openAddFromWizardHandled.current = true;
      openForm();
      const next = new URLSearchParams(searchParams);
      next.delete("openAdd");
      setSearchParams(next, { replace: true });
    }
  }, [openForm, searchParams, setSearchParams]);

  useEffect(() => {
    if (sessionStorage.getItem(SESSION_OPEN_SLACK_MODAL_ON_RETURN) !== "1") {
      return;
    }
    if (!slackConnectedChannel) {
      return;
    }
    setChannelType("SLACK");
    openForm();
    sessionStorage.removeItem(SESSION_OPEN_SLACK_MODAL_ON_RETURN);
  }, [openForm, slackConnectedChannel]);

  useEffect(() => {
    if (!formOpened || channelType !== "SLACK") {
      return;
    }
    void refetchSlackChannels();
  }, [channelType, formOpened, refetchSlackChannels]);

  useEffect(() => {
    if (channelType !== "SLACK" || selectedSlackChannelIds.length === 0) {
      return;
    }
    const availableIds = new Set(
      slackChannelOptions.map((option) => option.value),
    );
    const filteredIds = selectedSlackChannelIds.filter((channelId) =>
      availableIds.has(channelId),
    );
    const isSameSelection =
      filteredIds.length === selectedSlackChannelIds.length &&
      filteredIds.every(
        (channelId, index) => channelId === selectedSlackChannelIds[index],
      );
    if (!isSameSelection) {
      setSelectedSlackChannelIds(filteredIds);
    }
  }, [channelType, selectedSlackChannelIds, slackChannelOptions]);

  const validateForm = (): string | null => {
    if (channelType === "SLACK") {
      if (slackConnectedChannel && selectedSlackChannelIds.length === 0) {
        return "Select at least one Slack channel";
      }
      return null;
    }
    if (!channelName.trim()) {
      return "Channel name is required";
    }
    if (channelType === "SLACK_WEBHOOK") {
      if (!webhookUrl.trim()) {
        return "Webhook URL is required";
      }
      if (!webhookUrl.startsWith("http")) {
        return "Webhook URL must be a valid URL";
      }
      return null;
    }
    if (emails.length === 0) {
      return "Add at least one email";
    }
    if (emails.some((email) => !EMAIL_REGEX.test(email.trim()))) {
      return "One or more email addresses are invalid";
    }
    return null;
  };

  const handleCreateSlackMappings = async () => {
    if (!slackConnectedChannel) {
      showError("Slack is not connected");
      return;
    }
    if (selectedSlackChannelIds.length === 0) {
      showError("Select at least one Slack channel");
      return;
    }

    const payload = selectedSlackChannelIds.map((channelId) => {
      const selected = slackChannels.find(
        (channel) => channel.id === channelId,
      );
      return {
        channelId: slackConnectedChannel.id,
        eventName: NOTIFICATION_EVENT_NAMES.PULSE_ALERT_FIRING,
        recipient: channelId,
        recipientName: selected?.name || channelId,
      };
    });

    const response = await createMappingsBatchMutation.mutateAsync({
      mappings: payload,
    });

    if (response.error) {
      showError(response.error.message);
      return;
    }

    showSuccess("Slack channels added");
    resetForm();
    closeForm();
    await refreshAll();
    notifyReturnToAlertWizard();
  };

  const handleCreateChannel = async () => {
    const validationError = validateForm();
    if (validationError) {
      showError(validationError);
      return;
    }

    if (channelType === "SLACK") {
      if (slackConnectedChannel) {
        await handleCreateSlackMappings();
        return;
      }
      const returnParams = new URLSearchParams(location.search);
      const fromAlertWizard =
        returnParams.get("fromAlertWizard") === "1" ||
        sessionStorage.getItem(SESSION_FROM_ALERT_WIZARD) === "1";
      if (fromAlertWizard) {
        returnParams.set("fromAlertWizard", "1");
      }
      returnParams.set("openAdd", "1");
      const returnPath = `${location.pathname}${
        returnParams.toString() ? `?${returnParams.toString()}` : ""
      }`;
      sessionStorage.setItem(SESSION_OPEN_SLACK_MODAL_ON_RETURN, "1");
      const install = await slackInstallMutation.mutateAsync({
        returnPath,
      });
      if (install.error || !install.data) {
        showError(install.error?.message || "Failed to start Slack connect");
        return;
      }
      window.location.assign(install.data);
      return;
    }

    const createResponse = await createChannelMutation.mutateAsync({
      channelType: channelType as ChannelType,
      name: channelName.trim(),
      config:
        channelType === "SLACK_WEBHOOK"
          ? {
              type: "SLACK_WEBHOOK",
            }
          : {
              type: "EMAIL",
            },
      eventNames: [NOTIFICATION_EVENT_NAMES.PULSE_ALERT_FIRING],
    });

    if (createResponse.error || createResponse.data == null) {
      showError(createResponse.error?.message || "Failed to create channel");
      return;
    }
    const createdChannelId = Number(createResponse.data);
    if (!Number.isFinite(createdChannelId) || createdChannelId <= 0) {
      showError("Invalid channel id returned from server");
      return;
    }

    if (channelType === "SLACK_WEBHOOK") {
      const mappingResponse = await createMappingMutation.mutateAsync({
        channelId: createdChannelId,
        eventName: NOTIFICATION_EVENT_NAMES.PULSE_ALERT_FIRING,
        recipient: webhookUrl.trim(),
        recipientName: channelName.trim(),
      });
      if (mappingResponse.error) {
        showError(mappingResponse.error.message);
        return;
      }
    } else {
      const mappingPayload = emails.map((email) => ({
        channelId: createdChannelId,
        eventName: NOTIFICATION_EVENT_NAMES.PULSE_ALERT_FIRING,
        recipient: email.trim(),
        recipientName: channelName.trim(),
      }));
      const mappingResponse = await createMappingsBatchMutation.mutateAsync({
        mappings: mappingPayload,
      });
      if (mappingResponse.error) {
        showError(mappingResponse.error.message);
        return;
      }
    }

    showSuccess("Channel and mappings created");
    resetForm();
    closeForm();
    await refreshAll();
    notifyReturnToAlertWizard();
  };

  const handleRemoveSlackConnect = async () => {
    if (!slackConnectedChannel) {
      return;
    }

    const channelResponse = await updateChannelMutation.mutateAsync({
      channelId: slackConnectedChannel.id,
      isActive: false,
    });
    if (channelResponse.error) {
      showError(channelResponse.error.message || "Failed to disconnect Slack");
      return;
    }

    if (slackMappings.length > 0) {
      const mappingResponses = await Promise.all(
        slackMappings.map((mapping) =>
          updateMappingMutation.mutateAsync({
            mappingId: mapping.id,
            recipient: mapping.recipient || undefined,
            recipientName: mapping.recipientName || undefined,
            isActive: false,
          }),
        ),
      );
      const failed = mappingResponses.find((response) => response.error);
      if (failed?.error) {
        showError(failed.error.message || "Failed to disable Slack mappings");
        return;
      }
    }

    showSuccess("Slack disconnected");
    setSelectedSlackChannelIds([]);
    await refreshAll();
  };

  const handleConfirmDisconnectSlack = async () => {
    await handleRemoveSlackConnect();
    closeDisconnectConfirm();
  };

  const isCreating =
    updateChannelMutation.isPending ||
    createChannelMutation.isPending ||
    createMappingMutation.isPending ||
    createMappingsBatchMutation.isPending ||
    updateMappingMutation.isPending;

  const handleDeleteMapping = async (mappingId: number) => {
    const response = await deleteMappingMutation.mutateAsync(mappingId);
    if (response.error) {
      showError(response.error.message);
      return;
    }
    showSuccess("Mapping deleted");
    await refreshAll();
  };

  const isLoading = isChannelsLoading || isMappingsLoading;

  /** One row per `pulse_alert_firing` mapping only (no unmapped channels). */
  const channelRows = useMemo<NotificationChannelRow[]>(() => {
    return mappings.flatMap((mapping) => {
      const channel = channels.find((c) => c.id === mapping.channelId);
      if (!channel) {
        return [];
      }
      return [
        {
          id: `${channel.id}-${mapping.id}`,
          mappingId: mapping.id,
          name:
            channel.channelType === "SLACK"
              ? mapping.recipientName || channel.name
              : channel.name,
          type: channel.channelType,
          configuration:
            mapping.recipient || mapping.recipientName || "No configuration",
        },
      ];
    });
  }, [mappings, channels]);

  const channelRowsByType = useMemo(() => {
    return CHANNEL_TYPE_SECTIONS.reduce<
      Record<ChannelType, NotificationChannelRow[]>
    >(
      (acc, type) => {
        acc[type] = channelRows.filter((row) => row.type === type);
        return acc;
      },
      {
        SLACK: [],
        SLACK_WEBHOOK: [],
        EMAIL: [],
        TEAMS: [],
      },
    );
  }, [channelRows]);

  const sectionTypesWithRows = useMemo(
    () =>
      CHANNEL_TYPE_SECTIONS.filter(
        (type) => channelRowsByType[type].length > 0,
      ),
    [channelRowsByType],
  );

  const getConfigurationDisplayText = useCallback(
    (row: NotificationChannelRow) => {
      if (
        row.type === "SLACK_WEBHOOK" &&
        row.configuration.length > WEBHOOK_DISPLAY_MAX_LENGTH
      ) {
        return `${row.configuration.slice(0, WEBHOOK_DISPLAY_MAX_LENGTH)}...`;
      }
      return row.configuration;
    },
    [],
  );

  return (
    <Box className={classes.pageContainer}>
      <Box className={classes.pageHeader}>
        <Box className={classes.headerGroup}>
          <Box className={classes.titleSection}>
            <Text className={classes.pageTitle}>Notification Channels</Text>
            <Badge size="sm" variant="light" color="teal">
              {channelRows.length}
            </Badge>
          </Box>
          <Group gap="sm">
            <Tooltip label="Refresh list" withArrow>
              <Button
                variant="subtle"
                color="gray"
                onClick={() => void refreshAll()}
              >
                <IconRefresh size={18} />
              </Button>
            </Tooltip>
            <Button
              leftSection={<IconPlus size={16} />}
              onClick={openForm}
              color="teal"
            >
              Add Channel
            </Button>
          </Group>
        </Box>
      </Box>

      <Box className={`${classes.channelListTable} ${classes.fadeIn}`}>
        {isLoading ? (
          <Box
            className={classes.tableWrapper}
            style={{ padding: "1rem", textAlign: "center" }}
          >
            <Loader size="sm" color="teal" />
          </Box>
        ) : (
          <Box className={classes.tableWrapper}>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Name</Table.Th>
                  <Table.Th>Configuration</Table.Th>
                  <Table.Th style={{ textAlign: "right" }}>Actions</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {sectionTypesWithRows.flatMap((type, sectionIndex) => {
                  const rows = channelRowsByType[type];
                  const isFirstSection = sectionIndex === 0;
                  const SectionIcon =
                    type === "SLACK"
                      ? IconBrandSlack
                      : type === "SLACK_WEBHOOK"
                        ? IconLink
                        : type === "EMAIL"
                          ? IconMail
                          : IconBell;
                  const sectionAccentClass =
                    type === "SLACK"
                      ? classes.sectionAccentSlack
                      : type === "SLACK_WEBHOOK"
                        ? classes.sectionAccentWebhook
                        : type === "EMAIL"
                          ? classes.sectionAccentEmail
                          : classes.sectionAccentDefault;

                  const sectionRow = (
                    <Table.Tr
                      key={`${type}-section`}
                      data-section-header
                      className={`${classes.sectionHeaderRow} ${sectionAccentClass} ${isFirstSection ? classes.sectionHeaderRowFirst : ""}`}
                    >
                      <Table.Td
                        colSpan={3}
                        className={classes.sectionHeaderCell}
                      >
                        <Group
                          justify="space-between"
                          wrap="nowrap"
                          align="flex-start"
                        >
                          <Stack gap={2} style={{ minWidth: 0 }}>
                            <Group gap="sm" wrap="wrap">
                              <Box
                                className={`${classes.sectionIconWrap} ${sectionAccentClass}`}
                              >
                                <SectionIcon size={18} stroke={1.75} />
                              </Box>
                              <Text className={classes.sectionTitle}>
                                {CHANNEL_TYPE_LABELS[type]}
                              </Text>
                              {type === "SLACK" && slackConnectedChannel && (
                                <Button
                                  color="red"
                                  variant="light"
                                  size="xs"
                                  onClick={openDisconnectConfirm}
                                  loading={updateChannelMutation.isPending}
                                >
                                  Disconnect Slack
                                </Button>
                              )}
                            </Group>
                            {CHANNEL_TYPE_SECTION_SUBTITLES[type] && (
                              <Text className={classes.sectionSubtitle}>
                                {CHANNEL_TYPE_SECTION_SUBTITLES[type]}
                              </Text>
                            )}
                          </Stack>
                          <Badge
                            size="sm"
                            variant="filled"
                            color="teal"
                            className={classes.sectionCountBadge}
                          >
                            {rows.length}
                          </Badge>
                        </Group>
                      </Table.Td>
                    </Table.Tr>
                  );

                  const dataRows = rows.map((row) => (
                    <Table.Tr
                      key={row.id}
                      data-tinted-row
                      className={`${classes.dataRowUnderSection} ${sectionAccentClass}`}
                    >
                      <Table.Td>{row.name}</Table.Td>
                      <Table.Td title={row.configuration}>
                        {getConfigurationDisplayText(row)}
                      </Table.Td>
                      <Table.Td>
                        <Group justify="flex-end">
                          {row.mappingId !== null && (
                            <ActionIcon
                              color="red"
                              variant="subtle"
                              onClick={() =>
                                void handleDeleteMapping(
                                  row.mappingId as number,
                                )
                              }
                              loading={deleteMappingMutation.isPending}
                            >
                              <IconTrash size={16} />
                            </ActionIcon>
                          )}
                        </Group>
                      </Table.Td>
                    </Table.Tr>
                  ));

                  return [sectionRow, ...dataRows];
                })}
              </Table.Tbody>
            </Table>
          </Box>
        )}
      </Box>

      <Modal
        opened={formOpened}
        onClose={closeForm}
        title="Add Notification Channel"
        centered
        size="lg"
      >
        <Box className={classes.modalContent}>
          <Stack gap="md">
            <Box>
              <Text size="sm" fw={500} mb="xs">
                Channel Type
              </Text>
              <Box className={classes.typeSelector}>
                <Box
                  className={`${classes.typeCard} ${channelType === "SLACK" ? classes.typeCardSelected : ""}`}
                  onClick={() => setChannelType("SLACK")}
                >
                  <Box
                    className={`${classes.typeCardIcon} ${classes.typeCardSlack}`}
                  >
                    <IconBrandSlack size={20} />
                  </Box>
                  <Text size="sm" fw={500}>
                    Slack Connect
                  </Text>
                  <Text size="xs" c="dimmed">
                    OAuth integration
                  </Text>
                </Box>
                <Box
                  className={`${classes.typeCard} ${channelType === "SLACK_WEBHOOK" ? classes.typeCardSelected : ""}`}
                  onClick={() => setChannelType("SLACK_WEBHOOK")}
                >
                  <Box
                    className={`${classes.typeCardIcon} ${classes.typeCardSlack}`}
                  >
                    <IconLink size={20} />
                  </Box>
                  <Text size="sm" fw={500}>
                    Slack Webhook
                  </Text>
                  <Text size="xs" c="dimmed">
                    Incoming webhook
                  </Text>
                </Box>
                <Box
                  className={`${classes.typeCard} ${channelType === "EMAIL" ? classes.typeCardSelected : ""}`}
                  onClick={() => setChannelType("EMAIL")}
                >
                  <Box
                    className={`${classes.typeCardIcon} ${classes.typeCardEmail}`}
                  >
                    <IconMail size={20} />
                  </Box>
                  <Text size="sm" fw={500}>
                    Email Group
                  </Text>
                  <Text size="xs" c="dimmed">
                    Multiple email recipients
                  </Text>
                </Box>
              </Box>
            </Box>

            {channelType === "SLACK" && (
              <>
                <Text size="sm" c="dimmed">
                  {slackConnectedChannel
                    ? "Slack already connected. Select workspace channels for mappings."
                    : "Click connect to start Slack OAuth flow."}
                </Text>
                {slackConnectedChannel && (
                  <MultiSelect
                    data={slackChannelOptions}
                    value={selectedSlackChannelIds}
                    onChange={setSelectedSlackChannelIds}
                    label="Slack channels"
                    placeholder={
                      slackChannelOptions.length === 0
                        ? "All workspace channels are already mapped"
                        : "Select one or more channels"
                    }
                    searchable
                    clearable
                    disabled={slackChannelOptions.length === 0}
                  />
                )}
              </>
            )}

            {channelType !== "SLACK" && (
              <TextInput
                label="Channel Name"
                placeholder="e.g. Critical Alert Notifications"
                value={channelName}
                onChange={(event) => setChannelName(event.target.value)}
                required
              />
            )}

            {channelType === "SLACK_WEBHOOK" && (
              <>
                <TextInput
                  label="Webhook URL"
                  placeholder="https://hooks.slack.com/services/..."
                  value={webhookUrl}
                  onChange={(event) => setWebhookUrl(event.target.value)}
                  required
                />
              </>
            )}

            {channelType === "EMAIL" && (
              <TagsInput
                label="Email Recipients"
                value={emails}
                onChange={setEmails}
                placeholder="Add email and press enter"
                splitChars={[",", " "]}
                clearable
                required
              />
            )}
          </Stack>
          <Box className={classes.modalActions}>
            <Button variant="default" onClick={closeForm}>
              Cancel
            </Button>
            {channelType === "SLACK" && slackConnectedChannel && (
              <Button
                color="red"
                variant="light"
                onClick={openDisconnectConfirm}
                loading={updateChannelMutation.isPending}
              >
                Disconnect Slack
              </Button>
            )}
            <Button
              color="teal"
              onClick={() => void handleCreateChannel()}
              loading={isCreating}
            >
              {channelType === "SLACK"
                ? slackConnectedChannel
                  ? "Add channels"
                  : "Connect Slack"
                : "Create Channel"}
            </Button>
          </Box>
        </Box>
      </Modal>
      <Modal
        opened={disconnectConfirmOpened}
        onClose={closeDisconnectConfirm}
        title="Disconnect Slack?"
        centered
        size="sm"
      >
        <Stack gap="md">
          <Text size="sm" c="dimmed">
            This will disable the Slack connection and all Slack channel
            mappings for this project. You can reconnect later.
          </Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={closeDisconnectConfirm}>
              Cancel
            </Button>
            <Button
              color="red"
              onClick={() => void handleConfirmDisconnectSlack()}
              loading={updateChannelMutation.isPending}
            >
              Disconnect
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Box>
  );
}
