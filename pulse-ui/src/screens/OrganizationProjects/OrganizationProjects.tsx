import { Container, Title, Grid, Card, Button, Group, Text, Badge } from '@mantine/core';
import { IconPlus, IconFolder, IconLock } from '@tabler/icons-react';
import { useTenantContext } from '../../contexts';
import { usePermissions, useTierLimits } from '../../hooks';
import { useNavigate } from 'react-router-dom';
import { showNotification } from '../../helpers/showNotification';

export function OrganizationProjects() {
  const { projects, isLoading, tier } = useTenantContext();
  const { canCreateProjects: hasPermission } = usePermissions();
  const { canCreateProjects, maxProjects, currentProjectCount } = useTierLimits();
  const navigate = useNavigate();

  const handleCreateProject = () => {
    if (!canCreateProjects) {
      showNotification(
        'Upgrade Required',
        'Free tier is limited to 1 project. Upgrade to Enterprise for unlimited projects.',
        <IconLock />,
        'orange'
      );
      navigate('/pricing');
      return;
    }
    navigate('/organization/projects/new');
  };

  const handleProjectClick = (projectId: string) => {
    navigate(`/projects/${projectId}`);
  };

  if (isLoading) {
    return (
      <Container size="xl">
        <Text>Loading projects...</Text>
      </Container>
    );
  }

  return (
    <Container size="xl">
      <Group justify="space-between" mb="xl">
        <Group>
          <div>
            <Title order={1}>All Projects</Title>
            <Text c="dimmed" size="sm">
              Manage your projects and create new ones
            </Text>
          </div>
          <Badge color={tier === 'enterprise' ? 'blue' : 'gray'} variant="light" size="lg">
            {tier === 'free' ? 'Free Tier' : 'Enterprise'}
          </Badge>
        </Group>
        <div>
          {tier === 'free' && (
            <Text size="sm" c="dimmed" mb="xs" ta="right">
              {currentProjectCount} / {maxProjects} projects used
            </Text>
          )}
          {hasPermission && (
            <Button
              leftSection={<IconPlus size={16} />}
              onClick={handleCreateProject}
              variant="gradient"
              gradient={{ from: '#0ec9c2', to: '#0ba09a' }}
              disabled={!canCreateProjects}
            >
              Create Project
            </Button>
          )}
        </div>
      </Group>
      
      {projects.length === 0 ? (
        <Card shadow="sm" padding="xl" style={{ textAlign: 'center' }}>
          <Text c="dimmed" mb="md">
            No projects yet. {canCreateProjects && 'Create your first project to get started.'}
          </Text>
          {hasPermission && (
            <Button
              leftSection={<IconPlus size={16} />}
              onClick={handleCreateProject}
              variant="light"
              color="teal"
              disabled={!canCreateProjects}
            >
              {canCreateProjects ? 'Create First Project' : 'Upgrade to Create Projects'}
            </Button>
          )}
        </Card>
      ) : (
        <Grid>
          {projects.map(project => (
            <Grid.Col key={project.projectId} span={4}>
              <Card 
                shadow="sm" 
                padding="lg"
                onClick={() => handleProjectClick(project.projectId)}
                style={{ cursor: 'pointer', height: '100%' }}
                withBorder
              >
                <Group justify="space-between" mb="xs">
                  <IconFolder size={24} style={{ color: '#0ba09a' }} />
                  {project.isActive ? (
                    <Badge color="teal" variant="light" size="sm">Active</Badge>
                  ) : (
                    <Badge color="gray" variant="light" size="sm">Inactive</Badge>
                  )}
                </Group>
                <Text fw={500} size="lg" mb="xs">{project.name}</Text>
                <Text size="sm" c="dimmed" lineClamp={2} mb="sm">
                  {project.description || 'No description'}
                </Text>
                <Group justify="space-between" mt="auto">
                  <Text size="xs" c="dimmed">Your Role: <strong>{project.role}</strong></Text>
                </Group>
              </Card>
            </Grid.Col>
          ))}
        </Grid>
      )}
    </Container>
  );
}
