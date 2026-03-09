/**
 * Notification Channels Management Component
 * Displays list of notification channels with full CRUD functionality
 * Supports Slack OAuth, Slack Webhook, Email, and Teams integrations
 */

import { useState, useCallback } from 'react';
import {
  Box,
  Text,
  Button,
  Group,
  Badge,
  ActionIcon,
  Tooltip,
  Loader,
  Table,
  Modal,
  TextInput,
  Stack,
  Select,
  Alert,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconPlus,
  IconBell,
  IconRefresh,
  IconBrandSlack,
  IconMail,
  IconCircleCheckFilled,
  IconSquareRoundedX,
  IconEdit,
  IconTrash,
  IconExternalLink,
  IconCheck,
  IconAlertCircle,
} from '@tabler/icons-react';
import { useGetAlertNotificationChannels } from '../../../../hooks/useGetAlertNotificationChannels';
import { useCreateNotificationChannel } from '../../../../hooks/useCreateNotificationChannel';
import { useUpdateNotificationChannel } from '../../../../hooks/useUpdateNotificationChannel';
import { useDeleteNotificationChannel } from '../../../../hooks/useDeleteNotificationChannel';
import { useSlackInstall } from '../../../../hooks/useSlackInstall';
import { useSlackChannels } from '../../../../hooks/useSlackChannels';
import { 
  NotificationChannelType,
  AlertNotificationChannelItem,
  SlackChannelListDto,
} from '../../../../hooks/useGetAlertNotificationChannels/useGetAlertNotificationChannels.interface';
import { showNotification } from '../../../../helpers/showNotification';
import { useMantineTheme } from '@mantine/core';
import { COMMON_CONSTANTS } from '../../../../constants';
import { useProjectContext } from '../../../../contexts';
import classes from './NotificationChannels.module.css';

type ChannelTypeOption = 'slack_oauth' | 'slack_webhook' | 'email';

type SlackOAuthConfig = {
  workspaceId?: string;
  workspaceName?: string;
  channelId?: string;
  channelName?: string;
  botName: string;
  iconEmoji: string;
};

type SlackWebhookConfig = {
  webhookUrl: string;
  botName: string;
  iconEmoji: string;
};

type EmailConfig = {
  fromAddress: string;
  fromName: string;
  replyToAddress: string;
  configurationSetName: string;
};

type FormData = {
  name: string;
  channelType: ChannelTypeOption;
  slackOAuthConfig: SlackOAuthConfig;
  slackWebhookConfig: SlackWebhookConfig;
  emailConfig: EmailConfig;
};

type ModalMode = 'create' | 'edit';

const initialFormData: FormData = {
  name: '',
  channelType: 'slack_oauth',
  slackOAuthConfig: {
    botName: 'PulseBot',
    iconEmoji: ':bell:',
  },
  slackWebhookConfig: {
    webhookUrl: '',
    botName: 'PulseBot',
    iconEmoji: ':bell:',
  },
  emailConfig: {
    fromAddress: '',
    fromName: '',
    replyToAddress: '',
    configurationSetName: 'pulse-prod',
  },
};

