import { useState } from 'react';
import { Stack, Text, Box, Group, Button, Code, CopyButton, ActionIcon, Tooltip, Loader, Alert } from '@mantine/core';
import { IconCopy, IconCheck, IconRefresh, IconPlus, IconInfoCircle, IconKey } from '@tabler/icons-react';
import { notifications } from '@mantine/notifications';
import { useProjectContext } from '../../../contexts';
import { ConfirmationModal } from '../../../components/ConfirmationModal';
import { useProjectApiKey, useRegenerateProjectApiKey } from '../../../hooks';
import classes from './ApiKeyManagement.module.css';

export function ApiKeyManagement() {
  const [confirmRegenerateOpen, setConfirmRegenerateOpen] = useState(false);
  const { projectId } = useProjectContext();

  const { data: apiKeyData, isLoading } = useProjectApiKey(projectId ?? '');
  const regenerateMutation = useRegenerateProjectApiKey();

  const apiKey = apiKeyData?.key ?? null;
  const isDummyKey = apiKeyData?.isDummy ?? false;
  const generating = regenerateMutation.isPending;

  const handleGenerateOrRegenerateKey = (isRegenerate: boolean) => {
    if (!projectId) {
      notifications.show({
        title: 'Error',
        message: 'Project ID not available',
        color: 'red',
      });
      return;
    }

    regenerateMutation.mutate(
      { projectId, isRegenerate },
      {
        onSuccess: (response) => {
          if (response?.data) {
            notifications.show({
              title: 'Success',
              message: isRegenerate
                ? 'API key regenerated successfully'
                : 'API key generated successfully',
              color: 'green',
              icon: <IconCheck size={18} />,
            });
          }
        },
        onError: (error) => {
          notifications.show({
            title: 'Error',
            message: error instanceof Error ? error.message : 'An unexpected error occurred',
            color: 'red',
          });
        },
      }
    );
  };

  // Show error if no project ID
  if (!projectId) {
    return (
      <Box className={classes.pageContainer}>
        <Box className={classes.pageHeader}>
          <Box className={classes.titleSection}>
            <Text className={classes.pageTitle}>API Key Management</Text>
          </Box>
        </Box>
        <Alert color="red" title="No Project Selected" icon={<IconInfoCircle />}>
          Please select a project to manage API keys.
        </Alert>
      </Box>
    );
  }

  // Show loading state
  if (isLoading) {
    return (
      <Box className={classes.pageContainer}>
        <Box className={classes.pageHeader}>
          <Box className={classes.titleSection}>
            <Text className={classes.pageTitle}>API Key Management</Text>
          </Box>
        </Box>
        <Box className={classes.contentTable}>
          <Box className={classes.tableHeader}>
            <Box className={classes.tableHeaderContent}>
              <IconKey size={18} color="#0ba09a" />
              <Text className={classes.tableHeaderTitle}>Project API Key</Text>
            </Box>
          </Box>
          <Box className={classes.tableWrapper} style={{ textAlign: 'center' }}>
            <Loader size="lg" />
            <Text c="dimmed" mt="md">Loading API key...</Text>
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
            <Text className={classes.pageTitle}>API Key Management</Text>
          </Box>
        </Box>
      </Box>

      {/* Show info banner when using dummy key */}
      {isDummyKey && (
        <Alert color="blue" title="Development Mode" icon={<IconInfoCircle />} mb="lg">
          Using a development API key. The API key management endpoint is being developed by your team.
          This key can be used for local testing.
        </Alert>
      )}

      {/* API Key Content */}
      <Box className={classes.contentTable}>
        <Box className={classes.tableHeader}>
          <Box className={classes.tableHeaderContent}>
            <IconKey size={18} color="#0ba09a" />
            <Text className={classes.tableHeaderTitle}>Project API Key</Text>
          </Box>
        </Box>
        <Box className={classes.tableWrapper}>
          <Stack gap="md">
            <Group justify="space-between">
              <Text fw={500}>API Key</Text>
              {apiKey && (
                <CopyButton value={apiKey}>
                  {({ copied, copy }) => (
                    <Tooltip label={copied ? 'Copied' : 'Copy'}>
                      <ActionIcon color={copied ? 'teal' : 'gray'} onClick={copy}>
                        {copied ? <IconCheck size={16} /> : <IconCopy size={16} />}
                      </ActionIcon>
                    </Tooltip>
                  )}
                </CopyButton>
              )}
            </Group>
            
            {apiKey && (
              <Code block>{apiKey}</Code>
            )}
            
            {!apiKey && (
              <Text c="dimmed" size="sm">No API key found for this project. Generate one to get started.</Text>
            )}
            
            <Group gap="sm">
              {!apiKey ? (
                // Show "Generate Key" button when no key exists
                <Button
                  leftSection={<IconPlus size={16} />}
                  variant="filled"
                  color="teal"
                  onClick={() => handleGenerateOrRegenerateKey(false)}
                  loading={generating}
                >
                  {generating ? 'Generating...' : 'Generate Key'}
                </Button>
              ) : (
                // Show "Regenerate Key" button when key exists
                <Button
                  leftSection={<IconRefresh size={16} />}
                  variant="light"
                  color="orange"
                  onClick={() => setConfirmRegenerateOpen(true)}
                  loading={generating}
                >
                  {generating ? 'Regenerating...' : 'Regenerate Key'}
                </Button>
              )}
            </Group>
            
            {apiKey && (
              <Text size="xs" c="dimmed">
                Warning: Regenerating the key will invalidate the old key immediately.
              </Text>
            )}
          </Stack>
        </Box>
      </Box>

      <ConfirmationModal
        opened={confirmRegenerateOpen}
        onClose={() => setConfirmRegenerateOpen(false)}
        onConfirm={() => {
          handleGenerateOrRegenerateKey(true);
          setConfirmRegenerateOpen(false);
        }}
        title="Regenerate API Key?"
        message="This will immediately invalidate your current API key. All applications using the old key will stop working. Are you sure you want to continue?"
        confirmLabel="Yes, Regenerate"
        cancelLabel="Cancel"
        confirmColor="orange"
        loading={generating}
        severity="danger"
      />
    </Box>
  );
}
