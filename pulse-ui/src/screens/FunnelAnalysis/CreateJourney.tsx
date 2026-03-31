import { useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import {
  ActionIcon,
  Box,
  Button,
  Divider,
  Group,
  Stack,
  Stepper,
  Text,
  Title,
  useMantineTheme,
} from "@mantine/core";
import {
  IconArrowLeft,
  IconRoute,
  IconSquareRoundedX,
} from "@tabler/icons-react";
import { generatePath, useNavigate, useParams } from "react-router-dom";
import { COMMON_CONSTANTS, ROUTES } from "../../constants";
import { showNotification } from "../../helpers/showNotification";
import { useCreateFunnelJourney } from "../../hooks/useCreateFunnelJourney";
import classes from "./FunnelAnalysis.module.css";
import createFormClasses from "./FunnelJourneyCreateForm.module.css";
import {
  JOURNEY_CREATE_STEP_ERRORS,
  JOURNEY_CREATE_STEPS,
} from "./funnelJourneyCreateForm.constants";
import { ActiveFilter, GlobalFilterBar } from "./components/GlobalFilterBar";
import { JourneyExplorer } from "./components/JourneyExplorer";
import {
  useGetFunnelEvents,
  useGetFunnelFilters,
} from "../../hooks/useGetFunnelData";
import { buildRollingTimeRange } from "./mockData";

export function CreateJourney() {
  const theme = useMantineTheme();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string }>();

  const [activeStep, setActiveStep] = useState(0);

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [tags, setTags] = useState<string[]>([]);
  const [rollingType, setRollingType] = useState<"RECURRING" | "ONCE">(
    "RECURRING",
  );
  const [dateRange, setDateRange] = useState("7d");
  const [customStartDate, setCustomStartDate] = useState<Date | null>(null);
  const [customEndDate, setCustomEndDate] = useState<Date | null>(null);
  const [expiryDate, setExpiryDate] = useState<Date | null>(null);
  const [filters, setFilters] = useState<ActiveFilter[]>([]);

  const [anchorEvent, setAnchorEvent] = useState("");
  const [direction, setDirection] = useState<"forward" | "reverse">("forward");
  const [depth, setDepth] = useState(5);

  const { data: eventsData } = useGetFunnelEvents();
  const { data: filtersData } = useGetFunnelFilters();

  const availableEvents = eventsData?.data?.events ?? [];

  const EXPECTED_FILTER_KEYS = ["OS Name", "OS Version", "App Version"];
  const filterOptions = EXPECTED_FILTER_KEYS.reduce(
    (acc, key) => {
      acc[key] = filtersData?.data?.filters?.[key] ?? [];
      return acc;
    },
    {} as Record<string, string[]>,
  );

  const timeRange = useMemo(
    () =>
      buildRollingTimeRange(
        rollingType,
        dateRange,
        customStartDate,
        customEndDate,
      ),
    [rollingType, dateRange, customStartDate, customEndDate],
  );

  const apiFilters = useMemo(
    () =>
      filters.map((f) => ({
        field: f.property,
        operator: "EQ" as const,
        value: f.value,
      })),
    [filters],
  );

  const { mutate: createJourney, isPending: isCreating } =
    useCreateFunnelJourney();

  const stepValid = (index: number): boolean => {
    switch (index) {
      case 0:
        return name.trim().length > 0;
      case 1:
        if (rollingType === "ONCE") {
          return !!(customStartDate && customEndDate);
        }
        return true;
      case 2:
        return anchorEvent.trim().length > 0;
      default:
        return true;
    }
  };

  const stepErrorMessage = (index: number): string => {
    switch (index) {
      case 0:
        return JOURNEY_CREATE_STEP_ERRORS.NAME;
      case 1:
        return JOURNEY_CREATE_STEP_ERRORS.SCHEDULE_ONCE;
      case 2:
        return JOURNEY_CREATE_STEP_ERRORS.PATH;
      default:
        return "";
    }
  };

  const handleCreateJourney = () => {
    createJourney(
      {
        name,
        description,
        tags,
        rollingType,
        kind: "JOURNEY",
        timeRange,
        filters: apiFilters,
        expiryDate:
          rollingType === "RECURRING" && expiryDate
            ? expiryDate.toISOString()
            : undefined,
        direction,
        anchorEvent,
        depth,
      },
      {
        onSuccess: async () => {
          await queryClient.invalidateQueries({
            queryKey: ["funnelsJourneysList"],
          });
          if (projectId) {
            navigate(generatePath(ROUTES.FUNNEL_ANALYSIS.path, { projectId }));
          }
        },
      },
    );
  };

  const goBack = () => {
    if (projectId) {
      navigate(generatePath(ROUTES.FUNNEL_ANALYSIS.path, { projectId }));
      return;
    }
    navigate(-1);
  };

  const goNext = () => {
    if (!stepValid(activeStep)) {
      showNotification(
        COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
        stepErrorMessage(activeStep),
        <IconSquareRoundedX />,
        theme.colors.red[6],
      );
      return;
    }
    setActiveStep((s) => Math.min(s + 1, JOURNEY_CREATE_STEPS.length - 1));
  };

  const goPrev = () => {
    setActiveStep((s) => Math.max(s - 1, 0));
  };

  const onStepClick = (clicked: number) => {
    if (clicked < activeStep) {
      setActiveStep(clicked);
      return;
    }
    for (let i = 0; i < clicked; i++) {
      if (!stepValid(i)) {
        showNotification(
          COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
          stepErrorMessage(i),
          <IconSquareRoundedX />,
          theme.colors.red[6],
        );
        setActiveStep(i);
        return;
      }
    }
    setActiveStep(clicked);
  };

  return (
    <Box className={classes.shell}>
      <Box className={createFormClasses.createFormRoot}>
        <Box className={classes.topBar}>
          <Box className={classes.topBarLeft}>
            <Group gap="sm" align="center">
              <ActionIcon
                variant="subtle"
                color="gray"
                onClick={goBack}
                size="lg"
              >
                <IconArrowLeft size={20} />
              </ActionIcon>
              <Box>
                <Text className={classes.moduleTitle}>Create Journey</Text>
              </Box>
            </Group>
          </Box>
        </Box>

        <Box className={createFormClasses.createFormMain}>
          <Divider className={createFormClasses.createFormDivider} />

          <Box className={createFormClasses.createFormHeading}>
            <Title order={2} fw={700}>
              Add a new journey
            </Title>
          </Box>

          <Box className={createFormClasses.createFormBody}>
            <Box className={createFormClasses.stepperContainer}>
              <Stepper
                className={createFormClasses.stepper}
                color="blue"
                active={activeStep}
                onStepClick={onStepClick}
                orientation="vertical"
              >
                {JOURNEY_CREATE_STEPS.map((step, index) => (
                  <Stepper.Step
                    key={step.label}
                    label={step.label}
                    description={step.description}
                    className={createFormClasses.stepperItem}
                    bg={activeStep === index ? "white" : undefined}
                    classNames={{
                      stepWrapper: createFormClasses.stepperStepWrapper,
                    }}
                  />
                ))}
              </Stepper>
            </Box>

            <Box className={createFormClasses.formPanel}>
              <Box className={createFormClasses.formPanelInner}>
                <Box className={createFormClasses.formContent}>
                  {activeStep < 3 && (
                    <JourneyExplorer
                      wizardStep={activeStep as 0 | 1 | 2}
                      useExternalPathState
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
                      onCreate={() => {}}
                      isCreating={false}
                      filters={filters}
                      anchorEvent={anchorEvent}
                      onAnchorEventChange={setAnchorEvent}
                      direction={direction}
                      onDirectionChange={setDirection}
                      depth={depth}
                      onDepthChange={setDepth}
                    />
                  )}

                  {activeStep === 3 && (
                    <Stack
                      className={createFormClasses.filtersCreateStep}
                      gap="xl"
                    >
                      <Box>
                        <Text className={createFormClasses.filtersCreateIntro}>
                          Audience filters
                        </Text>
                        <Text
                          className={createFormClasses.filtersCreateHint}
                          mt="xs"
                        >
                          Optional. Choose OS, app version, or other dimensions
                          to limit which users are included when this journey is
                          computed.
                        </Text>
                      </Box>
                      <GlobalFilterBar
                        comfortable
                        className={createFormClasses.filterBarCreate}
                        filters={filters}
                        onFiltersChange={setFilters}
                        filterOptions={filterOptions}
                      />
                      <Box className={createFormClasses.finalStepCard}>
                        <Box
                          style={{
                            color: "var(--mantine-color-blue-6)",
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            width: 48,
                            height: 48,
                            borderRadius: 12,
                            background: "rgba(34, 139, 230, 0.12)",
                          }}
                        >
                          <IconRoute size={26} stroke={1.5} />
                        </Box>
                        <Text size="lg" fw={700} c="dark.7" lh={1.3}>
                          Ready to create
                        </Text>
                        <Text size="sm" c="dimmed" lh={1.55}>
                          Filters above apply to how this journey is computed.
                          After creation, open the journey detail page to
                          explore the Sankey visualization and metrics.
                        </Text>
                      </Box>
                    </Stack>
                  )}
                </Box>

                <Box className={createFormClasses.stepNavEmbedded}>
                  <Group
                    justify="space-between"
                    gap="md"
                    wrap="nowrap"
                    w="100%"
                  >
                    <Button
                      variant="outline"
                      color="blue"
                      size="md"
                      onClick={goPrev}
                      disabled={activeStep === 0}
                    >
                      Back
                    </Button>
                    {activeStep < JOURNEY_CREATE_STEPS.length - 1 ? (
                      <Button color="blue" size="md" onClick={goNext}>
                        Next
                      </Button>
                    ) : (
                      <Button
                        color="blue"
                        size="md"
                        onClick={handleCreateJourney}
                        disabled={!stepValid(2) || isCreating}
                        loading={isCreating}
                      >
                        {isCreating ? "Creating…" : "Create journey"}
                      </Button>
                    )}
                  </Group>
                </Box>
              </Box>
            </Box>
          </Box>
        </Box>
      </Box>
    </Box>
  );
}
