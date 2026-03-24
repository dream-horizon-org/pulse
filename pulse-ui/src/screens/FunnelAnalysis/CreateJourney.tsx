import { useState } from "react";
import { ActionIcon, Box, Select, Text, Group } from "@mantine/core";
import { IconArrowLeft } from "@tabler/icons-react";
import { useNavigate, useParams, generatePath } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { ROUTES } from "../../constants";
import { createFunnelJourney } from "../../services/funnels.service";
import classes from "./FunnelAnalysis.module.css";
import {
  GlobalFilterBar,
  ActiveFilter,
} from "./components/GlobalFilterBar";
import { JourneyExplorer } from "./components/JourneyExplorer";
import { DATE_RANGE_OPTIONS } from "./mockData";
import {
  useGetFunnelEvents,
  useGetFunnelFilters,
} from "../../hooks/useGetFunnelData";

export function CreateJourney() {
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string }>();

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [dateRange, setDateRange] = useState("7d");
  const [filters, setFilters] = useState<ActiveFilter[]>([]);

  const { data: eventsData } = useGetFunnelEvents();
  const { data: filtersData } = useGetFunnelFilters();

  const availableEvents = eventsData?.data?.events ?? [];
  const filterOptions = filtersData?.data?.filters ?? {};

  const { mutate: createJourney, isPending: isCreating } = useMutation({
    mutationFn: createFunnelJourney,
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
  });

  const handleCreate = (config: any) => {
    createJourney({
      name,
      description,
      kind: "JOURNEY",
      ...config,
    });
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

        <Box className={classes.topBarRight}>
          <Select
            data={DATE_RANGE_OPTIONS}
            value={dateRange}
            onChange={(val) => {
              setDateRange(val || "7d");
            }}
            size="xs"
            style={{ width: 160 }}
            allowDeselect={false}
          />
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
        dateRange={dateRange}
        availableEvents={availableEvents}
        onCreate={handleCreate}
        isCreating={isCreating}
      />
    </Box>
  );
}
