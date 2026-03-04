import { Tabs, Container } from '@mantine/core';
import { IconKey, IconUsers } from '@tabler/icons-react';
import { ApiKeyManagement } from './components/ApiKeyManagement';
import { CollaboratorManagement } from './components/CollaboratorManagement';
import classes from './ProjectSettings.module.css';

export function ProjectSettings() {
  return (
    <Container size="xl" className={classes.container}>
      <Tabs defaultValue="api-keys">
        <Tabs.List>
          <Tabs.Tab value="api-keys" leftSection={<IconKey size={16} />}>
            API Keys
          </Tabs.Tab>
          <Tabs.Tab value="collaborators" leftSection={<IconUsers size={16} />}>
            Collaborators
          </Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="api-keys" pt="xl">
          <ApiKeyManagement />
        </Tabs.Panel>

        <Tabs.Panel value="collaborators" pt="xl">
          <CollaboratorManagement />
        </Tabs.Panel>
      </Tabs>
    </Container>
  );
}
