import { useState } from 'react';
import { flushSync } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import {
  Container,
  Title,
  Text,
  TextInput,
  Textarea,
  Button,
  Stack,
  Card,
  Group,
} from '@mantine/core';
import { IconFolder, IconArrowLeft } from '@tabler/icons-react';
import { useTenantContext, useProjectContext } from '../../contexts';
import { showNotification } from '../../helpers/showNotification';
import { ROUTES } from '../../constants';
import { useCreateProject } from '../../hooks';
import { PROJECT_ROLES } from '../../constants/Roles';
import { TIERS } from '../../constants/Tiers';

export function CreateProject() {
  const navigate = useNavigate();
  const { tenantId, addProject } = useTenantContext();
  const { setProject } = useProjectContext();
  
  const [projectName, setProjectName] = useState('');
  const [projectDescription, setProjectDescription] = useState('');
  const [errors, setErrors] = useState<{ name?: string }>({});
  
  const createProjectMutation = useCreateProject();
  const isSubmitting = createProjectMutation.isPending;

  const validateForm = (): boolean => {
    const newErrors: { name?: string } = {};
    
    if (!projectName.trim()) {
      newErrors.name = 'Project name is required';
    } else if (projectName.trim().length < 3) {
      newErrors.name = 'Project name must be at least 3 characters';
    }
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!validateForm() || !tenantId) return;
    
    createProjectMutation.mutate(
      {
        name: projectName.trim(),
        description: projectDescription.trim() || undefined,
      },
      {
        onSuccess: (response) => {
          if (response?.data) {
            const projectData = response.data;

            // Force immediate state update using flushSync to prevent race conditions
            // This ensures the project is in both TenantContext and ProjectContext before navigation
            flushSync(() => {
              // Update TenantContext (adds to projects list)
              addProject({
                projectId: projectData.projectId,
                name: projectData.name,
                description: projectData.description,
                isActive: true,
                role: PROJECT_ROLES.ADMIN,
              });
              
              // Update ProjectContext (sets as active project)
              setProject({
                projectId: projectData.projectId,
                projectName: projectData.name,
                userRole: PROJECT_ROLES.ADMIN,
                isActive: true,
              });
              
              // CRITICAL: Also update sessionStorage immediately to prevent race conditions
              sessionStorage.setItem('pulse_project_context', JSON.stringify({
                projectId: projectData.projectId,
                projectName: projectData.name,
                userRole: PROJECT_ROLES.ADMIN,
                isActive: true,
                plan: TIERS.FREE,
                timestamp: Date.now()
              }));
              
              // Update last used project ID
              sessionStorage.setItem('pulse_last_project_id', projectData.projectId);
            });
            
            showNotification(
              'Success',
              'Project created successfully!',
              <IconFolder />,
              '#0ec9c2'
            );
            
            // Navigate to project onboarding
            navigate(`/projects/${projectData.projectId}/onboarding`, {
              state: {
                projectId: projectData.projectId,
                projectName: projectData.name,
                projectApiKey: projectData.apiKey,
              }
            });
          }
        },
        onError: (error) => {
          showNotification(
            'Error',
            error instanceof Error ? error.message : 'Failed to create project',
            <IconFolder />,
            '#fa5252'
          );
        },
      }
    );
  };

  const handleBack = () => {
    if (tenantId) {
      navigate(ROUTES.ORGANIZATION_PROJECTS.basePath.replace(':organizationId', tenantId));
    }
  };

  return (
    <Container size="sm" py="xl">
      <Stack gap="xl">
        {/* Header */}
        <div>
          <Group gap="xs" mb="xs">
            <Button
              variant="subtle"
              color="gray"
              leftSection={<IconArrowLeft size={16} />}
              onClick={handleBack}
              p={0}
            >
              Back to Projects
            </Button>
          </Group>
          <Title order={1}>Create New Project</Title>
          <Text c="dimmed" size="sm">
            Set up a new project to start monitoring your application
          </Text>
        </div>

        {/* Form */}
        <Card shadow="sm" padding="xl" withBorder>
          <form onSubmit={handleSubmit}>
            <Stack gap="lg">
              <TextInput
                label="Project Name"
                placeholder="e.g., Mobile App, Web Dashboard"
                size="md"
                required
                value={projectName}
                onChange={(e) => setProjectName(e.target.value)}
                error={errors.name}
                leftSection={<IconFolder size={18} />}
                description="A descriptive name for your project"
              />

              <Textarea
                label="Description (Optional)"
                placeholder="Brief description of your project..."
                size="md"
                value={projectDescription}
                onChange={(e) => setProjectDescription(e.target.value)}
                minRows={3}
                maxRows={5}
                description="Help your team understand what this project is for"
              />

              <Group justify="flex-end" mt="md">
                <Button
                  variant="subtle"
                  color="gray"
                  onClick={handleBack}
                  disabled={isSubmitting}
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  loading={isSubmitting}
                  disabled={isSubmitting}
                  variant="gradient"
                  gradient={{ from: '#0ec9c2', to: '#0ba09a' }}
                >
                  {isSubmitting ? 'Creating...' : 'Create Project'}
                </Button>
              </Group>
            </Stack>
          </form>
        </Card>
      </Stack>
    </Container>
  );
}
