import {
  Button,
  Text,
  Badge,
  Group,
  TextInput,
  Select,
  Stack,
  Paper,
  Table,
  Checkbox,
  ActionIcon,
  Tooltip,
  Pagination,
  Loader,
  Divider,
  Box,
} from "@mantine/core";
import { useNavigate } from "react-router-dom";
import { DateInput } from "@mantine/dates";
import {
  IconSearch,
  IconDownload,
  IconCalendar,
  IconExternalLink,
  IconTag,
  IconTrash,
  IconAlertTriangle,
  IconPointer,
  IconClock,
  IconX,
  IconVideo,
  IconSettings,
  IconClick,
  IconBug,
  IconAlertCircle,
  IconActivity,
  IconInfoCircle,
} from "@tabler/icons-react";
import { useState, useEffect } from "react";
import { SessionReplayProps } from "./SessionReplay.interface";
import classes from "./SessionReplay.module.css";
import { useAnalytics } from "../../hooks/useAnalytics";
import {
  sessionReplayService,
  SessionResponse,
  GetSessionsResponse,
  GetSessionsRequest,
} from "../../services/sessionReplay";
import { AdvancedFilterBuilder } from "./components/AdvancedFilterBuilder";
import { InsightsDashboard } from "./components/InsightsDashboard";
import { FilterGroup, getFieldDefinition, OPERATOR_LABELS } from "../../services/sessionReplay/filterConfig";
import { useDateRangeConfig } from "./hooks/useDateRangeConfig";
import { useQuickFilters as useQuickFiltersConfig } from "./hooks/useQuickFilters";

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

