import { Container, Title, Grid, Card, Text } from '@mantine/core';
import { useUserProjects } from '../../hooks';
import { useState, useEffect } from 'react';

export function OrganizationDashboard() {
  const [stats, setStats] = useState({
    totalProjects: 0,
    activeProjects: 0,
    totalMembers: 0,
  });

  const { data: projectsData } = useUserProjects();

  useEffect(() => {
    if (projectsData?.data) {
      setStats(prev => ({
        ...prev,
        totalProjects: projectsData.data!.projects.length,
        activeProjects: projectsData.data!.projects.filter(p => p.isActive).length,
      }));
    }
  }, [projectsData]);

  return (
    <Container size="xl">
      <Title order={1} mb="xl">Organization Overview</Title>
      <Grid>
        <Grid.Col span={4}>
          <Card shadow="sm" padding="lg">
            <Text size="xl" fw={700}>{stats.totalProjects}</Text>
            <Text c="dimmed">Total Projects</Text>
          </Card>
        </Grid.Col>
        <Grid.Col span={4}>
          <Card shadow="sm" padding="lg">
            <Text size="xl" fw={700}>{stats.activeProjects}</Text>
            <Text c="dimmed">Active Projects</Text>
          </Card>
        </Grid.Col>
        <Grid.Col span={4}>
          <Card shadow="sm" padding="lg">
            <Text size="xl" fw={700}>{stats.totalMembers}</Text>
            <Text c="dimmed">Team Members</Text>
          </Card>
        </Grid.Col>
      </Grid>
    </Container>
  );
}
