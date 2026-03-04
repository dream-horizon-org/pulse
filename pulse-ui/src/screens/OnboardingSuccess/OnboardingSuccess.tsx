import { useState, useEffect } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Container,
  Text,
  Button,
  Stack,
  Group,
  Paper,
  ActionIcon,
  Tabs,
  Code,
  TextInput,
  Select,
  Badge,
} from '@mantine/core';
import {
  IconCheck,
  IconCopy,
  IconEye,
  IconEyeOff,
  IconRocket,
  IconKey,
  IconUsers,
  IconCode,
} from '@tabler/icons-react';
import { showNotification } from '../../helpers/showNotification';
import { getProjectApiKey } from '../../helpers/getProjectApiKey';
import { useProjectContext } from '../../contexts';
import { makeRequest } from '../../helpers/makeRequest';
import { API_BASE_URL } from '../../constants';
import classes from './OnboardingSuccess.module.css';

export function OnboardingSuccess() {
  const { projectId: urlProjectId } = useParams<{ projectId: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const locationState = location.state || {};
  const projectId = urlProjectId; // Get from URL params
  
  // Get project info from context
  const { projectName: contextProjectName, projectId: contextProjectId } = useProjectContext();

  const [projectName, setProjectName] = useState<string | null>(locationState.projectName || contextProjectName || null);
  const [projectApiKey, setProjectApiKey] = useState<string | null>(locationState.projectApiKey || null);
  const [loading, setLoading] = useState(!locationState.projectName || !locationState.projectApiKey);
  
  const [showApiKey, setShowApiKey] = useState(false);
  const [copiedKey, setCopiedKey] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState('viewer');
  const [inviting, setInviting] = useState(false);

  // Fetch project details if not in location.state
  useEffect(() => {
    const fetchProjectDetails = async () => {
      if (!projectId) {
        navigate('/project-selection', { replace: true });
        return;
      }

      // If we already have data from location.state, no need to fetch
      if (projectName && projectApiKey) {
        setLoading(false);
        return;
      }

      console.log('[OnboardingSuccess] Fetching project details from API');
      setLoading(true);

      try {
        // Get project name from React Context
        if (contextProjectName && contextProjectId === projectId) {
          console.log('[OnboardingSuccess] Using project name from context:', contextProjectName);
          setProjectName(contextProjectName);
        } else if (!projectName) {
          // If context doesn't have the project name and it's not in state, redirect to project dashboard
          console.log('[OnboardingSuccess] No project context, redirecting to dashboard');
          navigate(`/projects/${projectId}`, { replace: true });
          return;
        }

        // Fetch API key using shared helper
        const apiKeyResult = await getProjectApiKey(projectId);
        setProjectApiKey(apiKeyResult.key);
      } catch (error) {
        console.error('[OnboardingSuccess] Error fetching project details:', error);
        // On error, redirect to project dashboard
        navigate(`/projects/${projectId}`, { replace: true });
      } finally {
        setLoading(false);
      }
    };

    fetchProjectDetails();
  }, [projectId, projectName, projectApiKey, navigate, contextProjectName, contextProjectId]);

  // Show loading while fetching
  if (loading || !projectId || !projectName || !projectApiKey) {
    return null;
  }

  const handleCopyKey = () => {
    navigator.clipboard.writeText(projectApiKey);
    setCopiedKey(true);
    showNotification(
      'Success',
      'API key copied to clipboard',
      <IconCheck />,
      '#0ec9c2'
    );
    setTimeout(() => setCopiedKey(false), 2000);
  };

  const handleInviteMember = async () => {
    if (!inviteEmail.trim() || !projectId) return;
    
    setInviting(true);
    try {
      const response = await makeRequest<any>({
        url: `${API_BASE_URL}/v1/projects/${projectId}/members`,
        init: {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            email: inviteEmail.trim(),
            role: inviteRole,
          }),
        },
      });
      
      if (response.data) {
        showNotification('Success', `Invitation sent to ${inviteEmail}`, <IconUsers />, '#0ec9c2');
        setInviteEmail('');
      } else {
        showNotification('Error', response.error?.message || 'Failed to invite member', <IconUsers />, '#fa5252');
      }
    } catch (error: any) {
      showNotification('Error', error.message || 'Failed to invite member', <IconUsers />, '#fa5252');
    } finally {
      setInviting(false);
    }
  };

  const handleGoToDashboard = () => {
    navigate(`/projects/${projectId}`);
  };

  const maskApiKey = (key: string) => {
    if (!key) return '';
    return `${key.substring(0, 8)}${'•'.repeat(24)}${key.substring(key.length - 4)}`;
  };

  const androidCode = `// Initialize Pulse SDK in your Application class
import com.dreamhorizon.pulse.Pulse

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        Pulse.initialize(
            context = this,
            apiKey = "${projectApiKey}"
        )
    }
}`;

  const iosCode = `// Initialize Pulse SDK in AppDelegate
import Pulse

func application(_ application: UIApplication,
                didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
    
    Pulse.initialize(apiKey: "${projectApiKey}")
    
    return true
}`;

  const reactNativeCode = `// Initialize Pulse SDK in your App.tsx or index.js
import { Pulse } from '@dreamhorizon/pulse-react-native';

Pulse.initialize({
  apiKey: '${projectApiKey}',
});`;

  return (
    <Box className={classes.container}>
      <Container size="lg" className={classes.mainContainer}>
        <Stack gap="xl">
          {/* Success Header */}
          <Box className={classes.successHeader}>
            <Box className={classes.iconWrapper}>
              <IconRocket size={48} className={classes.successIcon} />
            </Box>
            <Text className={classes.title}>
              🎉 Project "{projectName}" Created Successfully!
            </Text>
            {/* <Text className={classes.subtitle}>
              Your analytics platform is ready. Follow the steps below to integrate Pulse into your app.
            </Text> */}
          </Box>

          {/* API Key Section */}
          <Paper className={classes.section} shadow="sm" p="xl" radius="md">
            <Group mb="md">
              <IconKey size={24} style={{ color: '#0ec9c2' }} />
              <Text fw={600} size="lg">SDK Initialization</Text>
            </Group>
            <Text size="sm" c="dimmed" mb="md">
              Use this API key to initialize the Pulse SDK in your application. Keep it secure!
            </Text>
            
            <Group gap="xs" className={classes.apiKeyGroup}>
              <Code className={classes.apiKeyDisplay}>
                {showApiKey ? projectApiKey : maskApiKey(projectApiKey)}
              </Code>
              <ActionIcon
                variant="subtle"
                onClick={() => setShowApiKey(!showApiKey)}
                title={showApiKey ? 'Hide API key' : 'Show API key'}
              >
                {showApiKey ? <IconEyeOff size={18} /> : <IconEye size={18} />}
              </ActionIcon>
              <ActionIcon
                variant="filled"
                color="teal"
                onClick={handleCopyKey}
                title="Copy API key"
              >
                {copiedKey ? <IconCheck size={18} /> : <IconCopy size={18} />}
              </ActionIcon>
            </Group>
          </Paper>

          {/* Quick Start Code Section */}
          <Paper className={classes.section} shadow="sm" p="xl" radius="md">
            <Group mb="md">
              <IconCode size={24} style={{ color: '#0ec9c2' }} />
              <Text fw={600} size="lg">Quick Start</Text>
            </Group>
            <Text size="sm" c="dimmed" mb="md">
              Choose your platform and copy the initialization code:
            </Text>

            <Tabs defaultValue="android" className={classes.tabs}>
              <Tabs.List>
                <Tabs.Tab value="android">Android</Tabs.Tab>
                <Tabs.Tab value="ios">iOS</Tabs.Tab>
                <Tabs.Tab value="react-native">React Native</Tabs.Tab>
              </Tabs.List>

              <Tabs.Panel value="android" pt="md">
                <Box className={classes.codeBlock}>
                  <pre className={classes.codeContent}>{androidCode}</pre>
                </Box>
              </Tabs.Panel>

              <Tabs.Panel value="ios" pt="md">
                <Box className={classes.codeBlock}>
                  <pre className={classes.codeContent}>{iosCode}</pre>
                </Box>
              </Tabs.Panel>

              <Tabs.Panel value="react-native" pt="md">
                <Box className={classes.codeBlock}>
                  <pre className={classes.codeContent}>{reactNativeCode}</pre>
                </Box>
              </Tabs.Panel>
            </Tabs>
          </Paper>

          {/* Invite Team Section */}
          <Paper className={classes.section} shadow="sm" p="xl" radius="md">
            <Group mb="md">
              <IconUsers size={24} style={{ color: '#0ec9c2' }} />
              <Text fw={600} size="lg">Invite Your Team</Text>
              <Badge variant="light" color="blue" size="sm">Optional</Badge>
            </Group>
            <Text size="sm" c="dimmed" mb="md">
              Collaborate with your team by inviting members to this project.
            </Text>

            <Group align="flex-end">
              <TextInput
                placeholder="teammate@example.com"
                label="Email Address"
                value={inviteEmail}
                onChange={(e) => setInviteEmail(e.currentTarget.value)}
                style={{ flex: 1 }}
              />
              <Select
                label="Role"
                value={inviteRole}
                onChange={(value) => setInviteRole(value || 'viewer')}
                data={[
                  { value: 'admin', label: 'Admin' },
                  { value: 'editor', label: 'Editor' },
                  { value: 'viewer', label: 'Viewer' },
                ]}
                style={{ width: 150 }}
              />
              <Button
                variant="light"
                color="teal"
                onClick={handleInviteMember}
                disabled={!inviteEmail || inviting}
                loading={inviting}
              >
                Send Invite
              </Button>
            </Group>
          </Paper>

          {/* CTA Button */}
          <Group justify="center" mt="xl">
            <Button
              size="lg"
              className={classes.ctaButton}
              onClick={handleGoToDashboard}
              leftSection={<IconRocket size={20} />}
            >
              Go to Dashboard
            </Button>
          </Group>
        </Stack>
      </Container>
    </Box>
  );
}
