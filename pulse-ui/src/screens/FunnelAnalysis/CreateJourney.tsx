import { useMemo, useState } from "react";
import { ActionIcon, Box, Select, Text, Group } from "@mantine/core";
import { DateTimePicker } from "@mantine/dates";
import { IconArrowLeft } from "@tabler/icons-react";
import { useNavigate, useParams, generatePath } from "react-router-dom";
import { useCreateFunnelJourney } from "../../hooks/useCreateFunnelJourney";
import { ROUTES } from "../../constants";
import classes from "./FunnelAnalysis.module.css";
import {
  GlobalFilterBar,
  ActiveFilter,
} from "./components/GlobalFilterBar";
import { JourneyExplorer } from "./components/JourneyExplorer";
import { DATE_RANGE_OPTIONS } from "./mockData";
import { useGetFunnelEvents, useGetFunnelFilters } from "../../hooks/useGetFunnelData";

export function CreateJourney() {
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string }>();

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [tags, setTags] = useState<string[]>([]);
  const [rollingType, setRollingType] = useState<"RECURRING" | "ONCE">("RECURRING");
  const [dateRange, setDateRange] = useState("7d");
  const [customStartDate, setCustomStartDate] = useState<Date | null>(null);
  const [customEndDate, setCustomEndDate] = useState<Date | null>(null);
  const [expiryDate, setExpiryDate] = useState<Date | null>(null);
  const [filters, setFilters] = useState<ActiveFilter[]>([]);

  const { data: eventsData } = useGetFunnelEvents();
  const { data: filtersData } = useGetFunnelFilters();

  const availableEvents = eventsData?.data?.events ?? [];

  const EXPECTED_FILTER_KEYS = ["OS Name", "OS Version", "App Version"];
  const filterOptions = EXPECTED_FILTER_KEYS.reduce((acc, key) => {
    acc[key] = filtersData?.data?.filters?.[key] ?? [];
    return acc;
  }, {} as Record<string, string[]>);

  const apiFilters = useMemo(
    () =>
      filters.map((f) => ({
        field: f.property,
        operator: "EQ" as const,
        value: f.value,
      })),
    [filters],
  );

  const { mutate: createJourney, isPending: isCreating } = useCreateFunnelJourney();

  const handleCreate = (config: any) => {
    createJourney(
      {
        name,
        description,
        tags,
        rollingType,
        kind: "JOURNEY",
        timeRange: config.timeRange,
        filters: apiFilters,
        expiryDate: rollingType === "RECURRING" && expiryDate ? expiryDate.toISOString() : undefined,
        ...config,
      },
      {
        onSuccess: (res) => {
          if (projectId && res.data) {
            navigate(
              generatePath(ROUTES.FUNNEL_JOURNEY_DETAIL.path, {
                projectId,
                id: res.data.id,
              })
            );
          }
        },
      }
    );
  };

  const goBack = () => {
    if (projectId) {
      navigate(generatePath(ROUTES.FUNNEL_ANALYSIS.path, { projectId }));
      return;
    }
    navigate(-1);
  };

  return (
    <Box className={classes.shell}>
      <Box className={classes.topBar}>
        <Box className={classes.topBarLeft}>
          <Group gap="sm">
            <ActionIcon variant="subtle" color="gray" onClick={goBack} size="lg">
              <IconArrowLeft size={20} />
            </ActionIcon>
            <Text className={classes.moduleTitle}>Create Journey</Text>
          </Group>
        </Box>

        <Box className={classes.topBarRight} style={{ display: "flex", gap: 12, alignItems: "center" }}>
        </Box>
      </Box>

      <GlobalFilterBar
        filters={filters}
        onFiltersChange={setFilters}
        filterOptions={filterOptions}
      />

      <JourneyExplorer
        name={name}
        onNameChange={setName}
        description={description}
        onDescriptionChange={setDescription}
        tags={tags}
        onTagsChange={setTags}
        rollingType={rollingType}
        onRollingTypeChange={setRollingType}
        dateRange={dateRange}
        onDateRangeChange={setDateRange}
        customStartDate={customStartDate}
        onCustomStartDateChange={setCustomStartDate}
          customEndDate={customEndDate}
          onCustomEndDateChange={setCustomEndDate}
          expiryDate={expiryDate}
          onExpiryDateChange={setExpiryDate}
          availableEvents={availableEvents}
        onCreate={handleCreate}
        isCreating={isCreating}
        filters={filters}
      />
    </Box>
  );
}
