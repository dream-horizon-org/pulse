import { useEffect, useState, useCallback } from 'react';
import { flushSync } from 'react-dom';
import { Container, Title, Grid, Card, Button, Group, Text, Badge, Stack, Box } from '@mantine/core';
import { IconPlus, IconFolder, IconLock, IconUsers, IconRocket } from '@tabler/icons-react';
import { useTenantContext, useProjectContext } from '../../contexts';
import { usePermissions, useTierLimits } from '../../hooks';
import { useNavigate, useParams } from 'react-router-dom';
import { showNotification } from '../../helpers/showNotification';
import classes from './OrganizationProjects.module.css';
import { TIERS } from '../../constants/Tiers';

export function OrganizationProjects() {
  const { organizationId } = useParams<{ organizationId: string }>();
  const { projects, hasLoadedProjects, tier, refreshProjects } = useTenantContext();
  const { projectId, setProject } = useProjectContext();
  const { canCreateProjects: hasPermission } = usePermissions();
  const { canCreateProjects, maxProjects, currentProjectCount } = useTierLimits();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  const handleProjectClick = useCallback(async (selectedProjectId: string) => {
    try {
      // Find the project details from the projects list
      const selectedProject = projects.find(p => p.projectId === selectedProjectId);
      
      if (!selectedProject) {
        console.error('[OrganizationProjects] Project not found in list');
        setError('Project not found');
        return;
      }
      
      // Force immediate context AND sessionStorage update before navigation
      flushSync(() => {
        // Update React context
        setProject({
          projectId: selectedProject.projectId,
          projectName: selectedProject.name,
          userRole: selectedProject.role as 'admin' | 'editor' | 'viewer',
          isActive: selectedProject.isActive,
        });
        
        // CRITICAL: Also update sessionStorage immediately to prevent race conditions
        sessionStorage.setItem('pulse_project_context', JSON.stringify({
          projectId: selectedProject.projectId,
          projectName: selectedProject.name,
          userRole: selectedProject.role,
          isActive: selectedProject.isActive,
          plan: 'free',
          timestamp: Date.now()
        }));
        
        // Update last used project ID
        sessionStorage.setItem('pulse_last_project_id', selectedProject.projectId);
      });

      navigate(`/projects/${selectedProjectId}`);
    } catch (err) {
      setError('Failed to switch to project');
      console.error(err);
    }
  }, [projects, setProject, navigate]);

  // Trigger refresh on mount to ensure fresh data
  useEffect(() => {
    refreshProjects();
  }, [refreshProjects]);

  // Handle auto-selection after projects are loaded
  useEffect(() => {
    if (!hasLoadedProjects) {
      return;
    }

    // If user already has a project context set, redirect to that project
    console.log('[OrganizationProjects] projectId:', projectId, projects);
    if (projectId) {
      navigate(`/projects/${projectId}`, { replace: true });
      return;
    }

    // Auto-select first project ONLY for free tier users (who can only have 1 project)
    if (!projectId && projects.length > 0 && tier === TIERS.FREE) {
      const lastUsedProjectId = sessionStorage.getItem('pulse_last_project_id');
      const projectToSelect = projects.find(p => p.projectId === lastUsedProjectId) || projects[0];
      handleProjectClick(projectToSelect.projectId);
    }
  }, [projectId, projects, navigate, hasLoadedProjects, tier, handleProjectClick]);

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
    navigate(`/${organizationId}/projects/new`);
  };

  // Show loader while projects are loading
  if (!hasLoadedProjects) {
    return (
      <Box className={classes.container}>
        <Container size="xl" className={classes.innerContainer}>
          <Text size="lg" c="dimmed">Loading projects...</Text>
        </Container>
      </Box>
    );
  }

  if (error) {
    return (
      <Box className={classes.container}>
        <Container size="xl" className={classes.innerContainer}>
          <Text c="red" size="lg">{error}</Text>
        </Container>
      </Box>
    );
  }

  return (
    <Box className={classes.container}>
      <Container size="xl" className={classes.innerContainer}>
        {/* Header Section */}
        <Box className={classes.header}>
          <Stack gap="xs">
            <Group gap="md" align="center">
              <Title order={1} className={classes.title}>Projects</Title>
              <Badge 
                size="lg" 
                variant="dot"
                color={tier === TIERS.ENTERPRISE ? 'blue' : 'gray'}
                className={classes.tierBadge}
              >
                {tier === TIERS.FREE ? 'Free Tier' : 'Enterprise'}
              </Badge>
            </Group>
            <Text c="dimmed" size="md">
              Manage and access all your organization's projects
            </Text>
          </Stack>
          
          <Group gap="md">
            {tier === TIERS.FREE && (
              <Text size="sm" c="dimmed" className={classes.projectCount}>
                {currentProjectCount} / {maxProjects} projects
              </Text>
            )}
            {hasPermission && (
              <Button
                leftSection={<IconPlus size={18} />}
                onClick={handleCreateProject}
                size="md"
                className={classes.createButton}
                disabled={!canCreateProjects}
              >
                Create Project
              </Button>
            )}
          </Group>
        </Box>
        
        {/* Projects Grid or Empty State */}
        {projects.length === 0 ? (
          <Card className={classes.emptyState} shadow="sm" radius="lg" withBorder>
            <Stack align="center" gap="lg">
              <Box className={classes.emptyIconWrapper}>
                <IconRocket size={48} className={classes.emptyIcon} />
              </Box>
              <Stack align="center" gap="xs">
                <Text size="xl" fw={600}>No projects yet</Text>
                <Text c="dimmed" size="md" ta="center" maw={400}>
                  {canCreateProjects 
                    ? 'Get started by creating your first project to track analytics and monitor your applications.'
                    : 'Contact your administrator to create projects for your organization.'}
                </Text>
              </Stack>
              {hasPermission && canCreateProjects && (
                <Button
                  leftSection={<IconPlus size={18} />}
                  onClick={handleCreateProject}
                  size="lg"
                  className={classes.createButton}
                  mt="md"
                >
                  Create First Project
                </Button>
              )}
            </Stack>
          </Card>
        ) : (
          <Grid gutter="xl">
            {projects.map(project => (
              <Grid.Col key={project.projectId} span={{ base: 12, sm: 6, md: 4 }}>
                <Card 
                  className={classes.projectCard}
                  shadow="sm" 
                  padding="xl"
                  radius="lg"
                  onClick={() => handleProjectClick(project.projectId)}
                  withBorder
                >
                  <Stack gap="md">
                    {/* Card Header */}
                    <Group justify="space-between" wrap="nowrap">
                      <Box className={classes.iconWrapper}>
                        <IconFolder size={28} />
                      </Box>
                      {project.isActive ? (
                        <Badge color="teal" variant="dot" size="sm">Active</Badge>
                      ) : (
                        <Badge color="gray" variant="dot" size="sm">Inactive</Badge>
                      )}
                    </Group>
                    
                    {/* Project Name */}
                    <Stack gap={4}>
                      <Text fw={600} size="lg" className={classes.projectName}>
                        {project.name}
                      </Text>
                      <Text size="sm" c="dimmed" lineClamp={2} className={classes.projectDescription}>
                        {project.description || 'No description provided'}
                      </Text>
                    </Stack>
                    
                    {/* Card Footer */}
                    <Group justify="space-between" mt="auto" pt="md">
                      <Group gap={4}>
                        <IconUsers size={14} style={{ color: 'var(--mantine-color-dimmed)' }} />
                        <Text size="xs" c="dimmed" tt="capitalize">{project.role}</Text>
                      </Group>
                      <Text size="xs" c="teal" fw={500} className={classes.openLink}>
                        Open →
                      </Text>
                    </Group>
                  </Stack>
                </Card>
              </Grid.Col>
            ))}
          </Grid>
        )}
      </Container>
    </Box>
  );
}
