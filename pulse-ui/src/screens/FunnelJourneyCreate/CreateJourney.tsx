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
import { useCreateJourney } from "../../hooks/useCreateJourney";
import classes from "./FunnelCreate.module.css";
import createFormClasses from "./FunnelJourneyCreateForm.module.css";
import {
  JOURNEY_CREATE_STEP_ERRORS,
  JOURNEY_CREATE_STEPS,
} from "./FunnelJourneyCreateForm.constants";
import { ActiveFilter, GlobalFilterBar } from "./components/GlobalFilterBar";
import { JourneyExplorer } from "./components/JourneyExplorer";
import {
  useGetAllFilterValues,
  useGetFunnelEvents,
  useGetFunnelFilters,
} from "../../hooks/useGetFunnelData";
import { FunnelType, type AnalysisBasis, type CreateJourneyRequestBody } from "../../services/funnels.service";

export function CreateJourney() {
  const theme = useMantineTheme();
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string }>();

  const [activeStep, setActiveStep] = useState(0);

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [tags, setTags] = useState<string[]>([]);
  const [rollingType, setRollingType] = useState<FunnelType>(FunnelType.AUTO);
  const [dateRange, setDateRange] = useState("7d");
  const [customStartDate, setCustomStartDate] = useState<Date | null>(null);
  const [customEndDate, setCustomEndDate] = useState<Date | null>(null);
  const [expiryDate, setExpiryDate] = useState<Date | null>(null);
  const [filters, setFilters] = useState<ActiveFilter[]>([]);

  const [anchorEvent, setAnchorEvent] = useState("");
  const [direction, setDirection] = useState<"START" | "END">("START");
  const [depth, setDepth] = useState(5);
  const [analysisBasis, setAnalysisBasis] = useState<AnalysisBasis>("EVENT");

  const { data: eventsData } = useGetFunnelEvents();
  const { data: filtersData } = useGetFunnelFilters();

  const availableEvents = eventsData?.data?.events ?? [];

  const filterKeys = useMemo(() => filtersData?.data?.filters ?? [], [filtersData?.data?.filters]);
  const filterValuesResults = useGetAllFilterValues(filterKeys, activeStep === 3);

  const filterOptions = useMemo(() => {
    const result: Record<string, string[]> = {};
    filterKeys.forEach((key, index) => {
      result[key] = filterValuesResults[index]?.data?.data?.values ?? [];
    });
    return result;
  }, [filterKeys, filterValuesResults]);

  const apiFilters = useMemo(() => {
    const grouped: Record<string, string[]> = {};
    for (const f of filters) {
      (grouped[f.property] ??= []).push(f.value);
    }
    return Object.entries(grouped).map(([field, values]) => ({
      field,
      operator: "EQ" as const,
      value: values,
    }));
  }, [filters]);

  const { mutate: createJourney, isPending: isCreating } = useCreateJourney();

  const stepValid = (index: number): boolean => {
    switch (index) {
      case 0:
        return name.trim().length > 0;
      case 1:
        if (rollingType === FunnelType.ONCE) {
          return !!(customStartDate && customEndDate);
        }
        // AUTO journeys must have an expiry date so the cron knows when to stop
        // refreshing them.
        return !!expiryDate;
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
        return rollingType === FunnelType.ONCE
          ? JOURNEY_CREATE_STEP_ERRORS.SCHEDULE_ONCE
          : JOURNEY_CREATE_STEP_ERRORS.SCHEDULE_RECURRING;
      case 2:
        return JOURNEY_CREATE_STEP_ERRORS.PATH;
      default:
        return "";
    }
  };

  const handleCreateJourney = () => {
    const body: CreateJourneyRequestBody = {
      name,
      description,
      tags,
      journeyType: rollingType,
      direction,
      anchorEvent,
      depth,
      analysisBasis,
      filters: apiFilters,
      dateRangeDays: parseInt(dateRange, 10) || 7,
    };

    if (rollingType === FunnelType.ONCE) {
      if (customStartDate) body.startTime = customStartDate.toISOString();
      if (customEndDate) body.endTime = customEndDate.toISOString();
    } else {
      // AUTO journeys: expiry is mandatory (validated upstream by stepValid).
      // The non-null assertion is safe because the wizard blocks submission
      // when expiryDate is null for AUTO.
      body.expiry = expiryDate!.toISOString();
    }

    createJourney(body, {
      onSuccess: () => {
        if (projectId) {
          navigate(generatePath(ROUTES.JOURNEYS_LIST.path, { projectId }));
        }
      },
    });
  };

  const goBack = () => {
    if (projectId) {
      navigate(generatePath(ROUTES.JOURNEYS_LIST.path, { projectId }));
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
                      analysisBasis={analysisBasis}
                      onAnalysisBasisChange={setAnalysisBasis}
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