export function NotificationChannels() {
  const theme = useMantineTheme();
  const { projectId } = useProjectContext();
  
  // Modal states
  const [formModalOpened, { open: openFormModal, close: closeFormModal }] = useDisclosure(false);
  const [deleteModalOpened, { open: openDeleteModal, close: closeDeleteModal }] = useDisclosure(false);
  
  // Form state
  const [modalMode, setModalMode] = useState<ModalMode>('create');
  const [formData, setFormData] = useState<FormData>(initialFormData);
  const [formErrors, setFormErrors] = useState<Partial<Record<string, string>>>({});
  const [editingChannelId, setEditingChannelId] = useState<number | null>(null);
  const [deletingChannel, setDeletingChannel] = useState<AlertNotificationChannelItem | null>(null);
  const [slackConnecting, setSlackConnecting] = useState(false);

  // API hooks
  const { data, isLoading, isError, refetch } = useGetAlertNotificationChannels();
  const channels = data?.data ?? [];

  // Slack OAuth hooks
  const { getInstallUrl, isLoading: isLoadingInstallUrl } = useSlackInstall({ projectId: projectId || undefined });
  const { data: slackChannels } = useSlackChannels(projectId || undefined);

  // Close form modal helper
  const handleCloseFormModal = useCallback(() => {
    closeFormModal();
    setFormData(initialFormData);
    setFormErrors({});
    setEditingChannelId(null);
    setSlackConnecting(false);
  }, [closeFormModal]);

  // Close delete modal helper
  const handleCloseDeleteModal = useCallback(() => {
    closeDeleteModal();
    setDeletingChannel(null);
  }, [closeDeleteModal]);

  // Create mutation with error handling
  const createMutation = useCreateNotificationChannel({
    onSettled: (data, error) => {
      if (error || data?.error) {
        showNotification(
          COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
          data?.error?.message || 'Failed to create notification channel',
          <IconSquareRoundedX />,
          theme.colors.red[6]
        );
        return;
      }
      showNotification(
        COMMON_CONSTANTS.SUCCESS_NOTIFICATION_TITLE,
        'Notification channel created successfully',
        <IconCircleCheckFilled />,
        theme.colors.teal[6]
      );
      handleCloseFormModal();
      refetch();
    },
  });

  // Update mutation with error handling
  const updateMutation = useUpdateNotificationChannel({
    onSettled: (data, error) => {
      if (error || data?.error) {
        showNotification(
          COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
          data?.error?.message || 'Failed to update notification channel',
          <IconSquareRoundedX />,
          theme.colors.red[6]
        );
        return;
      }
      showNotification(
        COMMON_CONSTANTS.SUCCESS_NOTIFICATION_TITLE,
        'Notification channel updated successfully',
        <IconCircleCheckFilled />,
        theme.colors.teal[6]
      );
      handleCloseFormModal();
      refetch();
    },
  });

  // Delete mutation with error handling
  const deleteMutation = useDeleteNotificationChannel({
    onSettled: (data, error) => {
      if (error || data?.error) {
        showNotification(
          COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
          data?.error?.message || 'Failed to delete notification channel',
          <IconSquareRoundedX />,
          theme.colors.red[6]
        );
        return;
      }
      showNotification(
        COMMON_CONSTANTS.SUCCESS_NOTIFICATION_TITLE,
        'Notification channel deleted successfully',
        <IconCircleCheckFilled />,
        theme.colors.teal[6]
      );
      handleCloseDeleteModal();
      refetch();
    },
  });

  // Modal handlers
  const handleOpenCreateModal = useCallback(() => {
    setModalMode('create');
    setFormData(initialFormData);
    setFormErrors({});
    setEditingChannelId(null);
    openFormModal();
  }, [openFormModal]);

  const handleOpenEditModal = useCallback((channel: AlertNotificationChannelItem) => {
    setModalMode('edit');
    
    // Parse config string and populate form
    const isWebhook = channel.config.startsWith('http');
    
    setFormData({
      name: channel.name,
      channelType: isWebhook ? 'slack_webhook' : 'slack_oauth',
      slackOAuthConfig: {
        botName: 'PulseBot',
        iconEmoji: ':bell:',
      },
      slackWebhookConfig: {
        webhookUrl: isWebhook ? channel.config : '',
        botName: 'PulseBot',
        iconEmoji: ':bell:',
      },
      emailConfig: initialFormData.emailConfig,
    });
    
    setFormErrors({});
    setEditingChannelId(channel.notification_channel_id);
    openFormModal();
  }, [openFormModal]);

  const handleOpenDeleteModal = useCallback((channel: AlertNotificationChannelItem) => {
    setDeletingChannel(channel);
    openDeleteModal();
  }, [openDeleteModal]);

  // Handle Slack OAuth connection
  const handleConnectSlack = useCallback(async () => {
    setSlackConnecting(true);
    try {
      const url = await getInstallUrl();
      if (url) {
        // Open in new window and listen for completion
        window.location.href = url;
      } else {
        showNotification(
          COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
          'Failed to generate Slack OAuth URL',
          <IconSquareRoundedX />,
          theme.colors.red[6]
        );
      }
    } catch (error) {
      showNotification(
        COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
        'Failed to connect to Slack',
        <IconSquareRoundedX />,
        theme.colors.red[6]
      );
    } finally {
      setSlackConnecting(false);
    }
  }, [getInstallUrl, theme]);

  // Form handlers
  const handleTypeSelect = useCallback((type: ChannelTypeOption) => {
    setFormData(prev => ({ ...prev, channelType: type }));
    setFormErrors({});
  }, []);

  const handleInputChange = useCallback((field: string, value: string) => {
    setFormData(prev => {
      if (field.startsWith('slackOAuth.')) {
        const key = field.split('.')[1];
        return {
          ...prev,
          slackOAuthConfig: { ...prev.slackOAuthConfig, [key]: value },
        };
      } else if (field.startsWith('slackWebhook.')) {
        const key = field.split('.')[1];
        return {
          ...prev,
          slackWebhookConfig: { ...prev.slackWebhookConfig, [key]: value },
        };
      } else if (field.startsWith('email.')) {
        const key = field.split('.')[1];
        return {
          ...prev,
          emailConfig: { ...prev.emailConfig, [key]: value },
        };
      }
      return { ...prev, [field]: value };
    });
    
    if (formErrors[field]) {
      setFormErrors(prev => ({ ...prev, [field]: undefined }));
    }
  }, [formErrors]);

  const validateForm = useCallback((): boolean => {
    const errors: Record<string, string> = {};

    if (!formData.name.trim()) {
      errors.name = 'Name is required';
    }

    if (formData.channelType === 'slack_webhook') {
      if (!formData.slackWebhookConfig.webhookUrl.trim()) {
        errors['slackWebhook.webhookUrl'] = 'Webhook URL is required';
      } else if (!formData.slackWebhookConfig.webhookUrl.startsWith('http')) {
        errors['slackWebhook.webhookUrl'] = 'Please enter a valid webhook URL';
      }
    } else if (formData.channelType === 'slack_oauth') {
      if (!formData.slackOAuthConfig.channelId) {
        errors['slackOAuth.channelId'] = 'Please select a Slack channel';
      }
    } else if (formData.channelType === 'email') {
      if (!formData.emailConfig.fromAddress.trim()) {
        errors['email.fromAddress'] = 'From address is required';
      }
      if (!formData.emailConfig.fromName.trim()) {
        errors['email.fromName'] = 'From name is required';
      }
    }

    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  }, [formData]);

  const handleSubmit = useCallback(() => {
    if (!validateForm()) return;

    let config = '';
    let type: NotificationChannelType = 'slack';

    // Build config string based on channel type
    if (formData.channelType === 'slack_webhook') {
      config = formData.slackWebhookConfig.webhookUrl;
      type = 'slack';
    } else if (formData.channelType === 'slack_oauth') {
      // For OAuth, we'll use the channel ID as config for now
      // Backend should handle the full OAuth flow
      config = formData.slackOAuthConfig.channelId || '';
      type = 'slack';
    } else if (formData.channelType === 'email') {
      // For email, serialize the config
      config = JSON.stringify(formData.emailConfig);
      type = 'email';
    }

    if (modalMode === 'create') {
      createMutation.mutate({
        name: formData.name.trim(),
        type,
        config,
      });
    } else if (editingChannelId) {
      updateMutation.mutate({
        notification_channel_id: editingChannelId,
        name: formData.name.trim(),
        type,
        config,
      });
    }
  }, [createMutation, updateMutation, formData, validateForm, modalMode, editingChannelId]);

  const handleDelete = useCallback(() => {
    if (!deletingChannel) return;
    deleteMutation.mutate({
      notification_channel_id: deletingChannel.notification_channel_id,
    });
  }, [deleteMutation, deletingChannel]);

  const isSubmitting = createMutation.isPending || updateMutation.isPending;

  // Check if Slack is connected (has channels available)
  const isSlackConnected = slackChannels && slackChannels.length > 0;

  // Loading state
  if (isLoading) {
    return (
      <Box className={classes.pageContainer}>
        <Box className={classes.pageHeader}>
          <Box className={classes.titleSection}>
            <Text className={classes.pageTitle}>Notification Channels</Text>
          </Box>
        </Box>
        <Box className={classes.channelListTable}>
          <Box className={classes.tableHeader}>
            <Box className={classes.tableHeaderContent}>
              <IconBell size={18} color="#0ba09a" />
              <Text className={classes.tableHeaderTitle}>Channels</Text>
            </Box>
          </Box>
          <Box className={classes.tableWrapper} style={{ padding: '2rem', textAlign: 'center' }}>
            <Loader size="sm" color="teal" />
          </Box>
        </Box>
      </Box>
    );
  }

  // Error state
  if (isError) {
    return (
      <Box className={classes.pageContainer}>
        <Box className={classes.pageHeader}>
          <Box className={classes.headerGroup}>
            <Box className={classes.titleSection}>
              <Text className={classes.pageTitle}>Notification Channels</Text>
            </Box>
            <Button
              leftSection={<IconRefresh size={16} />}
              onClick={() => refetch()}
              variant="light"
              color="teal"
            >
              Retry
            </Button>
          </Box>
        </Box>
        <Box className={classes.channelListTable}>
          <Box className={classes.tableHeader}>
            <Box className={classes.tableHeaderContent}>
              <IconBell size={18} color="#0ba09a" />
              <Text className={classes.tableHeaderTitle}>Channels</Text>
            </Box>
          </Box>
          <Box className={classes.tableWrapper} style={{ padding: '2rem' }}>
            <Text size="sm" c="red" ta="center">
              Failed to load notification channels. Please try again.
            </Text>
          </Box>
        </Box>
      </Box>
    );
  }

  // Check for API error in response
  if (data?.error) {
    return (
      <Box className={classes.pageContainer}>
        <Box className={classes.pageHeader}>
          <Box className={classes.headerGroup}>
            <Box className={classes.titleSection}>
              <Text className={classes.pageTitle}>Notification Channels</Text>
            </Box>
            <Button
              leftSection={<IconRefresh size={16} />}
              onClick={() => refetch()}
              variant="light"
              color="teal"
            >
              Retry
            </Button>
          </Box>
        </Box>
        <Box className={classes.channelListTable}>
          <Box className={classes.tableHeader}>
            <Box className={classes.tableHeaderContent}>
              <IconBell size={18} color="#0ba09a" />
              <Text className={classes.tableHeaderTitle}>Channels</Text>
            </Box>
          </Box>
          <Box className={classes.tableWrapper} style={{ padding: '2rem' }}>
            <Text size="sm" c="red" ta="center">
              {data.error.message || 'Failed to load notification channels. Please try again.'}
            </Text>
          </Box>
        </Box>
      </Box>
    );
  }

  return (
    <Box className={classes.pageContainer}>
      {/* Page Header */}
      <Box className={classes.pageHeader}>
        <Box className={classes.headerGroup}>
          <Box className={classes.titleSection}>
            <Text className={classes.pageTitle}>Notification Channels</Text>
          </Box>
          <Group gap="sm">
            <Tooltip label="Refresh list" withArrow>
              <ActionIcon
                variant="subtle"
                color="gray"
                onClick={() => refetch()}
              >
                <IconRefresh size={18} />
              </ActionIcon>
            </Tooltip>
            <Button
              leftSection={<IconPlus size={16} />}
              onClick={handleOpenCreateModal}
              variant="filled"
              color="teal"
            >
              Add Channel
            </Button>
          </Group>
        </Box>
      </Box>

      {/* Channels Table */}
      <Box className={`${classes.channelListTable} ${classes.fadeIn}`}>
        <Box className={classes.tableHeader}>
          <Box className={classes.tableHeaderContent}>
            <IconBell size={18} color="#0ba09a" />
            <Text className={classes.tableHeaderTitle}>Notification Channels</Text>
            <Badge size="sm" variant="light" color="teal" ml="auto">
              {channels.length} channel{channels.length !== 1 ? 's' : ''}
            </Badge>
          </Box>
        </Box>

        {channels.length === 0 ? (
          <Box className={classes.emptyState}>
            <Box className={classes.emptyStateIcon}>🔔</Box>
            <Text className={classes.emptyStateText}>No notification channels configured</Text>
            <Text size="xs" c="dimmed" mt="xs">
              Add a channel to receive alert notifications
            </Text>
            <Button
              leftSection={<IconPlus size={16} />}
              onClick={handleOpenCreateModal}
              variant="light"
              color="teal"
              mt="md"
            >
              Add Your First Channel
            </Button>
          </Box>
        ) : (
          <Box className={classes.tableWrapper}>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Name</Table.Th>
                  <Table.Th>Type</Table.Th>
                  <Table.Th>Configuration</Table.Th>
                  <Table.Th style={{ textAlign: 'right', width: 100 }}>Actions</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {channels.map((channel) => (
                  <Table.Tr key={channel.notification_channel_id}>
                    <Table.Td>
                      <Text fw={500} size="sm">
                        {channel.name}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      <span
                        className={`${classes.typeBadge} ${
                          channel.type === 'slack'
                            ? classes.typeBadgeSlack
                            : classes.typeBadgeEmail
                        }`}
                      >
                        {channel.type === 'slack' ? (
                          <IconBrandSlack size={14} />
                        ) : (
                          <IconMail size={14} />
                        )}
                        {channel.type === 'slack' ? 'Slack' : 'Email'}
                      </span>
                    </Table.Td>
                    <Table.Td>
                      <Box className={classes.configCell}>
                        <Tooltip label={channel.config} withArrow>
                          <Text className={classes.configText}>
                            {channel.config}
                          </Text>
                        </Tooltip>
                      </Box>
                    </Table.Td>
                    <Table.Td>
                      <Group gap="xs" justify="flex-end">
                        <Tooltip label="Edit channel" withArrow>
                          <ActionIcon
                            variant="subtle"
                            color="teal"
                            onClick={() => handleOpenEditModal(channel)}
                          >
                            <IconEdit size={16} />
                          </ActionIcon>
                        </Tooltip>
                        <Tooltip label="Delete channel" withArrow>
                          <ActionIcon
                            variant="subtle"
                            color="red"
                            onClick={() => handleOpenDeleteModal(channel)}
                          >
                            <IconTrash size={16} />
                          </ActionIcon>
                        </Tooltip>
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Box>
        )}
      </Box>

      {/* Create/Edit Channel Modal */}
      <Modal
        opened={formModalOpened}
        onClose={handleCloseFormModal}
        title={modalMode === 'create' ? 'Add Notification Channel' : 'Edit Notification Channel'}
        centered
        size="lg"
      >
        <Box className={classes.modalContent}>
          <Stack gap="md">
            {/* Channel Type Selector */}
            <Box>
              <Text size="sm" fw={500} mb="xs">
                Channel Type
              </Text>
              <Box className={classes.typeSelector}>
                {/* Slack OAuth */}
                <Box
                  className={`${classes.typeCard} ${
                    formData.channelType === 'slack_oauth' ? classes.typeCardSelected : ''
                  }`}
                  onClick={() => handleTypeSelect('slack_oauth')}
                >
                  <Box className={`${classes.typeCardIcon} ${classes.typeCardSlack}`}>
                    <IconBrandSlack size={20} />
                  </Box>
                  <Text size="sm" fw={500}>Slack (OAuth)</Text>
                  <Text size="xs" c="dimmed">Bot integration</Text>
                </Box>

                {/* Slack Webhook */}
                <Box
                  className={`${classes.typeCard} ${
                    formData.channelType === 'slack_webhook' ? classes.typeCardSelected : ''
                  }`}
                  onClick={() => handleTypeSelect('slack_webhook')}
                >
                  <Box className={`${classes.typeCardIcon} ${classes.typeCardSlack}`}>
                    <IconBrandSlack size={20} />
                  </Box>
                  <Text size="sm" fw={500}>Slack Webhook</Text>
                  <Text size="xs" c="dimmed">Simple webhook</Text>
                </Box>

                {/* Email */}
                <Box
                  className={`${classes.typeCard} ${
                    formData.channelType === 'email' ? classes.typeCardSelected : ''
                  }`}
                  onClick={() => handleTypeSelect('email')}
                >
                  <Box className={`${classes.typeCardIcon} ${classes.typeCardEmail}`}>
                    <IconMail size={20} />
                  </Box>
                  <Text size="sm" fw={500}>Email</Text>
                  <Text size="xs" c="dimmed">Email alerts</Text>
                </Box>
              </Box>
            </Box>

            {/* Channel Name */}
            <TextInput
              label="Channel Name"
              placeholder="e.g., Slack - #alerts-critical"
              value={formData.name}
              onChange={(e) => handleInputChange('name', e.target.value)}
              error={formErrors.name}
              required
            />

            {/* Slack OAuth Form */}
            {formData.channelType === 'slack_oauth' && (
              <Stack gap="sm">
                {!isSlackConnected ? (
                  <Alert icon={<IconAlertCircle size={16} />} color="blue" variant="light">
                    <Text size="sm" fw={500} mb="xs">Connect your Slack workspace</Text>
                    <Text size="xs" c="dimmed" mb="md">
                      Click below to authorize Pulse to post messages to your Slack workspace
                    </Text>
                    <Button
                      leftSection={<IconBrandSlack size={16} />}
                      rightSection={<IconExternalLink size={14} />}
                      onClick={handleConnectSlack}
                      loading={slackConnecting || isLoadingInstallUrl}
                      variant="light"
                      color="blue"
                      fullWidth
                    >
                      Connect to Slack
                    </Button>
                  </Alert>
                ) : (
                  <>
                    <Alert icon={<IconCheck size={16} />} color="teal" variant="light">
                      <Text size="sm">Slack workspace connected successfully!</Text>
                    </Alert>

                    <Select
                      label="Slack Channel"
                      placeholder="Select a channel"
                      data={slackChannels.map((ch: SlackChannelListDto) => ({
                        value: ch.id,
                        label: `${ch.isPrivate ? '🔒' : '#'} ${ch.name}${ch.isMember ? '' : ' (not a member)'}`,
                      }))}
                      value={formData.slackOAuthConfig.channelId}
                      onChange={(value) => {
                        const channel = slackChannels.find((ch: SlackChannelListDto) => ch.id === value);
                        handleInputChange('slackOAuth.channelId', value || '');
                        if (channel) {
                          handleInputChange('slackOAuth.channelName', channel.name);
                        }
                      }}
                      error={formErrors['slackOAuth.channelId']}
                      required
                      searchable
                    />

                    <Group grow>
                      <TextInput
                        label="Bot Name"
                        placeholder="PulseBot"
                        value={formData.slackOAuthConfig.botName}
                        onChange={(e) => handleInputChange('slackOAuth.botName', e.target.value)}
                      />
                      <TextInput
                        label="Icon Emoji"
                        placeholder=":bell:"
                        value={formData.slackOAuthConfig.iconEmoji}
                        onChange={(e) => handleInputChange('slackOAuth.iconEmoji', e.target.value)}
                      />
                    </Group>
                  </>
                )}
              </Stack>
            )}

            {/* Slack Webhook Form */}
            {formData.channelType === 'slack_webhook' && (
              <Stack gap="sm">
                <TextInput
                  label="Webhook URL"
                  placeholder="https://hooks.slack.com/services/..."
                  value={formData.slackWebhookConfig.webhookUrl}
                  onChange={(e) => handleInputChange('slackWebhook.webhookUrl', e.target.value)}
                  error={formErrors['slackWebhook.webhookUrl']}
                  required
                  leftSection={<IconBrandSlack size={16} />}
                />
                <Group grow>
                  <TextInput
                    label="Bot Name"
                    placeholder="PulseBot"
                    value={formData.slackWebhookConfig.botName}
                    onChange={(e) => handleInputChange('slackWebhook.botName', e.target.value)}
                  />
                  <TextInput
                    label="Icon Emoji"
                    placeholder=":bell:"
                    value={formData.slackWebhookConfig.iconEmoji}
                    onChange={(e) => handleInputChange('slackWebhook.iconEmoji', e.target.value)}
                  />
                </Group>
              </Stack>
            )}

            {/* Email Form */}
            {formData.channelType === 'email' && (
              <Stack gap="sm">
                <Group grow>
                  <TextInput
                    label="From Address"
                    placeholder="noreply@example.com"
                    value={formData.emailConfig.fromAddress}
                    onChange={(e) => handleInputChange('email.fromAddress', e.target.value)}
                    error={formErrors['email.fromAddress']}
                    required
                    leftSection={<IconMail size={16} />}
                  />
                  <TextInput
                    label="From Name"
                    placeholder="Pulse Notifications"
                    value={formData.emailConfig.fromName}
                    onChange={(e) => handleInputChange('email.fromName', e.target.value)}
                    error={formErrors['email.fromName']}
                    required
                  />
                </Group>
                <TextInput
                  label="Reply-To Address"
                  placeholder="support@example.com"
                  value={formData.emailConfig.replyToAddress}
                  onChange={(e) => handleInputChange('email.replyToAddress', e.target.value)}
                />
                <TextInput
                  label="Configuration Set Name"
                  placeholder="pulse-prod"
                  value={formData.emailConfig.configurationSetName}
                  onChange={(e) => handleInputChange('email.configurationSetName', e.target.value)}
                />
              </Stack>
            )}
          </Stack>

          <Box className={classes.modalActions}>
            <Button variant="default" onClick={handleCloseFormModal}>
              Cancel
            </Button>
            <Button
              color="teal"
              onClick={handleSubmit}
              loading={isSubmitting}
              disabled={formData.channelType === 'slack_oauth' && !isSlackConnected}
            >
              {modalMode === 'create' ? 'Create Channel' : 'Save Changes'}
            </Button>
          </Box>
        </Box>
      </Modal>

      {/* Delete Confirmation Modal */}
      <Modal
        opened={deleteModalOpened}
        onClose={handleCloseDeleteModal}
        title="Delete Notification Channel"
        centered
        size="sm"
      >
        <Box className={classes.modalContent}>
          <Text size="sm" c="dimmed" mb="md">
            Are you sure you want to delete <strong>{deletingChannel?.name}</strong>? 
            This action cannot be undone.
          </Text>
          <Text size="xs" c="red" mb="lg">
            Any alerts using this channel will need to be updated with a new notification channel.
          </Text>
          <Box className={classes.modalActions}>
            <Button variant="default" onClick={handleCloseDeleteModal}>
              Cancel
            </Button>
            <Button
              color="red"
              onClick={handleDelete}
              loading={deleteMutation.isPending}
            >
              Delete Channel
            </Button>
          </Box>
        </Box>
      </Modal>
    </Box>
  );
}
