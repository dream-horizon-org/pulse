import { Container, Title, Tabs, Text } from '@mantine/core';
import { IconSettings, IconCreditCard } from '@tabler/icons-react';

export function OrganizationSettings() {
  return (
    <Container size="xl">
      <Title order={1} mb="xl">Organization Settings</Title>
      <Tabs defaultValue="general">
        <Tabs.List>
          <Tabs.Tab value="general" leftSection={<IconSettings size={16} />}>
            General
          </Tabs.Tab>
          <Tabs.Tab value="billing" leftSection={<IconCreditCard size={16} />}>
            Billing
          </Tabs.Tab>
        </Tabs.List>
        
        <Tabs.Panel value="general" pt="xl">
          <Title order={3} mb="md">General Settings</Title>
          <Text>Organization name, branding, and other general settings will be available here.</Text>
        </Tabs.Panel>
        
        <Tabs.Panel value="billing" pt="xl">
          <Title order={3} mb="md">Billing & Subscription</Title>
          <Text>Billing and subscription management will be available here.</Text>
        </Tabs.Panel>
      </Tabs>
    </Container>
  );
}
