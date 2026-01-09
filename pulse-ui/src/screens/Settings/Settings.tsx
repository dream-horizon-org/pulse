/**
 * Settings Page
 * Container for various configuration options including SDK Configuration
 */

import { useState } from 'react';
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
} from '@tabler/icons-react';
import { SamplingConfig } from '../SamplingConfig';
import classes from './Settings.module.css';

type SettingsTab = 'sdk-config' | 'notifications' | 'security';

interface SettingsNavItem {
  id: SettingsTab;
  label: string;
  description: string;
  icon: React.ElementType;
  badge?: string;
  disabled?: boolean;
}

const SETTINGS_NAV_ITEMS: SettingsNavItem[] = [
  {
    id: 'sdk-config',
    label: 'SDK Configuration',
    description: 'Control what data your app sends to Pulse',
    icon: IconAdjustments,
  },
  {
    id: 'notifications',
    label: 'Notifications',
    description: 'Manage alert preferences',
    icon: IconBell,
    badge: 'Coming Soon',
    disabled: true,
  },
  {
    id: 'security',
    label: 'Security & Access',
    description: 'API keys and permissions',
    icon: IconShield,
    badge: 'Coming Soon',
    disabled: true,
  },
];

export function Settings() {
  const [activeTab, setActiveTab] = useState<SettingsTab>('sdk-config');

  const renderContent = () => {
    switch (activeTab) {
      case 'sdk-config':
        return <SamplingConfig />;
      case 'notifications':
        return (
          <Box className={classes.comingSoon}>
            <IconBell size={48} style={{ opacity: 0.3 }} />
            <Text size="lg" fw={600} mt="md">Notifications</Text>
            <Text c="dimmed">Coming soon...</Text>
          </Box>
        );
      case 'security':
        return (
          <Box className={classes.comingSoon}>
            <IconShield size={48} style={{ opacity: 0.3 }} />
            <Text size="lg" fw={600} mt="md">Security & Access</Text>
            <Text c="dimmed">Coming soon...</Text>
          </Box>
        );
      default:
        return null;
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
              onClick={() => !item.disabled && setActiveTab(item.id)}
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

      {/* Main Content */}
      <Box className={classes.content}>
        {renderContent()}
      </Box>
    </Box>
  );
}

