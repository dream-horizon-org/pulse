import {
  Button,
  Text,
  Badge,
  Group,
  TextInput,
  Stack,
  Paper,
  Table,
  Checkbox,
  ActionIcon,
  Tooltip,
  Pagination,
  Loader,
  Box,
  Alert,
} from "@mantine/core";
import { useNavigate } from "react-router-dom";
import {
  IconSearch,
  IconDownload,
  IconExternalLink,
  IconTag,
  IconTrash,
  IconX,
  IconVideo,
  IconSettings,
  IconClick,
  IconBug,
  IconAlertCircle,
  IconActivity,
  IconClock,
  IconInfoCircle,
  IconArrowLeft,
} from "@tabler/icons-react";
import { useState, useEffect } from "react";
import classes from "./SessionReplaySessions.module.css";
import { useAnalytics } from "../../hooks/useAnalytics";
import {
  sessionReplayService,
  SessionResponse,
  GetSessionsResponse,
  GetSessionsRequest,
} from "../../services/sessionReplay";
import { AdvancedFilterBuilder } from "./components/AdvancedFilterBuilder";
import { FilterGroup, getFieldDefinition, OPERATOR_LABELS } from "../../services/sessionReplay/filterConfig";
import { useQuickFilters as useQuickFiltersConfig } from "./hooks/useQuickFilters";
import { useSessionReplayFilters } from "../../contexts/SessionReplayFilterContext";

// Helper to map icon names to icon components
const getIconComponent = (iconName: string) => {
  const iconMap: Record<string, any> = {
    'alert-circle': IconAlertCircle,
    'bug': IconBug,
    'click': IconClick,
    'activity': IconActivity,
    'clock': IconClock,
  };
  return iconMap[iconName] || IconTag;
};

/**
 * Session Replay Sessions Page (Filtered Table)
 * 
 * Displays a filterable, searchable table of sessions.
 * Receives filter context from Insights page drill-down.
 * 
 * Route: /session-replay/sessions
 */
