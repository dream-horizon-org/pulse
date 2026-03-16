import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box,
  Text,
  Stack,
  Container,
  TextInput,
  Textarea,
  Button,
  Paper,
  Badge,
  Group,
} from "@mantine/core";
import {
  IconSquareRoundedX,
  IconBuilding,
  IconFolder,
  IconCheck,
} from "@tabler/icons-react";
import classes from "./Onboarding.module.css";
import { ROUTES, COMMON_CONSTANTS } from "../../constants";
import { useCompleteOnboarding } from "../../hooks";
import { PROJECT_ROLES } from "../../constants/Roles";
import { TIERS } from "../../constants/Tiers";
import { setCookiesAfterAuthentication } from "../../helpers/setCookiesAfterAuthentication";
import { showNotification } from "../../helpers/showNotification";
import { LoaderWithMessage } from "../../components/LoaderWithMessage";
import { useMantineTheme } from "@mantine/core";
import { useTenantContext, useProjectContext } from "../../contexts";

interface OnboardingUserData {
  userId: string;
  email: string;
  name: string;
  firebaseToken?: string;
}

export function Onboarding() {
  const navigate = useNavigate();
  const theme = useMantineTheme();
  const { setTenantInfo, addProject } = useTenantContext();
  const { setProject } = useProjectContext();

  const [userData, setUserData] = useState<OnboardingUserData | null>(null);
  const [organizationName, setOrganizationName] = useState("");
  const [projectName, setProjectName] = useState("");
  const [projectDescription, setProjectDescription] = useState("");
  const [errors, setErrors] = useState<{ org?: string; project?: string }>({});

  const onboardingMutation = useCompleteOnboarding();
  const isSubmitting = onboardingMutation.isPending;

  useEffect(() => {
    // Load user data from sessionStorage
    const storedData = sessionStorage.getItem("onboarding_user");
    const firebaseToken = sessionStorage.getItem("firebase_token");

    if (!storedData || !firebaseToken) {
      // No onboarding data - redirect to login
      navigate(ROUTES.LOGIN.basePath);
      return;
    }

    try {
      const parsed = JSON.parse(storedData);
      setUserData({ ...parsed, firebaseToken });
    } catch (e) {
      navigate(ROUTES.LOGIN.basePath);
    }
  }, [navigate]);

  const validateForm = (): boolean => {
    const newErrors: { org?: string; project?: string } = {};

    if (!organizationName.trim()) {
      newErrors.org = "Organization name is required";
    }
    if (!projectName.trim()) {
      newErrors.project = "Project name is required";
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!validateForm() || !userData?.firebaseToken) return;

    onboardingMutation.mutate(
      {
        request: {
          organizationName: organizationName.trim(),
          projectName: projectName.trim(),
          projectDescription: projectDescription.trim() || undefined,
        },
        firebaseToken: userData.firebaseToken,
      },
      {
        onSuccess: async (response) => {
          if (response?.data) {
            const data = response.data;

            // Set cookies with auth tokens only
            await setCookiesAfterAuthentication(data);

            // Set tenant context
            setTenantInfo({
              tenantId: data.tenantId,
              tenantName: data.tenantName,
              userRole: "admin",
              tier: data.tier || "free",
            });

            // Set project context
            if (data.projectId && data.projectName) {
              setProject({
                projectId: data.projectId,
                projectName: data.projectName,
                userRole: PROJECT_ROLES.ADMIN,
                isActive: true,
                plan: TIERS.FREE,
              });

              // Add project to tenant's projects list
              addProject({
                projectId: data.projectId,
                name: data.projectName,
                description: projectDescription.trim() || "",
                isActive: true,
                role: PROJECT_ROLES.ADMIN,
              });
            }

            // Clear sessionStorage
            sessionStorage.removeItem("onboarding_user");
            sessionStorage.removeItem("firebase_token");

            // Navigate to project-scoped onboarding success page
            navigate(
              ROUTES.PROJECT_ONBOARDING_SUCCESS.basePath.replace(
                ":projectId",
                data.projectId,
              ),
              {
                state: {
                  projectId: data.projectId,
                  projectName: data.projectName,
                  projectApiKey: data.projectApiKey,
                },
              },
            );
          }
        },
        onError: (error) => {
          showNotification(
            COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
            error instanceof Error
              ? error.message
              : "Onboarding failed. Please try again.",
            <IconSquareRoundedX />,
            theme.colors.red[6],
          );
        },
      },
    );
  };

  if (!userData) {
    return <LoaderWithMessage loadingMessage="Loading..." />;
  }

  return (
    <Box className={classes.onboardingContainer}>
      <Container size="sm" className={classes.contentWrapper}>
        <Stack gap="xl">
          {/* Header */}
          <div className={classes.header}>
            <h1 className={classes.title}>Welcome to Pulse!</h1>
            <Text className={classes.subtitle}>
              Let's get you started in 2 simple steps
            </Text>
            <Text className={classes.userInfo}>
              Signed in as <strong>{userData.email}</strong>
            </Text>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit}>
            <Stack gap="xl">
              {/* Step 1: Organization */}
              <Paper
                className={classes.stepSection}
                shadow="sm"
                p="xl"
                radius="md"
              >
                <Group gap="sm" mb="md">
                  <Badge
                    size="lg"
                    circle
                    variant="filled"
                    style={{
                      background:
                        "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
                    }}
                  >
                    1
                  </Badge>
                  <Text fw={600} size="lg" className={classes.stepTitle}>
                    Create Your Organization
                  </Text>
                  {organizationName.trim() && (
                    <IconCheck size={20} style={{ color: "#0ec9c2" }} />
                  )}
                </Group>

                <Text
                  size="sm"
                  c="dimmed"
                  mb="lg"
                  className={classes.stepDescription}
                >
                  Your organization represents your company or team. You can
                  invite members and manage multiple projects within it.
                </Text>

                <TextInput
                  label="Organization Name"
                  placeholder="e.g., Acme Inc., Marketing Team"
                  size="md"
                  required
                  value={organizationName}
                  onChange={(e) => setOrganizationName(e.target.value)}
                  error={errors.org}
                  leftSection={<IconBuilding size={18} />}
                  description="This will be your company or team name"
                />
              </Paper>

              {/* Step 2: Project */}
              <Paper
                className={classes.stepSection}
                shadow="sm"
                p="xl"
                radius="md"
                style={{
                  opacity: organizationName.trim() ? 1 : 0.7,
                  transition: "opacity 0.3s ease",
                }}
              >
                <Group gap="sm" mb="md">
                  <Badge
                    size="lg"
                    circle
                    variant="filled"
                    style={{
                      background:
                        "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
                    }}
                  >
                    2
                  </Badge>
                  <Text fw={600} size="lg" className={classes.stepTitle}>
                    Create Your First Project
                  </Text>
                  {projectName.trim() && (
                    <IconCheck size={20} style={{ color: "#0ec9c2" }} />
                  )}
                </Group>

                <Text
                  size="sm"
                  c="dimmed"
                  mb="lg"
                  className={classes.stepDescription}
                >
                  Projects live inside your organization. Each project tracks a
                  specific application or service.
                </Text>

                <Stack gap="md">
                  <TextInput
                    label="Project Name"
                    placeholder="e.g., Mobile App, Web Dashboard, API Service"
                    size="md"
                    required
                    value={projectName}
                    onChange={(e) => setProjectName(e.target.value)}
                    error={errors.project}
                    leftSection={<IconFolder size={18} />}
                    description="What application do you want to monitor?"
                  />

                  <Textarea
                    label="Project Description (Optional)"
                    placeholder="Add a brief description to help your team understand this project..."
                    size="md"
                    value={projectDescription}
                    onChange={(e) => setProjectDescription(e.target.value)}
                    minRows={3}
                    maxRows={5}
                    description="Provide context about what this project does"
                  />
                </Stack>
              </Paper>

              {/* Submit Button */}
              <Button
                type="submit"
                size="lg"
                loading={isSubmitting}
                disabled={isSubmitting}
                fullWidth
                style={{
                  background:
                    "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
                  marginTop: "1rem",
                }}
              >
                {isSubmitting
                  ? "Setting up..."
                  : "Complete Setup & Get Started"}
              </Button>
            </Stack>
          </form>
        </Stack>
      </Container>
    </Box>
  );
}
