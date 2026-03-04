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
} from "@mantine/core";
import { IconSquareRoundedX, IconBuilding, IconFolder } from "@tabler/icons-react";
import classes from "./Onboarding.module.css";
import { ROUTES, COMMON_CONSTANTS } from "../../constants";
import { completeOnboarding } from "../../helpers/onboarding";
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
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errors, setErrors] = useState<{ org?: string; project?: string }>({});

  useEffect(() => {
    // Load user data from sessionStorage
    const storedData = sessionStorage.getItem('onboarding_user');
    const firebaseToken = sessionStorage.getItem('firebase_token');
    
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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!validateForm() || !userData?.firebaseToken) return;
    
    setIsSubmitting(true);
    
    const { data, error } = await completeOnboarding(
      {
        organizationName: organizationName.trim(),
        projectName: projectName.trim(),
        projectDescription: projectDescription.trim() || undefined,
      },
      userData.firebaseToken
    );
    
    if (data) {
      console.log('[Onboarding] Onboarding successful, setting contexts');
      
      // Set cookies with auth tokens only
      await setCookiesAfterAuthentication(data);
      
      // Set tenant context
      setTenantInfo({
        tenantId: data.tenantId,
        tenantName: data.tenantName,
        userRole: 'admin', // User who completes onboarding is always admin
        tier: data.tier || 'free',
      });
      
      // Set project context
      if (data.projectId && data.projectName) {
        setProject({
          projectId: data.projectId,
          projectName: data.projectName,
          userRole: 'admin', // Owner's first project = admin
          isActive: true,
          plan: 'free',
        });
        
        // Add project to tenant's projects list
        addProject({
          projectId: data.projectId,
          name: data.projectName,
          description: projectDescription.trim() || '',
          isActive: true,
          role: 'admin',
        });
      }
      
      // Clear sessionStorage
      sessionStorage.removeItem('onboarding_user');
      sessionStorage.removeItem('firebase_token');
      
      // Navigate to project-scoped onboarding success page
      navigate(`/projects/${data.projectId}/onboarding`, {
        state: {
          projectId: data.projectId,
          projectName: data.projectName,
          projectApiKey: data.projectApiKey,
        }
      });
    }
    
    if (error) {
      showNotification(
        COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
        error.message,
        <IconSquareRoundedX />,
        theme.colors.red[6]
      );
    }
    
    setIsSubmitting(false);
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
              Let's set up your organization and create your first project.
            </Text>
            <Text className={classes.userInfo}>
              Signed in as <strong>{userData.email}</strong>
            </Text>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className={classes.form}>
            <Stack gap="lg">
              {/* Organization Name */}
              <TextInput
                label="Organization Name"
                placeholder="e.g., Acme Inc."
                size="md"
                required
                value={organizationName}
                onChange={(e) => setOrganizationName(e.target.value)}
                error={errors.org}
                leftSection={<IconBuilding size={18} />}
                description="This will be your company or team name"
              />

              {/* Project Name */}
              <TextInput
                label="Project Name"
                placeholder="e.g., Mobile App, Web Dashboard"
                size="md"
                required
                value={projectName}
                onChange={(e) => setProjectName(e.target.value)}
                error={errors.project}
                leftSection={<IconFolder size={18} />}
                description="The application you want to monitor"
              />

              {/* Project Description (Optional) */}
              <Textarea
                label="Project Description (Optional)"
                placeholder="Brief description of your project..."
                size="md"
                value={projectDescription}
                onChange={(e) => setProjectDescription(e.target.value)}
                minRows={3}
                maxRows={5}
              />

              {/* Submit Button */}
              <Button
                type="submit"
                size="lg"
                loading={isSubmitting}
                disabled={isSubmitting}
                fullWidth
                style={{
                  background: "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
                  marginTop: "1rem",
                }}
              >
                {isSubmitting ? "Creating..." : "Create Project & Continue"}
              </Button>
            </Stack>
          </form>
        </Stack>
      </Container>
    </Box>
  );
}