export function SessionReplay(_props: SessionReplayProps) {
  const { trackClick } = useAnalytics("SessionReplay");
  const navigate = useNavigate();
  
  // Fetch configuration from API
  const { config: dateRangeConfig, loading: dateRangeLoading } = useDateRangeConfig();
  const { quickFilters: quickFiltersConfig, loading: quickFiltersLoading } = useQuickFiltersConfig();
  
  // State
  const [loading, setLoading] = useState(true);
  const [sessionsData, setSessionsData] = useState<GetSessionsResponse | null>(null);
  const [selectedSessions, setSelectedSessions] = useState<string[]>([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize] = useState(10);
  const [advancedFilterOpen, setAdvancedFilterOpen] = useState(false);
  const [advancedFilters, setAdvancedFilters] = useState<FilterGroup | null>(null);
  
  // Filter state
  const [dateRange, setDateRange] = useState<string>("");
  const [customDateRange, setCustomDateRange] = useState<{
    from: string | null;
    to: string | null;
  }>({
    from: null,
    to: null,
  });
  const [searchQuery, setSearchQuery] = useState("");
  const [quickFilters, setQuickFilters] = useState<Record<string, boolean>>({});

  // Initialize quick filters from API config
  useEffect(() => {
    if (quickFiltersConfig && Object.keys(quickFilters).length === 0) {
      const initialFilters: Record<string, boolean> = {};
      quickFiltersConfig.filters.forEach((filter) => {
        initialFilters[filter.id] = false;
      });
      setQuickFilters(initialFilters);
    }
  }, [quickFiltersConfig, quickFilters]);

  // Initialize date range from API config
  useEffect(() => {
    if (dateRangeConfig && !dateRange) {
      setDateRange(dateRangeConfig.defaultValue);
    }
  }, [dateRangeConfig, dateRange]);

  // Fetch sessions
  const fetchSessions = async () => {
    setLoading(true);
    try {
      const requestParams: GetSessionsRequest = {
        filters: quickFilters,
        page: currentPage,
        pageSize,
      };
      
      // Add optional fields only if they have values
      if (dateRange !== 'custom') {
        requestParams.dateRange = {
          start: new Date(Date.now() - parseInt(dateRange) * 24 * 60 * 60 * 1000).toISOString(),
          end: new Date().toISOString(),
        };
      } else if (customDateRange.from && customDateRange.to) {
        // Use custom date range if selected
        requestParams.dateRange = {
          start: new Date(customDateRange.from).toISOString(),
          end: new Date(customDateRange.to).toISOString(),
        };
      }
      
      if (searchQuery) {
        requestParams.searchQuery = searchQuery;
      }
      
      if (advancedFilters) {
        requestParams.advancedFilters = advancedFilters;
      }
      
      const response = await sessionReplayService.getSessions(requestParams);
      setSessionsData(response);
    } catch (error) {
      console.error("Failed to fetch sessions:", error);
    } finally {
      setLoading(false);
    }
  };

  // Fetch sessions on mount and when filters change
  useEffect(() => {
    fetchSessions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentPage, dateRange, quickFilters, advancedFilters]);

  // Debounced search
  useEffect(() => {
    const timer = setTimeout(() => {
      if (currentPage === 1) {
        fetchSessions();
      } else {
        setCurrentPage(1);
      }
    }, 500);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchQuery]);

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
    setQuickFilters(prev => ({
      ...prev,
      [filterKey]: !prev[filterKey],
    }));
    setCurrentPage(1);
  };

  const clearAllFilters = () => {
    setDateRange(dateRangeConfig?.defaultValue || "7d");
    setSearchQuery("");
    // Reset all quick filters to false
    const resetFilters: Record<string, boolean> = {};
    Object.keys(quickFilters).forEach(key => {
      resetFilters[key] = false;
    });
    setQuickFilters(resetFilters);
    setAdvancedFilters(null);
    setCurrentPage(1);
  };

  const handleApplyAdvancedFilters = (filterGroup: FilterGroup) => {
    setAdvancedFilters(filterGroup);
    setCurrentPage(1);
  };

  const removeAdvancedFilter = (conditionId: string) => {
    if (!advancedFilters) return;
    
    const updatedFilters = {
      ...advancedFilters,
      conditions: advancedFilters.conditions.filter(c => c.id !== conditionId),
    };
    
    if (updatedFilters.conditions.length === 0) {
      setAdvancedFilters(null);
    } else {
      setAdvancedFilters(updatedFilters);
    }
    setCurrentPage(1);
  };

  const activeFiltersCount = Object.values(quickFilters).filter(Boolean).length +
    (searchQuery ? 1 : 0) +
    (dateRange !== (dateRangeConfig?.defaultValue || '7d') ? 1 : 0) +
    (advancedFilters && advancedFilters.conditions.length > 0 ? advancedFilters.conditions.length : 0);

  // Handle bulk actions
  const handleBulkTag = () => {
    trackClick("BulkTag");
    console.log("Tagging sessions:", selectedSessions);
    // Implement tag modal/dialog
  };

  const handleBulkDelete = () => {
    trackClick("BulkDelete");
    console.log("Deleting sessions:", selectedSessions);
    // Implement delete confirmation
  };

  const handleBulkExport = () => {
    trackClick("BulkExport");
    console.log("Exporting sessions:", selectedSessions);
    // Implement export
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
        <div className={classes.header}>
          <div>
            <h1 className={classes.title}>Session Replay</h1>
            <p className={classes.subtitle}>
              Monitor and replay user sessions to identify issues, improve UX, and debug faster
            </p>
          </div>
        </div>

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

  const metrics = sessionsData?.metrics || {
    totalSessions: 0,
    criticalInteractions: [],
    estimatedImpact: {
      totalRevenueAtRisk: 0,
      revenueAtRiskPeriod: 'week' as const,
      affectedUsers: 0,
      totalUsers: 0,
      affectedUsersPercentage: 0,
      conversionImpact: 0,
      conversionBaseline: 0,
      supportTicketCorrelation: undefined,
    },
    topIssueHotspots: [],
    topErrorPatterns: [],
    comparison: {
      currentPeriod: {
        start: new Date().toISOString(),
        end: new Date().toISOString(),
        label: 'Today',
      },
      comparisonPeriod: {
        start: new Date().toISOString(),
        end: new Date().toISOString(),
        label: 'Yesterday',
      },
      totalSessions: {
        current: 0,
        previous: 0,
        change: 0,
        changePercent: 0,
      },
      sessionsWithIssues: {
        current: 0,
        currentPercent: 0,
        previous: 0,
        previousPercent: 0,
        change: 0,
        trend: 'stable' as const,
      },
      topDegradedFlows: [],
      topImprovedFlows: [],
    },
    // Legacy fields for backward compatibility
    sessionsWithIssues: 0,
    issueRate: 0,
    cleanSessions: 0,
    cleanRate: 0,
    avgInteractionQuality: undefined,
    qualityTrend: undefined,
    issueBreakdown: {
      failedInteractions: 0,
      errorsAndCrashes: 0,
      frustrationSignals: 0,
      dropOffs: 0,
    },
  };

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
      <div className={classes.header}>
        <div>
          <h1 className={classes.title}>Session Replay</h1>
          <p className={classes.subtitle}>
            Watch reconstructed user sessions to understand why interactions failed, conversions dropped, or users got frustrated
          </p>
        </div>
        <Button
          leftSection={<IconDownload size={16} />}
          variant="light"
          color="teal"
          onClick={() => trackClick("ExportSessions")}
        >
          Export
        </Button>
      </div>
      
      {/* Date Range Controls - All in one row */}
      <Group gap="sm" mb="md">
        <Select
          leftSection={<IconCalendar size={16} />}
          placeholder="Date range"
          value={dateRange}
          onChange={(value) => { 
            setDateRange(value ?? (dateRangeConfig?.defaultValue || '7d')); 
            if (value !== 'custom') {
              setCustomDateRange({ from: null, to: null });
            }
            setCurrentPage(1); 
          }}
          data={dateRangeConfig?.options || []}
          disabled={dateRangeLoading}
          style={{ minWidth: 150, maxWidth: 200 }}
        />
        
        {/* Custom Date Range Pickers - Same row */}
        {dateRange === 'custom' && (
          <>
            <DateInput
              leftSection={<IconCalendar size={16} />}
              placeholder="From date"
              value={customDateRange.from ? new Date(customDateRange.from) : undefined}
              onChange={(date) => {
                setCustomDateRange(prev => ({ 
                  ...prev, 
                  from: date?.toISOString() ?? null 
                }));
                setCurrentPage(1);
              }}
              maxDate={customDateRange.to ? new Date(customDateRange.to) : new Date()}
              style={{ minWidth: 200 }}
              clearable
            />
            <DateInput
              leftSection={<IconCalendar size={16} />}
              placeholder="To date"
              value={customDateRange.to ? new Date(customDateRange.to) : undefined}
              onChange={(date) => {
                setCustomDateRange(prev => ({ 
                  ...prev, 
                  to: date?.toISOString() ?? null 
                }));
                setCurrentPage(1);
              }}
              minDate={customDateRange.from ? new Date(customDateRange.from) : undefined}
              maxDate={new Date()}
              style={{ minWidth: 200 }}
              clearable
            />
          </>
        )}
      </Group>

      {/* Insights Dashboard (Date-scoped only, not affected by filters) */}
      <InsightsDashboard 
        metrics={metrics} 
        onViewSession={(sessionId) => navigate(`/session-replay/${sessionId}`)} 
      />

      {/* Advanced Filter Builder Modal */}
      <AdvancedFilterBuilder
        opened={advancedFilterOpen}
        onClose={() => setAdvancedFilterOpen(false)}
        onApply={handleApplyAdvancedFilters}
        initialFilters={advancedFilters || undefined}
      />

      {/* Sessions Table with Integrated Filters */}
      <Paper className={classes.tableContainer} p={0} radius="md" mt="lg">
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
                    const isActive = (quickFilters as any)[filter.id] === true;
                    
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
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                style={{ minWidth: 300, maxWidth: 400 }}
              />
            </Group>

            {/* Active Advanced Filters Display */}
            {advancedFilters && advancedFilters.conditions.length > 0 && (
              <Stack gap="xs">
                <Text size="xs" fw={500} c="dimmed">Advanced Filters ({advancedFilters.operator}):</Text>
                <Group gap="xs" style={{ flexWrap: "wrap" }}>
                  {advancedFilters.conditions.map(condition => {
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
                        {fieldDef?.label} {OPERATOR_LABELS[condition.operator]} {
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
                
                {/* Start Time */}
                <Table.Td>
                  <Text size="sm">{formatTimestamp(session.startTime)}</Text>
                </Table.Td>
                
                {/* Duration */}
                <Table.Td>
                  <Text size="sm">{formatDuration(session.duration)}</Text>
                </Table.Td>
                
                {/* User */}
                <Table.Td>
                  <Text size="sm" fw={500}>
                    {session.isAnonymous ? "Anonymous" : session.userId}
                  </Text>
                </Table.Td>
                
                {/* Interaction Quality Score */}
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
                
                {/* Issues */}
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
                
                {/* Platform */}
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
                
                {/* Journey - Critical interaction path (max 3 hops) */}
                <Table.Td>
                  <Tooltip label={session.journey.join(" → ")}>
                    <Text size="xs" c="dimmed" className={classes.journey}>
                      {session.journey.slice(0, 3).join(" → ")}
                      {session.journey.length > 3 && " ..."}
                    </Text>
                  </Tooltip>
                </Table.Td>
                
                {/* Actions */}
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
            value={currentPage}
            onChange={setCurrentPage}
            color="teal"
            size="sm"
          />
        </Group>
      </Paper>
    </div>
  );
}
