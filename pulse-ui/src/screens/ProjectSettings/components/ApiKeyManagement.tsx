import { useState, useEffect } from 'react';
import { Stack, Title, Text, Card, Group, Button, Code, CopyButton, ActionIcon, Tooltip, Loader, Alert } from '@mantine/core';
import { IconCopy, IconCheck, IconRefresh, IconPlus, IconInfoCircle } from '@tabler/icons-react';
import { notifications } from '@mantine/notifications';
import { useProjectContext } from '../../../contexts';
import { getProjectApiKey } from '../../../helpers/getProjectApiKey';
import { makeRequest } from '../../../helpers/makeRequest';
import { API_BASE_URL } from '../../../constants';

// Type definitions for API responses
interface ApiKeyRestResponse {
  apiKeyId: number;
  projectId: string;
  displayName: string;
  apiKey: string;
  isActive: boolean;
  expiresAt: string | null;
  gracePeriodEndsAt: string | null;
  createdBy: string;
  createdAt: string;
  deactivatedAt: string | null;
  deactivatedBy: string | null;
  deactivationReason: string | null;
}

interface ApiKeyListResponse {
  apiKeys: ApiKeyRestResponse[];
  count: number;
}

interface CreateApiKeyResponse {
  apiKeyId: number;
  projectId: string;
  displayName: string;
  apiKey: string;
  expiresAt: string | null;
  createdAt: string;
}

export function ApiKeyManagement() {
  const [apiKey, setApiKey] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [isDummyKey, setIsDummyKey] = useState(false);
  const { projectId, projectName } = useProjectContext();

  useEffect(() => {
    fetchApiKey();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const fetchApiKey = async () => {
    // Handle missing project context
    if (!projectId) {
      console.warn('[ApiKeyManagement] No project ID available');
      setLoading(false);
      return;
    }
    
    setLoading(true);
    try {
      const result = await getProjectApiKey(projectId);
      setApiKey(result.key);
      setIsDummyKey(result.isDummy);
      
      if (result.isDummy) {
        console.log('[ApiKeyManagement] Using dummy development key');
      }
    } catch (error) {
      console.error('[ApiKeyManagement] Failed to fetch API key:', error);
      notifications.show({
        title: 'Error',
        message: 'Failed to fetch API key. Please try again.',
        color: 'red',
      });
    } finally {
      setLoading(false);
    }
  };

  const handleGenerateOrRegenerateKey = async (isRegenerate: boolean) => {
    if (!projectId) {
      notifications.show({
        title: 'Error',
        message: 'Project ID not available',
        color: 'red',
      });
      return;
    }
    
    setGenerating(true);
    
    try {
      let currentDisplayName = 'Default Key';
      
      // Step 1: If regenerating, revoke old key first
      if (isRegenerate && apiKey) {
        console.log('[ApiKeyManagement] Regenerating: fetching current keys');
        
        // Get current active key to find its ID and display name
        const listResponse = await makeRequest<ApiKeyListResponse>({
          url: `${API_BASE_URL}/v1/projects/${projectId}/api-keys`,
          init: { method: 'GET' },
        });
        
        if (listResponse.data?.apiKeys) {
          const activeKey = listResponse.data.apiKeys.find((k) => k.isActive);
          
          if (activeKey) {
            console.log('[ApiKeyManagement] Revoking old key:', activeKey.apiKeyId);
            currentDisplayName = activeKey.displayName || 'Default Key';
            
            // Revoke the old key with 0-day grace period (immediate revocation)
            await makeRequest({
              url: `${API_BASE_URL}/v1/projects/${projectId}/api-keys/${activeKey.apiKeyId}`,
              init: {
                method: 'DELETE',
                body: JSON.stringify({ gracePeriodDays: 0 })
              },
            });
            
            console.log('[ApiKeyManagement] Old key revoked successfully');
          }
        }
      }
      
      // Step 2: Create new key
      console.log('[ApiKeyManagement] Creating new API key');
      const createResponse = await makeRequest<CreateApiKeyResponse>({
        url: `${API_BASE_URL}/v1/projects/${projectId}/api-keys`,
        init: {
          method: 'POST',
          body: JSON.stringify({
            displayName: currentDisplayName,
            expiresAt: null  // null means never expires
          })
        },
      });
      
      // Step 3: Handle response
      if (createResponse.data?.apiKey) {
        setApiKey(createResponse.data.apiKey);
        setIsDummyKey(false);
        
        notifications.show({
          title: 'Success',
          message: isRegenerate 
            ? 'API key regenerated successfully' 
            : 'API key generated successfully',
          color: 'green',
          icon: <IconCheck size={18} />,
        });
      } else {
        throw new Error(createResponse.error?.message || 'Failed to create API key');
      }
      
    } catch (error) {
      console.error('[ApiKeyManagement] Error during generate/regenerate:', error);
      notifications.show({
        title: 'Error',
        message: error instanceof Error ? error.message : 'An unexpected error occurred',
        color: 'red',
      });
    } finally {
      setGenerating(false);
    }
  };

  // Show error if no project ID
  if (!projectId) {
    return (
      <Stack gap="lg">
        <Alert color="red" title="No Project Selected" icon={<IconInfoCircle />}>
          Please select a project to manage API keys.
        </Alert>
      </Stack>
    );
  }

  // Show loading state
  if (loading) {
    return (
      <Stack align="center" gap="md" py="xl">
        <Loader size="lg" />
        <Text c="dimmed">Loading API key...</Text>
      </Stack>
    );
  }

  return (
    <Stack gap="lg">
      <div>
        <Title order={2}>API Key Management</Title>
        <Text c="dimmed" size="sm">
          Use this API key to authenticate SDK and API requests for {projectName}
        </Text>
      </div>

      {/* Show info banner when using dummy key */}
      {isDummyKey && (
        <Alert color="blue" title="Development Mode" icon={<IconInfoCircle />}>
          Using a development API key. The API key management endpoint is being developed by your team.
          This key can be used for local testing.
        </Alert>
      )}

      <Card shadow="sm" padding="lg" radius="md" withBorder>
        <Stack gap="md">
          <Group justify="space-between">
            <Text fw={500}>Project API Key</Text>
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
                color="blue"
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
                onClick={() => handleGenerateOrRegenerateKey(true)}
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
      </Card>
    </Stack>
  );
}
