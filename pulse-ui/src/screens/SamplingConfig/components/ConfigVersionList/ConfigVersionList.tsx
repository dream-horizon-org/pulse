/**
 * Configuration Version List Component
 * Displays all configuration versions with beautiful table styling matching crash list
 */

import { useState, useEffect } from 'react';
import {
  Box,
  Text,
  Button,
  Group,
  Badge,
  ActionIcon,
  Tooltip,
  Loader,
  Table,
} from '@mantine/core';
import {
  IconPlus,
  IconEye,
  IconCopy,
  IconCheck,
  IconHistory,
  IconRocket,
} from '@tabler/icons-react';
import { ConfigVersion } from '../../SamplingConfig.interface';
import { makeRequest } from '../../../../helpers/makeRequest';
import { API_BASE_URL, API_METHODS } from '../../../../constants';
import classes from './ConfigVersionList.module.css';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';

dayjs.extend(relativeTime);

interface ConfigVersionListProps {
  onViewVersion: (version: number) => void;
  onCreateNew: (baseVersion?: number) => void;
}

export function ConfigVersionList({ onViewVersion, onCreateNew }: ConfigVersionListProps) {
  const [versions, setVersions] = useState<ConfigVersion[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isError, setIsError] = useState(false);

  const loadVersions = async () => {
    setIsLoading(true);
    setIsError(false);
    try {
      const response = await makeRequest<{ versions: ConfigVersion[] }>({
        url: `${API_BASE_URL}/v1/sdk-config/versions`,
        init: { method: API_METHODS.GET },
      });

      if (response.data?.versions) {
        setVersions(response.data.versions);
      } else {
        // Mock data for development
        setVersions([
          {
            version: 5,
            createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
            createdBy: 'john.doe@example.com',
            description: 'Increased crash reporting sample rate',
            isActive: true,
          },
          {
            version: 4,
            createdAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
            createdBy: 'jane.smith@example.com',
            description: 'Added payment_error to critical events',
            isActive: false,
          },
          {
            version: 3,
            createdAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString(),
            createdBy: 'john.doe@example.com',
            description: 'Reduced default sample rate to 50%',
            isActive: false,
          },
          {
            version: 2,
            createdAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
            createdBy: 'admin@example.com',
            description: 'Added blacklist filters for sensitive data',
            isActive: false,
          },
          {
            version: 1,
            createdAt: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(),
            createdBy: 'admin@example.com',
            description: 'Initial configuration',
            isActive: false,
          },
        ]);
      }
    } catch {
      setIsError(true);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadVersions();
  }, []);

  const formatDate = (dateString: string) => {
    if (!dateString || !dayjs(dateString).isValid()) return '-';
    return dayjs(dateString).format('MMM D, YYYY HH:mm');
  };

  const formatRelativeTime = (dateString: string) => {
    if (!dateString || !dayjs(dateString).isValid()) return '';
    return dayjs(dateString).fromNow();
  };

  const activeVersion = versions.find(v => v.isActive);

  // Loading state
  if (isLoading) {
    return (
      <Box className={classes.pageContainer}>
        <Box className={classes.pageHeader}>
          <Box className={classes.titleSection}>
            <Text className={classes.pageTitle}>SDK Configuration</Text>
          </Box>
        </Box>
        <Box className={classes.issueListTable}>
          <Box className={classes.tableHeader}>
            <Box className={classes.tableHeaderContent}>
              <IconHistory size={18} color="#0ba09a" />
              <Text className={classes.tableHeaderTitle}>Configuration Versions</Text>
            </Box>
          </Box>
          <Box className={classes.issueTableWrapper} style={{ padding: '2rem', textAlign: 'center' }}>
            <Loader size="sm" color="teal" />
          </Box>
        </Box>
      </Box>
    );
  }

  // Error state
  if (isError) {
    return (
      <Box className={classes.pageContainer}>
        <Box className={classes.pageHeader}>
          <Box className={classes.titleSection}>
            <Text className={classes.pageTitle}>SDK Configuration</Text>
          </Box>
        </Box>
        <Box className={classes.issueListTable}>
          <Box className={classes.tableHeader}>
            <Box className={classes.tableHeaderContent}>
              <IconHistory size={18} color="#0ba09a" />
              <Text className={classes.tableHeaderTitle}>Configuration Versions</Text>
            </Box>
          </Box>
          <Box className={classes.issueTableWrapper} style={{ padding: '2rem' }}>
            <Text size="sm" c="red" ta="center">
              Failed to load configuration versions
            </Text>
          </Box>
        </Box>
      </Box>
    );
  }

  // Empty state
  if (versions.length === 0) {
    return (
      <Box className={classes.pageContainer}>
        <Box className={classes.pageHeader}>
          <Box className={classes.headerGroup}>
            <Box className={classes.titleSection}>
              <Text className={classes.pageTitle}>SDK Configuration</Text>
            </Box>
            <Button
              leftSection={<IconPlus size={16} />}
              onClick={() => onCreateNew()}
              variant="filled"
              color="teal"
            >
              Create Configuration
            </Button>
          </Box>
        </Box>
        <Box className={classes.issueListTable}>
          <Box className={classes.tableHeader}>
            <Box className={classes.tableHeaderContent}>
              <IconHistory size={18} color="#0ba09a" />
              <Text className={classes.tableHeaderTitle}>Configuration Versions</Text>
            </Box>
          </Box>
          <Box className={classes.emptyTableState}>
            <Box className={classes.emptyTableIcon}>⚙️</Box>
            <Text className={classes.emptyTableText}>No configuration versions found</Text>
            <Text size="xs" c="dimmed" mt="xs">Create your first configuration to get started</Text>
          </Box>
        </Box>
      </Box>
    );
  }

  return (
    <Box className={classes.pageContainer}>
      {/* Page Header */}
      <Box className={classes.pageHeader}>
        <Box className={classes.headerGroup}>
          <Box className={classes.titleSection}>
            <Text className={classes.pageTitle}>SDK Configuration</Text>
          </Box>
          <Button
            leftSection={<IconPlus size={16} />}
            onClick={() => onCreateNew(activeVersion?.version)}
            variant="filled"
            color="teal"
          >
            Create New Version
          </Button>
        </Box>
      </Box>

      {/* Active Version Card */}
      {activeVersion && (
        <Box className={classes.activeVersionCard}>
          <Group justify="space-between" align="center">
            <Group gap="md">
              <Box className={classes.activeVersionIcon}>
                <IconRocket size={24} color="white" />
              </Box>
              <Box>
                <Group gap="xs" mb={4}>
                  <Text fw={700} size="lg">Version {activeVersion.version}</Text>
                  <Badge color="green" variant="filled" size="sm">
                    Active
                  </Badge>
                </Group>
                <Text size="sm" c="dimmed">
                  {activeVersion.description || 'Currently deployed configuration'}
                </Text>
                <Text size="xs" c="dimmed" mt={4}>
                  Updated {formatRelativeTime(activeVersion.createdAt)} by {activeVersion.createdBy}
                </Text>
              </Box>
            </Group>
            <Group gap="xs">
              <Button
                variant="light"
                color="green"
                size="sm"
                leftSection={<IconEye size={16} />}
                onClick={() => onViewVersion(activeVersion.version)}
              >
                View
              </Button>
              <Tooltip label="Create new version based on this" withArrow>
                <Button
                  variant="light"
                  size="sm"
                  leftSection={<IconCopy size={16} />}
                  onClick={() => onCreateNew(activeVersion.version)}
                >
                  Duplicate
                </Button>
              </Tooltip>
            </Group>
          </Group>
        </Box>
      )}

      {/* Version History Table */}
      <Box className={`${classes.issueListTable} ${classes.fadeIn}`}>
        <Box className={classes.tableHeader}>
          <Box className={classes.tableHeaderContent}>
            <IconHistory size={18} color="#0ba09a" />
            <Text className={classes.tableHeaderTitle}>Version History</Text>
            <Badge size="sm" variant="light" color="teal" ml="auto">
              {versions.length} versions
            </Badge>
          </Box>
        </Box>
        <Box className={classes.issueTableWrapper}>
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Version</Table.Th>
                <Table.Th>Description</Table.Th>
                <Table.Th>Created By</Table.Th>
                <Table.Th>Created At</Table.Th>
                <Table.Th style={{ textAlign: 'right' }}>Actions</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {versions.map((version) => (
                <Table.Tr
                  key={version.version}
                  onClick={() => onViewVersion(version.version)}
                  style={{ cursor: 'pointer' }}
                >
                  <Table.Td>
                    <Group gap="xs">
                      <span className={classes.versionBadge}>
                        v{version.version}
                      </span>
                      {version.isActive && (
                        <Badge size="xs" color="green" variant="filled">
                          Active
                        </Badge>
                      )}
                    </Group>
                  </Table.Td>
                  <Table.Td>
                    <Text fw={500} size="sm" lineClamp={1}>
                      {version.description || '-'}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm" c="dimmed">
                      {version.createdBy}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Box>
                      <Text className={classes.dateCell}>
                        {formatDate(version.createdAt)}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {formatRelativeTime(version.createdAt)}
                      </Text>
                    </Box>
                  </Table.Td>
                  <Table.Td>
                    <Group gap="xs" justify="flex-end" onClick={(e) => e.stopPropagation()}>
                      <Tooltip label="View configuration" withArrow>
                        <ActionIcon
                          variant="subtle"
                          color="teal"
                          onClick={() => onViewVersion(version.version)}
                        >
                          <IconEye size={18} />
                        </ActionIcon>
                      </Tooltip>
                      <Tooltip label="Create new version based on this" withArrow>
                        <ActionIcon
                          variant="subtle"
                          color="gray"
                          onClick={() => onCreateNew(version.version)}
                        >
                          <IconCopy size={18} />
                        </ActionIcon>
                      </Tooltip>
                      {version.isActive && (
                        <Tooltip label="Currently active" withArrow>
                          <ActionIcon variant="subtle" color="green">
                            <IconCheck size={18} />
                          </ActionIcon>
                        </Tooltip>
                      )}
                    </Group>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Box>
      </Box>
    </Box>
  );
}
