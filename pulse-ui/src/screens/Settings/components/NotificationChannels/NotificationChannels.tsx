/**
 * Notification Channels Management Component
 * Displays list of notification channels with full CRUD functionality
 * Follows the design patterns from ConfigVersionList
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
} from '@tabler/icons-react';
import { useGetAlertNotificationChannels } from '../../../../hooks/useGetAlertNotificationChannels';
import { useCreateNotificationChannel } from '../../../../hooks/useCreateNotificationChannel';
import { useUpdateNotificationChannel } from '../../../../hooks/useUpdateNotificationChannel';
import { useDeleteNotificationChannel } from '../../../../hooks/useDeleteNotificationChannel';
import { 
  NotificationChannelType,
  AlertNotificationChannelItem 
} from '../../../../hooks/useGetAlertNotificationChannels/useGetAlertNotificationChannels.interface';
import { showNotification } from '../../../../helpers/showNotification';
import { useMantineTheme } from '@mantine/core';
import { COMMON_CONSTANTS } from '../../../../constants';
import classes from './NotificationChannels.module.css';

type FormData = {
  name: string;
  type: NotificationChannelType;
  config: string;
};

type ModalMode = 'create' | 'edit';

const initialFormData: FormData = {
  name: '',
  type: 'slack',
  config: '',
};

export function NotificationChannels() {
  const theme = useMantineTheme();
  
  // Modal states
  const [formModalOpened, { open: openFormModal, close: closeFormModal }] = useDisclosure(false);
  const [deleteModalOpened, { open: openDeleteModal, close: closeDeleteModal }] = useDisclosure(false);
  
  // Form state
  const [modalMode, setModalMode] = useState<ModalMode>('create');
  const [formData, setFormData] = useState<FormData>(initialFormData);
  const [formErrors, setFormErrors] = useState<Partial<Record<keyof FormData, string>>>({});
  const [editingChannelId, setEditingChannelId] = useState<number | null>(null);
  const [deletingChannel, setDeletingChannel] = useState<AlertNotificationChannelItem | null>(null);

  // API hooks
  const { data, isLoading, isError, refetch } = useGetAlertNotificationChannels();
  const channels = data?.data ?? [];

  // Close form modal helper
  const handleCloseFormModal = useCallback(() => {
    closeFormModal();
    setFormData(initialFormData);
    setFormErrors({});
    setEditingChannelId(null);
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
    setFormData({
      name: channel.name,
      type: channel.type,
      config: channel.config,
    });
    setFormErrors({});
    setEditingChannelId(channel.notification_channel_id);
    openFormModal();
  }, [openFormModal]);

  const handleOpenDeleteModal = useCallback((channel: AlertNotificationChannelItem) => {
    setDeletingChannel(channel);
    openDeleteModal();
  }, [openDeleteModal]);

  // Form handlers
  const handleTypeSelect = useCallback((type: NotificationChannelType) => {
    setFormData(prev => ({ ...prev, type, config: '' }));
    setFormErrors(prev => ({ ...prev, config: undefined }));
  }, []);

  const handleInputChange = useCallback((field: keyof FormData, value: string) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    if (formErrors[field]) {
      setFormErrors(prev => ({ ...prev, [field]: undefined }));
    }
  }, [formErrors]);

  const validateForm = useCallback((): boolean => {
    const errors: Partial<Record<keyof FormData, string>> = {};

    if (!formData.name.trim()) {
      errors.name = 'Name is required';
    }

    if (!formData.config.trim()) {
      errors.config = 'Webhook URL is required';
    } else if (!formData.config.startsWith('http')) {
      errors.config = 'Please enter a valid webhook URL';
    }

    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  }, [formData]);

  const handleSubmit = useCallback(() => {
    if (!validateForm()) return;

    if (modalMode === 'create') {
      createMutation.mutate({
        name: formData.name.trim(),
        type: formData.type,
        config: formData.config.trim(),
      });
    } else if (editingChannelId) {
      updateMutation.mutate({
        notification_channel_id: editingChannelId,
        name: formData.name.trim(),
        type: formData.type,
        config: formData.config.trim(),
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
                <Box
                  className={`${classes.typeCard} ${classes.typeCardSelected}`}
                  onClick={() => handleTypeSelect('slack')}
                >
                  <Box className={`${classes.typeCardIcon} ${classes.typeCardSlack}`}>
                    <IconBrandSlack size={20} />
                  </Box>
                  <Text size="sm" fw={500}>Slack</Text>
                  <Text size="xs" c="dimmed">Webhook integration</Text>
                </Box>
                <Tooltip label="Email alerts are not available yet — use Slack in the meantime" withArrow>
                  <Box className={`${classes.typeCard} ${classes.typeCardDisabled}`}>
                    <Box className={`${classes.typeCardIcon} ${classes.typeCardEmail}`}>
                      <IconMail size={20} />
                    </Box>
                    <Group gap={4}>
                      <Text size="sm" fw={500} c="dimmed">Email</Text>
                      <Badge size="xs" variant="light" color="gray">Soon</Badge>
                    </Group>
                    <Text size="xs" c="dimmed">Direct email alerts</Text>
                  </Box>
                </Tooltip>
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

            {/* Slack Webhook URL */}
            <TextInput
              label="Webhook URL"
              placeholder="https://hooks.slack.com/services/..."
              value={formData.config}
              onChange={(e) => handleInputChange('config', e.target.value)}
              error={formErrors.config}
              required
              leftSection={<IconBrandSlack size={16} />}
            />
          </Stack>

          <Box className={classes.modalActions}>
            <Button variant="default" onClick={handleCloseFormModal}>
              Cancel
            </Button>
            <Button
              color="teal"
              onClick={handleSubmit}
              loading={isSubmitting}
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