export function SessionReplaySessions() {
  const { trackClick } = useAnalytics("SessionReplaySessions");
  const navigate = useNavigate();
  const { state: filterState, actions: filterActions } = useSessionReplayFilters();
  
  // Fetch configuration from API
  const { quickFilters: quickFiltersConfig, loading: quickFiltersLoading } = useQuickFiltersConfig();
  
  // Local state
  const [loading, setLoading] = useState(true);
  const [sessionsData, setSessionsData] = useState<GetSessionsResponse | null>(null);
  const [selectedSessions, setSelectedSessions] = useState<string[]>([]);
  const [advancedFilterOpen, setAdvancedFilterOpen] = useState(false);

  // Fetch sessions
  const fetchSessions = async () => {
    setLoading(true);
    try {
      const requestParams: GetSessionsRequest = {
        filters: filterState.quickFilters,
        page: filterState.currentPage,
        pageSize: filterState.pageSize,
      };
      
      // Date range
      if (filterState.dateRange.preset !== 'custom') {
        requestParams.dateRange = {
          start: new Date(Date.now() - parseInt(filterState.dateRange.preset) * 24 * 60 * 60 * 1000).toISOString(),
          end: new Date().toISOString(),
        };
      } else if (filterState.dateRange.from && filterState.dateRange.to) {
        requestParams.dateRange = {
          start: new Date(filterState.dateRange.from).toISOString(),
          end: new Date(filterState.dateRange.to).toISOString(),
        };
      }
      
      // Search query
      if (filterState.searchQuery) {
        requestParams.searchQuery = filterState.searchQuery;
      }
      
      // Advanced filters
      if (filterState.advancedFilters) {
        requestParams.advancedFilters = filterState.advancedFilters;
      }
      
      // Drill-down filter (from Insights page)
      if (filterState.drillDown.type) {
        requestParams.drillDown = {
          type: filterState.drillDown.type,
          value: filterState.drillDown.value,
        };
      }
      
      const response = await sessionReplayService.getSessions(requestParams);
      setSessionsData(response);
    } catch (error) {
      console.error("Failed to fetch sessions:", error);
    } finally {
      setLoading(false);
    }
  };

  // Fetch sessions when filters change
  useEffect(() => {
    fetchSessions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    filterState.currentPage,
    filterState.dateRange,
    filterState.quickFilters,
    filterState.advancedFilters,
    filterState.drillDown,
  ]);

  // Debounced search
  useEffect(() => {
    const timer = setTimeout(() => {
      if (filterState.currentPage === 1) {
        fetchSessions();
      } else {
        filterActions.setPage(1);
      }
    }, 500);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterState.searchQuery]);

  // Helper functions
  const formatTimestamp = (isoString: string) => {
    const date = new Date(isoString);
    return date.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    });
  };

  const formatDuration = (ms: number) => {
    const seconds = Math.floor(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    
    if (minutes === 0) {
      return `${seconds}s`;
    }
    return `${minutes}m ${remainingSeconds}s`;
  };

  const toggleSessionSelection = (sessionId: string) => {
    setSelectedSessions(prev =>
      prev.includes(sessionId)
        ? prev.filter(id => id !== sessionId)
        : [...prev, sessionId]
    );
  };

  const toggleSelectAll = () => {
    if (!sessionsData) return;
    
    if (selectedSessions.length === sessionsData.sessions.length) {
      setSelectedSessions([]);
    } else {
      setSelectedSessions(sessionsData.sessions.map(s => s.id));
    }
  };

  const toggleQuickFilter = (filterKey: string) => {
    filterActions.setQuickFilters({
      ...filterState.quickFilters,
      [filterKey]: !filterState.quickFilters[filterKey],
    });
  };

  const clearAllFilters = () => {
    filterActions.resetFilters();
    filterActions.clearDrillDown();
  };

  const handleApplyAdvancedFilters = (filterGroup: FilterGroup) => {
    filterActions.setAdvancedFilters(filterGroup);
  };

  const removeAdvancedFilter = (conditionId: string) => {
    if (!filterState.advancedFilters) return;
    
    const updatedFilters = {
      ...filterState.advancedFilters,
      conditions: filterState.advancedFilters.conditions.filter((c: any) => c.id !== conditionId),
    };
    
    if (updatedFilters.conditions.length === 0) {
      filterActions.setAdvancedFilters(null);
    } else {
      filterActions.setAdvancedFilters(updatedFilters);
    }
  };

  const activeFiltersCount = Object.values(filterState.quickFilters).filter(Boolean).length +
    (filterState.searchQuery ? 1 : 0) +
    (filterState.advancedFilters && filterState.advancedFilters.conditions.length > 0 ? filterState.advancedFilters.conditions.length : 0) +
    (filterState.drillDown.type ? 1 : 0);

  // Handle bulk actions
  const handleBulkTag = () => {
    trackClick("BulkTag");
    console.log("Tagging sessions:", selectedSessions);
  };

  const handleBulkDelete = () => {
    trackClick("BulkDelete");
    console.log("Deleting sessions:", selectedSessions);
  };

  const handleBulkExport = () => {
    trackClick("BulkExport");
    console.log("Exporting sessions:", selectedSessions);
  };

  // Render loading state
  if (loading && !sessionsData) {
    return (
      <div className={classes.container}>
        <div className={classes.loadingContainer}>
          <Loader color="teal" size="lg" />
          <Text size="sm" c="dimmed">Loading sessions...</Text>
        </div>
      </div>
    );
  }

  // Render empty state
  if (!loading && sessionsData && sessionsData.sessions.length === 0) {
    return (
      <div className={classes.container}>
        <Stack gap="md" mb="lg">
          <Button
            variant="subtle"
            leftSection={<IconArrowLeft size={16} />}
            onClick={() => navigate('/session-replay/insights')}
          >
            Back to Insights
          </Button>
          <div>
            <h1 className={classes.title}>Session List</h1>
            <p className={classes.subtitle}>
              Filtered sessions based on your selection
            </p>
          </div>
        </Stack>

        <div className={classes.emptyState}>
          <IconVideo size={64} className={classes.emptyStateIcon} />
          <Text className={classes.emptyStateTitle}>No Sessions Found</Text>
          <Text className={classes.emptyStateDescription}>
            {activeFiltersCount > 0
              ? "Try adjusting your filters to see more results."
              : "Session replay data will appear here once your app starts sending telemetry."}
          </Text>
          {activeFiltersCount > 0 && (
            <Button variant="light" color="teal" onClick={clearAllFilters}>
              Clear All Filters
            </Button>
          )}
        </div>
      </div>
    );
  }

  const sessions = sessionsData?.sessions || [];
  const pagination = sessionsData?.pagination || {
    page: 1,
    pageSize: 10,
    total: 0,
    totalPages: 1,
  };

  return (
    <div className={classes.container}>
      {/* Header */}
      <Stack gap="md" mb="lg">
        <Group justify="space-between" align="center">
          <Button
            variant="subtle"
            leftSection={<IconArrowLeft size={16} />}
            onClick={() => navigate('/session-replay/insights')}
          >
            Back to Insights
          </Button>
          <Button
            leftSection={<IconDownload size={16} />}
            variant="light"
            color="teal"
            onClick={() => trackClick("ExportSessions")}
          >
            Export
          </Button>
        </Group>
        <div>
          <h1 className={classes.title}>Session List</h1>
          <p className={classes.subtitle}>
            Watch reconstructed user sessions to understand why interactions failed, conversions dropped, or users got frustrated
          </p>
        </div>
      </Stack>

      {/* Drill-Down Banner */}
      {filterState.drillDown.type && filterState.drillDown.label && (
        <Alert
          icon={<IconInfoCircle size={16} />}
          title="Filtered View"
          color="teal"
          withCloseButton
          onClose={() => filterActions.clearDrillDown()}
          mb="lg"
        >
          Showing sessions for: <strong>{filterState.drillDown.label}</strong>
        </Alert>
      )}

      {/* Advanced Filter Builder Modal */}
      <AdvancedFilterBuilder
        opened={advancedFilterOpen}
        onClose={() => setAdvancedFilterOpen(false)}
        onApply={handleApplyAdvancedFilters}
        initialFilters={filterState.advancedFilters || undefined}
      />

      {/* Sessions Table with Integrated Filters */}
      <Paper className={classes.tableContainer} p={0} radius="md">
        {/* Table Header with Filters */}
        <Box p="md" style={{ background: 'var(--mantine-color-gray-0)', borderBottom: '1px solid var(--mantine-color-gray-2)' }}>
          <Stack gap="md">
            {/* Title and Session Count */}
            <Group justify="space-between">
              <div>
                <Text size="md" fw={600}>Sessions for Investigation</Text>
                <Text size="xs" c="dimmed">Click on any session to watch the replay and understand the full user journey</Text>
              </div>
              <Badge size="lg" variant="light" color="gray">
                {pagination.total} total sessions
              </Badge>
            </Group>

            {/* Search and Quick Filters - Single Row */}
            <Group justify="space-between" align="center">
              {/* Quick Filters - Left */}
              <Group gap="xs" style={{ flexWrap: "wrap", flex: 1 }}>
                <Text size="sm" fw={500} c="dimmed">Quick filters:</Text>
                
                {quickFiltersLoading ? (
                  <Loader size="sm" />
                ) : (
                  quickFiltersConfig?.filters?.map((filter) => {
                    const IconComponent = getIconComponent(filter.icon);
                    const isActive = (filterState.quickFilters as any)[filter.id] === true;
                    
                    return (
                      <Badge
                        key={filter.id}
                        variant={isActive ? "filled" : "light"}
                        color="teal"
                        style={{ cursor: "pointer" }}
                        onClick={() => toggleQuickFilter(filter.id)}
                        leftSection={<IconComponent size={12} />}
                      >
                        {filter.label}
                      </Badge>
                    );
                  })
                )}
                
                <Button
                  variant="subtle"
                  color="teal"
                  size="xs"
                  leftSection={<IconSettings size={14} />}
                  onClick={() => setAdvancedFilterOpen(true)}
                >
                  Advanced Filters
                </Button>
                {activeFiltersCount > 0 && (
                  <>
                    <Badge variant="filled" color="gray" size="sm">
                      {activeFiltersCount} active
                    </Badge>
                    <ActionIcon
                      variant="subtle"
                      color="gray"
                      size="sm"
                      onClick={clearAllFilters}
                    >
                      <IconX size={14} />
                    </ActionIcon>
                  </>
                )}
              </Group>

              {/* Search - Right */}
              <TextInput
                leftSection={<IconSearch size={16} />}
                placeholder="Search by userId, sessionId..."
                value={filterState.searchQuery}
                onChange={(e) => filterActions.setSearchQuery(e.target.value)}
                style={{ minWidth: 300, maxWidth: 400 }}
              />
            </Group>

            {/* Active Advanced Filters Display */}
            {filterState.advancedFilters && filterState.advancedFilters.conditions.length > 0 && (
              <Stack gap="xs">
                <Text size="xs" fw={500} c="dimmed">Advanced Filters ({filterState.advancedFilters.operator}):</Text>
                <Group gap="xs" style={{ flexWrap: "wrap" }}>
                  {filterState.advancedFilters.conditions.map((condition: any) => {
                    const fieldDef = getFieldDefinition(condition.field);
                    return (
                      <Badge
                        key={condition.id}
                        variant="light"
                        color="indigo"
                        size="md"
                        rightSection={
                          <ActionIcon
                            size="xs"
                            color="indigo"
                            radius="xl"
                            variant="transparent"
                            onClick={() => removeAdvancedFilter(condition.id)}
                          >
                            <IconX size={10} />
                          </ActionIcon>
                        }
                      >
                        {fieldDef?.label} {OPERATOR_LABELS[condition.operator as keyof typeof OPERATOR_LABELS]} {
                          typeof condition.value === 'boolean' 
                            ? (condition.value ? 'Yes' : 'No')
                            : condition.value
                        }
                      </Badge>
                    );
                  })}
                </Group>
              </Stack>
            )}
          </Stack>
        </Box>
        <Table highlightOnHover horizontalSpacing="md" verticalSpacing="sm">
          <Table.Thead>
            <Table.Tr>
              <Table.Th style={{ width: 40 }}>
                <Checkbox
                  checked={selectedSessions.length === sessions.length && sessions.length > 0}
                  indeterminate={selectedSessions.length > 0 && selectedSessions.length < sessions.length}
                  onChange={toggleSelectAll}
                />
              </Table.Th>
              <Table.Th>Start Time</Table.Th>
              <Table.Th>Duration</Table.Th>
              <Table.Th>User</Table.Th>
              <Table.Th>Quality</Table.Th>
              <Table.Th>Issues</Table.Th>
              <Table.Th>Platform</Table.Th>
              <Table.Th>Journey</Table.Th>
              <Table.Th style={{ width: 100 }}>Actions</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {sessions.map((session: SessionResponse) => (
              <Table.Tr key={session.id} className={classes.tableRow}>
                <Table.Td>
                  <Checkbox
                    checked={selectedSessions.includes(session.id)}
                    onChange={() => toggleSessionSelection(session.id)}
                  />
                </Table.Td>
                
                <Table.Td>
                  <Text size="sm">{formatTimestamp(session.startTime)}</Text>
                </Table.Td>
                
                <Table.Td>
                  <Text size="sm">{formatDuration(session.duration)}</Text>
                </Table.Td>
                
                <Table.Td>
                  <Text size="sm" fw={500}>
                    {session.isAnonymous ? "Anonymous" : session.userId}
                  </Text>
                </Table.Td>
                
                <Table.Td>
                  <Text 
                    size="sm" 
                    fw={600}
                    c={
                      session.interactionQuality >= 8 ? "teal" :
                      session.interactionQuality >= 6 ? "orange" : "red"
                    }
                  >
                    {session.interactionQuality.toFixed(1)}
                  </Text>
                </Table.Td>
                
                <Table.Td>
                  {!session.issueSummary.hasIssues ? (
                    <Badge color="teal" variant="light" size="sm">
                      Clean
                    </Badge>
                  ) : (
                    <Group gap={4} style={{ flexWrap: "wrap" }}>
                      {session.issueSummary.crashed && (
                        <Badge color="red" variant="filled" size="sm">
                          Crashed
                        </Badge>
                      )}
                      {session.issueSummary.failedInteractions && (
                        <Badge color="red" variant="light" size="sm">
                          Failed
                        </Badge>
                      )}
                      {session.issueSummary.hasErrors && (
                        <Badge color="orange" variant="light" size="sm">
                          {session.errors} {session.errors > 1 ? 'Errors' : 'Error'}
                        </Badge>
                      )}
                      {session.issueSummary.hasFrustration && (
                        <Badge color="pink" variant="light" size="sm">
                          Rage
                        </Badge>
                      )}
                      {session.issueSummary.isSlow && (
                        <Badge color="yellow" variant="light" size="sm">
                          Slow
                        </Badge>
                      )}
                    </Group>
                  )}
                </Table.Td>
                
                <Table.Td>
                  <Tooltip label={`Device: ${session.device} • Browser: ${session.browser} • OS: ${session.os}`}>
                    <Badge 
                      size="sm"
                      variant="light"
                      color={
                        session.device === 'iOS' ? 'blue' :
                        session.device === 'Android' ? 'green' : 'gray'
                      }
                    >
                      {session.device}
                    </Badge>
                  </Tooltip>
                </Table.Td>
                
                <Table.Td>
                  <Tooltip label={session.journey.join(" → ")}>
                    <Text size="xs" c="dimmed" className={classes.journey}>
                      {session.journey.slice(0, 3).join(" → ")}
                      {session.journey.length > 3 && " ..."}
                    </Text>
                  </Tooltip>
                </Table.Td>
                
                <Table.Td>
                  <Group gap={4}>
                    <Tooltip label="Watch session">
                      <ActionIcon 
                        variant="light" 
                        color="teal"
                        onClick={() => {
                          trackClick(`WatchSession_${session.sessionId}`);
                          navigate(`/session-replay/${session.sessionId}`);
                        }}
                      >
                        <IconVideo size={16} />
                      </ActionIcon>
                    </Tooltip>
                    <Tooltip label="Open in new tab">
                      <ActionIcon 
                        variant="subtle" 
                        color="gray"
                        onClick={() => {
                          trackClick(`OpenSession_${session.sessionId}`);
                          window.open(`/session-replay/${session.sessionId}`, '_blank');
                        }}
                      >
                        <IconExternalLink size={16} />
                      </ActionIcon>
                    </Tooltip>
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Paper>

      {/* Bottom Bar */}
      <Paper className={classes.bottomBar} p="md" radius="md">
        <Group justify="space-between" style={{ flexWrap: "wrap", gap: 16 }}>
          <Group gap="sm">
            {selectedSessions.length > 0 && (
              <>
                <Text size="sm" c="dimmed">
                  {selectedSessions.length} selected
                </Text>
                <Button
                  variant="light"
                  color="teal"
                  size="sm"
                  leftSection={<IconTag size={14} />}
                  onClick={handleBulkTag}
                >
                  Tag
                </Button>
                <Button
                  variant="light"
                  color="red"
                  size="sm"
                  leftSection={<IconTrash size={14} />}
                  onClick={handleBulkDelete}
                >
                  Delete
                </Button>
                <Button
                  variant="light"
                  color="gray"
                  size="sm"
                  leftSection={<IconDownload size={14} />}
                  onClick={handleBulkExport}
                >
                  Export IDs
                </Button>
                <ActionIcon
                  variant="subtle"
                  color="gray"
                  onClick={() => setSelectedSessions([])}
                >
                  <IconX size={16} />
                </ActionIcon>
              </>
            )}
          </Group>
          <Pagination
            total={pagination.totalPages}
            value={filterState.currentPage}
            onChange={(page) => filterActions.setPage(page)}
            color="teal"
            size="sm"
          />
        </Group>
      </Paper>
    </div>
  );
}
