import { useState } from 'react';
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
import { useTenantContext } from '../../contexts';
import { showNotification } from '../../helpers/showNotification';
import { API_BASE_URL } from '../../constants';
import { makeRequest } from '../../helpers/makeRequest';

interface CreateProjectRequest {
  name: string;
  description?: string;
}

interface ProjectResponse {
  projectId: string;
  name: string;
  description: string;
  tenantId: string;
  apiKey: string;
  createdAt: string;
  createdBy: string;
}

export function CreateProject() {
  const navigate = useNavigate();
  const { tenantId, addProject } = useTenantContext();
  
  const [projectName, setProjectName] = useState('');
  const [projectDescription, setProjectDescription] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errors, setErrors] = useState<{ name?: string }>({});

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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!validateForm() || !tenantId) return;
    
    setIsSubmitting(true);
    
    try {
      const response = await makeRequest<ProjectResponse>({
        url: `${API_BASE_URL}/v1/projects`,
        init: {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            name: projectName.trim(),
            description: projectDescription.trim() || undefined,
          } as CreateProjectRequest),
        },
      });
      
      if (response.data) {
        // Add project to tenant context
        addProject({
          projectId: response.data.projectId,
          name: response.data.name,
          description: response.data.description,
          isActive: true,
          role: 'admin',
        });
        
        showNotification(
          'Success',
          'Project created successfully!',
          <IconFolder />,
          '#0ec9c2'
        );
        
        // Navigate to project onboarding
        navigate(`/projects/${response.data.projectId}/onboarding`, {
          state: {
            projectId: response.data.projectId,
            projectName: response.data.name,
            projectApiKey: response.data.apiKey,
          }
        });
      } else {
        showNotification(
          'Error',
          response.error?.message || 'Failed to create project',
          <IconFolder />,
          '#fa5252'
        );
      }
    } catch (error: any) {
      console.error('[CreateProject] Error:', error);
      showNotification(
        'Error',
        error.message || 'Failed to create project',
        <IconFolder />,
        '#fa5252'
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleBack = () => {
    navigate('/organization/projects');
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
