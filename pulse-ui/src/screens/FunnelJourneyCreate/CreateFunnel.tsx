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
  IconChartFunnel,
  IconSquareRoundedX,
} from "@tabler/icons-react";
import { generatePath, useNavigate, useParams } from "react-router-dom";
import { COMMON_CONSTANTS, ROUTES } from "../../constants";
import { showNotification } from "../../helpers/showNotification";
import classes from "./FunnelCreate.module.css";
import createFormClasses from "./FunnelJourneyCreateForm.module.css";
import {
  FUNNEL_CREATE_STEP_ERRORS,
  FUNNEL_CREATE_STEPS,
} from "./FunnelJourneyCreateForm.constants";
import { ActiveFilter, GlobalFilterBar } from "./components/GlobalFilterBar";
import { BuilderStep, FunnelBuilder } from "./components/FunnelBuilder";
import { getDateRangeFromPreset } from "./FunnelJourneyCreate.util";
import {
  FunnelStep,
  useGetFunnelEvents,
  useGetFunnelFilters,
} from "../../hooks/useGetFunnelData";
import { useCreateFunnel } from "../../hooks/useCreateFunnel";

const EMPTY_STEPS: BuilderStep[] = [
  { id: "s-1", eventName: "" },
  { id: "s-2", eventName: "" },
];

function toApiSteps(steps: BuilderStep[]): FunnelStep[] {
  return steps
    .filter((s) => s.eventName)
    .map((s) => ({
      eventName: s.eventName,
      dataType: "LOGS" as const,
    }));
}

export function CreateFunnel() {
  const theme = useMantineTheme();
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

  const [steps, setSteps] = useState<BuilderStep[]>(EMPTY_STEPS);
  const [funnelMode, setFunnelMode] = useState<"ordered" | "unordered">(
    "ordered",
  );
  const [conversionWindow, setConversionWindow] = useState("86400");

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

  const timeRange = useMemo(() => {
    if (rollingType === "ONCE") {
      return {
        start: customStartDate
          ? customStartDate.toISOString()
          : new Date().toISOString(),
        end: customEndDate
          ? customEndDate.toISOString()
          : new Date().toISOString(),
      };
    }
    return getDateRangeFromPreset(dateRange);
  }, [rollingType, dateRange, customStartDate, customEndDate]);

  const apiSteps = useMemo(() => toApiSteps(steps), [steps]);

  const apiFilters = useMemo(
    () =>
      filters.map((f) => ({
        field: f.property,
        operator: "EQ" as const,
        value: f.value,
      })),
    [filters],
  );

  const { mutate: createFunnel, isPending: isCreating } = useCreateFunnel();

  const hasValidSteps =
    steps.length >= 2 && steps.every((s) => Boolean(s.eventName));

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
        return hasValidSteps;
      default:
        return true;
    }
  };

  const stepErrorMessage = (index: number): string => {
    switch (index) {
      case 0:
        return FUNNEL_CREATE_STEP_ERRORS.NAME;
      case 1:
        return FUNNEL_CREATE_STEP_ERRORS.SCHEDULE_ONCE;
      case 2:
        return FUNNEL_CREATE_STEP_ERRORS.STEPS;
      default:
        return "";
    }
  };

  const handleAnalyze = () => {
    createFunnel(
      {
        name,
        description,
        tags,
        rollingType,
        funnelType: funnelMode.toUpperCase(),
        steps: apiSteps,
        timeRange,
        windowSeconds: parseInt(conversionWindow, 10),
        filters: apiFilters,
        expiryDate:
          rollingType === "RECURRING" && expiryDate
            ? expiryDate.toISOString()
            : undefined,
      },
      {
        onSuccess: () => {
          if (projectId) {
            navigate(generatePath(ROUTES.FUNNELS_LIST.path, { projectId }));
          }
        },
      },
    );
  };

  const goBack = () => {
    if (projectId) {
      navigate(generatePath(ROUTES.FUNNELS_LIST.path, { projectId }));
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
    setActiveStep((s) => Math.min(s + 1, FUNNEL_CREATE_STEPS.length - 1));
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
                <Text className={classes.moduleTitle}>Create Funnel</Text>
              </Box>
            </Group>
          </Box>
        </Box>

        <Box className={createFormClasses.createFormMain}>
          <Divider className={createFormClasses.createFormDivider} />

          <Box className={createFormClasses.createFormHeading}>
            <Title order={2} fw={700}>
              Add a new funnel
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
                {FUNNEL_CREATE_STEPS.map((step, index) => (
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
                    <FunnelBuilder
                      wizardStep={activeStep as 0 | 1 | 2}
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
                      steps={steps}
                      onStepsChange={(s) => {
                        setSteps(s);
                      }}
                      funnelMode={funnelMode}
                      onFunnelModeChange={setFunnelMode}
                      conversionWindow={conversionWindow}
                      onConversionWindowChange={setConversionWindow}
                      onAnalyze={handleAnalyze}
                      isCreating={isCreating}
                      availableEvents={availableEvents}
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
                          to limit which users are included when this funnel is
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
                          <IconChartFunnel size={26} stroke={1.5} />
                        </Box>
                        <Text size="lg" fw={700} c="dark.7" lh={1.3}>
                          Ready to create
                        </Text>
                        <Text size="sm" c="dimmed" lh={1.55}>
                          Filters above apply to how this funnel is computed.
                          After creation, open the funnel detail page to explore
                          conversion, trends, and step breakdown.
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
                    {activeStep < FUNNEL_CREATE_STEPS.length - 1 ? (
                      <Button color="blue" size="md" onClick={goNext}>
                        Next
                      </Button>
                    ) : (
                      <Button
                        color="blue"
                        size="md"
                        onClick={handleAnalyze}
                        disabled={!hasValidSteps || isCreating}
                        loading={isCreating}
                      >
                        {isCreating ? "Creating…" : "Create funnel"}
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
