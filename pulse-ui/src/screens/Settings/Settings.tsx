/**
 * Settings Page
 * Container for various configuration options including SDK Configuration
 * Uses React Router for sub-navigation between settings sections
 */

import {
  Box,
  Text,
  Paper,
  Group,
  Stack,
  NavLink,
  Divider,
  Badge,
} from '@mantine/core';
import {
  IconSettings,
  IconAdjustments,
  IconBell,
  IconShield,
  IconChevronRight,
  IconKey,
  IconUsers,
} from '@tabler/icons-react';
import { Routes, Route, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { SamplingConfig } from '../SamplingConfig';
import { NotificationChannels } from './components/NotificationChannels';
import { ApiKeyManagement } from '../ProjectSettings/components/ApiKeyManagement';
import { CollaboratorManagement } from '../ProjectSettings/components/CollaboratorManagement';
import classes from './Settings.module.css';

type SettingsTab = 'sdk-config' | 'notifications' | 'api-keys' | 'collaborators' | 'security';

interface SettingsNavItem {
  id: SettingsTab;
  path: string;
  label: string;
  description: string;
  icon: React.ElementType;
  badge?: string;
  disabled?: boolean;
}

const SETTINGS_NAV_ITEMS: SettingsNavItem[] = [
  {
    id: 'sdk-config',
    path: 'sdk-config',
    label: 'SDK Configuration',
    description: 'Control what data your app sends to Pulse',
    icon: IconAdjustments,
  },
  {
    id: 'api-keys',
    path: 'api-keys',
    label: 'API Keys',
    description: 'Manage project API keys',
    icon: IconKey,
  },
  {
    id: 'collaborators',
    path: 'collaborators',
    label: 'Team Members',
    description: 'Project Members',
    icon: IconUsers,
  },
  {
    id: 'notifications',
    path: 'notifications',
    label: 'Notifications',
    description: 'Manage notification channels',
    icon: IconBell,
  },
  {
    id: 'security',
    path: 'security',
    label: 'Security & Access',
    description: 'Advanced security settings',
    icon: IconShield,
    badge: 'Coming Soon',
    disabled: true,
  },
];

// Coming Soon placeholder component
function ComingSoonSection({ icon: Icon, title }: { icon: React.ElementType; title: string }) {
  return (
    <Box className={classes.comingSoon}>
      <Icon size={48} style={{ opacity: 0.3 }} />
      <Text size="lg" fw={600} mt="md">{title}</Text>
      <Text c="dimmed">Coming soon...</Text>
    </Box>
  );
}

export function Settings() {
  const navigate = useNavigate();
  const location = useLocation();

  // Determine active tab from current path
  const getActiveTab = (): SettingsTab => {
    const path = location.pathname;
    if (path.includes('/api-keys')) return 'api-keys';
    if (path.includes('/collaborators')) return 'collaborators';
    if (path.includes('/notifications')) return 'notifications';
    if (path.includes('/security')) return 'security';
    return 'sdk-config';
  };

  const activeTab = getActiveTab();

  const handleNavClick = (item: SettingsNavItem) => {
    if (!item.disabled) {
      // Use project-scoped route if we're on a project page
      const projectMatch = location.pathname.match(/\/projects\/([^/]+)/);
      if (projectMatch) {
        const projectId = projectMatch[1];
        navigate(`/projects/${projectId}/settings/${item.path}`);
      } else {
        navigate(`/settings/${item.path}`);
      }
    }
  };

  return (
    <Box className={classes.container}>
      {/* Sidebar Navigation */}
      <Paper className={classes.sidebar} withBorder>
        <Group gap="sm" mb="lg" p="md">
          <IconSettings size={24} style={{ color: '#0ec9c2' }} />
          <Text fw={700} size="lg">Settings</Text>
        </Group>
        
        <Divider mb="md" />
        
        <Stack gap={4} px="xs">
          {SETTINGS_NAV_ITEMS.map((item) => (
            <NavLink
              key={item.id}
              active={activeTab === item.id}
              label={
                <Group justify="space-between" wrap="nowrap">
                  <Text size="sm" fw={500}>{item.label}</Text>
                  {item.badge && (
                    <Badge size="xs" variant="light" color="gray">
                      {item.badge}
                    </Badge>
                  )}
                </Group>
              }
              description={item.description}
              leftSection={<item.icon size={20} />}
              rightSection={<IconChevronRight size={14} />}
              onClick={() => handleNavClick(item)}
              disabled={item.disabled}
              variant="light"
              styles={{
                root: {
                  borderRadius: 8,
                  marginBottom: 4,
                },
                label: {
                  fontWeight: 500,
                },
              }}
            />
          ))}
        </Stack>
      </Paper>

      {/* Main Content - Routed */}
      <Box className={classes.content}>
        <Routes>
          <Route index element={<Navigate to="sdk-config" replace />} />
          <Route path="sdk-config/*" element={<SamplingConfig />} />
          <Route path="api-keys" element={<ApiKeyManagement />} />
          <Route path="collaborators" element={<CollaboratorManagement />} />
          <Route path="notifications" element={<NotificationChannels />} />
          <Route path="security" element={<ComingSoonSection icon={IconShield} title="Security & Access" />} />
        </Routes>
      </Box>
    </Box>
  );
}
