import { useState, useEffect } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
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
} from "@mantine/core";
import {
  IconCheck,
  IconCopy,
  IconEye,
  IconEyeOff,
  IconRocket,
  IconKey,
  IconUsers,
  IconCode,
} from "@tabler/icons-react";
import { showNotification } from "../../helpers/showNotification";
import { getProjectApiKey } from "../../helpers/getProjectApiKey";
import { useProjectContext } from "../../contexts";
import { ROUTES } from "../../constants";
import { useInviteProjectMember } from "../../hooks";
import { ApiResponse } from "../../helpers/makeRequest";
import { ProjectMember } from "../../types/members";
import {
  PROJECT_ROLES,
  PROJECT_ROLE_LABELS,
  ProjectRole,
} from "../../constants/Roles";
import classes from "./OnboardingSuccess.module.css";

export function OnboardingSuccess() {
  const { projectId: urlProjectId } = useParams<{ projectId: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const locationState = location.state || {};
  const projectId = urlProjectId;

  // Get project info from context
  const { projectName: contextProjectName, projectId: contextProjectId } =
    useProjectContext();

  const [projectName, setProjectName] = useState<string | null>(
    locationState.projectName || contextProjectName || null,
  );
  const [projectApiKey, setProjectApiKey] = useState<string | null>(
    locationState.projectApiKey || null,
  );
  const [loading, setLoading] = useState(
    !locationState.projectName || !locationState.projectApiKey,
  );

  const [showApiKey, setShowApiKey] = useState(false);
  const [copiedKey, setCopiedKey] = useState(false);
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<ProjectRole>(
    PROJECT_ROLES.VIEWER,
  );

  // React Query hook for inviting members
  const inviteMutation = useInviteProjectMember();
  const inviting = inviteMutation.isPending;

  // Fetch project details if not in location.state
  useEffect(() => {
    const fetchProjectDetails = async () => {
      if (!projectId) {
        // Get tenant ID from cookies and redirect to organization projects
        const tenantId = document.cookie
          .split("; ")
          .find((row) => row.startsWith("tenantId="))
          ?.split("=")[1];

        if (tenantId && tenantId !== "undefined") {
          navigate(`/${tenantId}/projects`, { replace: true });
        } else {
          navigate("/", { replace: true });
        }
        return;
      }

      // If we already have data from location.state, no need to fetch
      if (projectName && projectApiKey) {
        setLoading(false);
        return;
      }

      setLoading(true);

      try {
        // Get project name from React Context
        if (contextProjectName && contextProjectId === projectId) {
          setProjectName(contextProjectName);
        } else if (!projectName) {
          // If context doesn't have the project name and it's not in state, redirect to project dashboard
          navigate(
            ROUTES.PROJECT_DASHBOARD.basePath.replace(":projectId", projectId),
            { replace: true },
          );
          return;
        }

        // Fetch API key using shared helper
        const apiKeyResult = await getProjectApiKey(projectId);
        setProjectApiKey(apiKeyResult.key);
      } catch (error) {
        console.error(
          "[OnboardingSuccess] Error fetching project details:",
          error,
        );
        // On error, redirect to project dashboard
        navigate(
          ROUTES.PROJECT_DASHBOARD.basePath.replace(":projectId", projectId),
          { replace: true },
        );
      } finally {
        setLoading(false);
      }
    };

    fetchProjectDetails();
  }, [
    projectId,
    projectName,
    projectApiKey,
    navigate,
    contextProjectName,
    contextProjectId,
  ]);

  // Show loading while fetching
  if (loading || !projectId || !projectName || !projectApiKey) {
    return null;
  }

  const handleCopyKey = () => {
    navigator.clipboard.writeText(projectApiKey);
    setCopiedKey(true);
    showNotification(
      "Success",
      "API key copied to clipboard",
      <IconCheck />,
      "#0ec9c2",
    );
    setTimeout(() => setCopiedKey(false), 2000);
  };

  const handleInviteMember = () => {
    if (!inviteEmail.trim() || !projectId) return;

    inviteMutation.mutate(
      {
        projectId,
        email: inviteEmail.trim(),
        role: inviteRole,
      },
      {
        onSuccess: (response: ApiResponse<ProjectMember>) => {
          if (response?.data && !response?.error) {
            showNotification(
              "Success",
              `Invitation sent to ${inviteEmail}`,
              <IconUsers />,
              "#0ec9c2",
            );
            setInviteEmail("");
          } else {
            showNotification(
              "Error",
              response?.error?.message || "Failed to invite member",
              <IconUsers />,
              "#fa5252",
            );
          }
        },
        onError: (error: any) => {
          showNotification(
            "Error",
            error.message || "Failed to invite member",
            <IconUsers />,
            "#fa5252",
          );
        },
      },
    );
  };

  const handleGoToDashboard = () => {
    if (!projectId) return;
    navigate(
      ROUTES.PROJECT_DASHBOARD.basePath.replace(":projectId", projectId),
    );
  };

  const maskApiKey = (key: string) => {
    if (!key) return "";
    return `${key.substring(0, 8)}${"•".repeat(24)}${key.substring(key.length - 4)}`;
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
            <Text
              className={classes.title}
              ta="center"
              c="white"
              fw={700}
              size="32px"
            >
              🎉 Project "{projectName}" Created Successfully!
            </Text>
            <Text
              className={classes.subtitle}
              ta="center"
              c="white"
              size="16px"
            >
              Your project is ready! Complete the setup below to start using
              Pulse.
            </Text>

            {/* Primary CTA Button in Header */}
            <Group justify="center" mt="lg" gap="md">
              <Button
                size="lg"
                className={classes.primaryCtaButton}
                onClick={handleGoToDashboard}
                leftSection={<IconRocket size={20} />}
              >
                Go to Dashboard
              </Button>
            </Group>
          </Box>

          {/* API Key Section */}
          <Paper className={classes.section} shadow="sm" p="xl" radius="md">
            <Group mb="md">
              <IconKey size={24} style={{ color: "#0ec9c2" }} />
              <Text fw={600} size="lg">
                SDK Initialization
              </Text>
            </Group>
            <Text size="sm" c="dimmed" mb="md">
              Use this API key to initialize the Pulse SDK in your application.
              Keep it secure!
            </Text>

            <Group gap="xs" className={classes.apiKeyGroup}>
              <Code className={classes.apiKeyDisplay}>
                {showApiKey ? projectApiKey : maskApiKey(projectApiKey)}
              </Code>
              <ActionIcon
                variant="subtle"
                onClick={() => setShowApiKey(!showApiKey)}
                title={showApiKey ? "Hide API key" : "Show API key"}
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
              <IconCode size={24} style={{ color: "#0ec9c2" }} />
              <Text fw={600} size="lg">
                Quick Start
              </Text>{" "}
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
              <IconUsers size={24} style={{ color: "#0ec9c2" }} />
              <Text fw={600} size="lg">
                Invite Your Team
              </Text>
              <Badge variant="light" color="blue" size="sm">
                Optional
              </Badge>
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
                onChange={(value) =>
                  setInviteRole((value as ProjectRole) || PROJECT_ROLES.VIEWER)
                }
                data={[
                  {
                    value: PROJECT_ROLES.ADMIN,
                    label: PROJECT_ROLE_LABELS[PROJECT_ROLES.ADMIN],
                  },
                  {
                    value: PROJECT_ROLES.EDITOR,
                    label: PROJECT_ROLE_LABELS[PROJECT_ROLES.EDITOR],
                  },
                  {
                    value: PROJECT_ROLES.VIEWER,
                    label: PROJECT_ROLE_LABELS[PROJECT_ROLES.VIEWER],
                  },
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

          {/* <Group justify="center" mt="xl">
            <Button
              size="lg"
              variant="outline"
              className={classes.secondaryCtaButton}
              onClick={handleGoToDashboard}
              leftSection={<IconRocket size={20} />}
            >
              Go to Dashboard
            </Button>
          </Group> */}
        </Stack>
      </Container>
    </Box>
  );
}
