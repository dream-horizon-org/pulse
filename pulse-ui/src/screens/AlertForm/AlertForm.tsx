import classes from "./AlertForm.module.css";
import {
  Button,
  useMantineTheme,
  Stepper,
  Box,
  Title,
  Modal,
  LoadingOverlay,
  Tooltip,
  TextInput,
  Textarea,
  Select,
  NumberInput,
  ActionIcon,
  Text,
} from "@mantine/core";
import { useForm, Controller } from "react-hook-form";
import { useEffect, useState } from "react";
import {
  alertDefaultValue,
  AlertFormData,
  AlertFormProps,
  MetricOperator,
  metricOperatorOptions,
} from "./AlertForm.interface";
import { useNavigate, useParams } from "react-router-dom";
import {
  ALERT_FORM_CONSTANTS,
  ALERT_FORM_STEPS,
  COMMON_CONSTANTS,
  COOKIES_KEY,
  CRITICAL_INTERACTION_FORM_CONSTANTS,
  ROUTES,
} from "../../constants";
import { showNotification } from "../../helpers/showNotification";
import {
  IconCircleCheckFilled,
  IconSquareRoundedX,
  IconTrash,
  IconX,
  IconPlus,
} from "@tabler/icons-react";
import { useGetAlertDetails } from "../../hooks/useGetAlertDetails";
import { useCreateAlert } from "../../hooks/useCreateAlert";
import { useAlertDelete } from "../../hooks/useDeleteAlert/useDeleteAlert";
import { CreateAlertOnSettledResponse } from "../../hooks/useCreateAlert/useCreateAlert.interface";
import { getCookies } from "../../helpers/cookies";
import { useUpdateAlert } from "../../hooks/useUpdateAlert";
import { useGetAlertScopes } from "../../hooks/useGetAlertScopes";
import { useGetAlertMetrics } from "../../hooks/useGetAlertMetrics";
import { useGetAlertSeverities } from "../../hooks/useGetAlertSeverities";
import { useGetAlertNotificationChannels } from "../../hooks/useGetAlertNotificationChannels";

export const AlertForm = ({
  isInteractionDetailsFlow,
  interactionAlertId,
  onBackButtonClick,
}: AlertFormProps) => {
  const theme = useMantineTheme();
  const navigate = useNavigate();
  const [showModal, setShowModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [showLoader, setShowLoader] = useState(false);
  const [stepperActiveState, setStepperActiveStep] = useState<number>(0);
  let alertId = useParams()["*"];
  if (isInteractionDetailsFlow && interactionAlertId) {
    alertId = interactionAlertId.toString();
  }
  const isUpdateFlow = isInteractionDetailsFlow
    ? !!interactionAlertId
    : !!(alertId && alertId !== "*");

  const { control, handleSubmit, watch, setValue, getValues, reset } =
    useForm<AlertFormData>({
      defaultValues: {
        ...alertDefaultValue,
        alert_id: interactionAlertId || alertDefaultValue.alert_id,
      },
    });

  const selectedScope = watch("scope");
  const alerts = watch("alerts");

  // Fetch scopes
  const { data: scopesData, isLoading: isScopesLoading } = useGetAlertScopes();

  // Fetch metrics based on selected scope
  const { data: metricsData, isLoading: isMetricsLoading } = useGetAlertMetrics({
    scope: selectedScope,
  });

  // Fetch severities
  const { data: severitiesData, isLoading: isSeveritiesLoading } = useGetAlertSeverities();

  // Fetch notification channels
  const { data: channelsData, isLoading: isChannelsLoading } = useGetAlertNotificationChannels();

  const {
    data: response,
    isLoading,
    error,
  } = useGetAlertDetails({
    queryParams: {
      alert_id: alertId || null,
    },
  });

  useEffect(() => {
    if (error) {
      showNotification(
        COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
        error.message,
        <IconSquareRoundedX />,
        theme.colors.red[6],
      );
      return;
    }
    if (isLoading) {
      setShowLoader(true);
      return;
    }

    if (response && response.data && alertId) {
      const data = response.data;
      // Map API response to form data structure
      const formData: AlertFormData = {
        alert_id: data.alert_id,
        name: data.name,
        description: data.description,
        scope: data.scope,
        dimension_filters: data.dimension_filter || undefined,
        condition_expression: data.condition_expression,
        alerts: data.alerts.map((alert) => ({
          alias: alert.alias,
          metric: alert.metric,
          metric_operator: alert.metric_operator as MetricOperator,
          threshold: alert.threshold,
        })),
        evaluation_period: data.evaluation_period,
        evaluation_interval: data.evaluation_interval,
        severity_id: data.severity_id,
        notification_channel_id: data.notification_channel_id,
        created_by: data.created_by,
        updated_by: data.updated_by || undefined,
      };
      reset(formData);
    }

    setShowLoader(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [response, isLoading, error]);

  const navigateToAlertListingPage = () => {
    setTimeout(() => {
      if (isInteractionDetailsFlow) {
        onBackButtonClick?.();
      } else {
        navigate(`${ROUTES["ALERTS"].basePath}`);
      }
    }, 2000);
  };

  const toggleModalVisibility = () => {
    setShowModal((prev) => !prev);
  };

  const toggleDeleteModalVisibility = () => {
    setShowDeleteModal((prev) => !prev);
  };

  const useDeleteAlertMutation = useAlertDelete((response) => {
    setShowLoader(false);
    if (response?.error) {
      showNotification(
        COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
        response.error.message,
        <IconSquareRoundedX />,
        theme.colors.red[6],
      );
      return;
    }
    showNotification(
      COMMON_CONSTANTS.SUCCESS_NOTIFICATION_TITLE,
      `Alert with id ${alertId} deleted successfully.`,
      <IconCircleCheckFilled />,
      theme.colors.teal[6],
    );
    navigateToAlertListingPage();
  }, alertId || null);

  const useCreateAlertMutation = useCreateAlert(
    (response: CreateAlertOnSettledResponse) => {
      setShowLoader(false);
      if (response?.error) {
        showNotification(
          COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
          response.error.message,
          <IconSquareRoundedX />,
          theme.colors.red[6],
        );
        return;
      }
      showNotification(
        COMMON_CONSTANTS.SUCCESS_NOTIFICATION_TITLE,
        `Alert with id ${response?.data?.alert_id} created successfully.`,
        <IconCircleCheckFilled />,
        theme.colors.teal[6],
      );
      navigateToAlertListingPage();
    },
  );

  const useUpdateAlertMutation = useUpdateAlert(
    (response: CreateAlertOnSettledResponse) => {
      setShowLoader(false);
      if (response?.error) {
        showNotification(
          COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
          response.error.message,
          <IconSquareRoundedX />,
          theme.colors.red[6],
        );
        return;
      }
      showNotification(
        COMMON_CONSTANTS.SUCCESS_NOTIFICATION_TITLE,
        `Alert with id ${response?.data?.alert_id} updated successfully`,
        <IconCircleCheckFilled />,
        theme.colors.teal[6],
      );
      navigateToAlertListingPage();
    },
  );

  const handleStepChange = async (nextStep: number) => {
    if (nextStep > 3 || nextStep < 0) return;
    setStepperActiveStep(nextStep);
  };

  const onSubmit = () => {
    toggleModalVisibility();
  };

  const onProceedButtonClick = async () => {
    toggleModalVisibility();
    setShowLoader(true);
    const userEmail = getCookies(COOKIES_KEY.USER_EMAIL) || null;

    if (!userEmail) {
      showNotification(
        COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
        COMMON_CONSTANTS.USER_EMAIL_NOT_FOUND,
        <IconSquareRoundedX />,
        theme.colors.red[6],
      );
      setShowLoader(false);
      return;
    }

    const formData = getValues();
    const updatedFormData = {
      ...formData,
      created_by: userEmail,
      updated_by: userEmail,
    };

    if (isUpdateFlow) {
      useUpdateAlertMutation.mutateAsync(updatedFormData);
      return;
    }

    useCreateAlertMutation.mutateAsync(updatedFormData);
  };

  const handleDeleteAlert = () => {
    setShowLoader(true);
    useDeleteAlertMutation.mutateAsync(null);
  };

  const onProceedDeleteButtonClick = () => {
    toggleDeleteModalVisibility();
    handleDeleteAlert();
  };

  const addAlertCondition = () => {
    const currentAlerts = getValues("alerts");
    const nextAlias = String.fromCharCode(65 + currentAlerts.length); // A, B, C...
    setValue("alerts", [
      ...currentAlerts,
      {
        alias: nextAlias,
        metric: "",
        metric_operator: MetricOperator.LESS_THAN,
        threshold: {},
      },
    ]);
    // Update condition expression
    const aliases = [...currentAlerts.map((a) => a.alias), nextAlias];
    setValue("condition_expression", aliases.join(" AND "));
  };

  const removeAlertCondition = (index: number) => {
    const currentAlerts = getValues("alerts");
    if (currentAlerts.length <= 1) return;
    const updatedAlerts = currentAlerts.filter((_, i) => i !== index);
    setValue("alerts", updatedAlerts);
    setValue(
      "condition_expression",
      updatedAlerts.map((a) => a.alias).join(" AND "),
    );
  };

  const getScopeOptions = () => {
    if (scopesData?.data?.scopes) {
      return scopesData.data.scopes.map((s) => ({
        value: s.id,
        label: s.label,
      }));
    }
    return [];
  };

  const getMetricOptions = () => {
    if (metricsData?.data?.metrics) {
      return metricsData.data.metrics.map((metric) => ({
        value: metric,
        label: metric.replace(/_/g, " "),
      }));
    }
    return [];
  };

  const getThresholdKeys = (_metric: string) => {
    // For now, all metrics use a single "value" threshold
    // This can be extended based on API response if needed
    return ["value"];
  };

  // Map severity level (number) to display label
  const severityLevelLabels: Record<number, string> = { 1: "Critical", 2: "Warning", 3: "Info" };
  
  const getSeverityOptions = () => {
    if (severitiesData?.data?.severity) {
      return severitiesData.data.severity.map((s) => ({
        value: s.severity_id.toString(),
        label: `${severityLevelLabels[s.name] || `Level ${s.name}`} - ${s.description}`,
      }));
    }
    return [];
  };

  const getChannelOptions = () => {
    if (channelsData?.data) {
      return channelsData.data.map((c) => ({
        value: c.notification_channel_id.toString(),
        label: c.name,
      }));
    }
    return [];
  };

  const getStepContent = (step: number) => {
    switch (step) {
      case 0:
        return (
          <Box className={classes.formSection}>
            <Text className={classes.formSectionTitle}>Alert Information</Text>
            <Box className={classes.inputGroup}>
              <Controller
                name="name"
                control={control}
                rules={{ required: "Name is required", minLength: 4 }}
                render={({ field, fieldState }) => (
                  <TextInput
                    {...field}
                    label={ALERT_FORM_CONSTANTS.ALERT_NAME_LABEL}
                    placeholder={ALERT_FORM_CONSTANTS.ALERT_NAME_PLACEHOLDER}
                    description={ALERT_FORM_CONSTANTS.ALERT_NAME_LABEL_INFO}
                    error={fieldState.error?.message}
                    required
                  />
                )}
              />
              <Controller
                name="description"
                control={control}
                rules={{ required: "Description is required", minLength: 10 }}
                render={({ field, fieldState }) => (
                  <Textarea
                    {...field}
                    label={ALERT_FORM_CONSTANTS.ALERT_DESCRIPTION_LABEL}
                    placeholder={
                      ALERT_FORM_CONSTANTS.ALERT_DESCRIPTION_PLACEHOLDER
                    }
                    description={
                      ALERT_FORM_CONSTANTS.ALERT_DESCRIPTION_LABEL_INFO
                    }
                    error={fieldState.error?.message}
                    minRows={3}
                    required
                  />
                )}
              />
            </Box>
          </Box>
        );

      case 1:
        return (
          <Box className={classes.formSection}>
            <Text className={classes.formSectionTitle}>
              Scope and Conditions
            </Text>
            <Box className={classes.inputGroup}>
              <Controller
                name="scope"
                control={control}
                rules={{ required: "Scope is required" }}
                render={({ field, fieldState }) => (
                  <Select
                    {...field}
                    label={ALERT_FORM_CONSTANTS.ALERT_SCOPE_LABEL}
                    placeholder={isScopesLoading ? "Loading scopes..." : "Select scope"}
                    description={ALERT_FORM_CONSTANTS.ALERT_SCOPE_LABEL_INFO}
                    data={getScopeOptions()}
                    error={fieldState.error?.message}
                    disabled={isScopesLoading}
                    required
                  />
                )}
              />

              <Controller
                name="dimension_filters"
                control={control}
                render={({ field }) => (
                  <Textarea
                    {...field}
                    label={ALERT_FORM_CONSTANTS.ALERT_DIMENSION_FILTERS_LABEL}
                    placeholder='{"key": "value"}'
                    description={
                      ALERT_FORM_CONSTANTS.ALERT_DIMENSION_FILTERS_LABEL_INFO
                    }
                    minRows={2}
                  />
                )}
              />

              <Box>
                <Text size="sm" fw={500} mb="xs">
                  Alert Conditions
                </Text>
                {alerts.map((alert, index) => (
                  <Box key={index} className={classes.alertConditionCard}>
                    <Box className={classes.conditionHeader}>
                      <Text className={classes.conditionAlias}>
                        Condition {alert.alias}
                      </Text>
                      {alerts.length > 1 && (
                        <ActionIcon
                          color="red"
                          variant="subtle"
                          onClick={() => removeAlertCondition(index)}
                        >
                          <IconTrash size={16} />
                        </ActionIcon>
                      )}
                    </Box>
                    <Box className={classes.inputGroup}>
                      <Controller
                        name={`alerts.${index}.metric`}
                        control={control}
                        rules={{ required: "Metric is required" }}
                        render={({ field, fieldState }) => (
                          <Select
                            {...field}
                            label={ALERT_FORM_CONSTANTS.ALERT_METRIC_LABEL}
                            placeholder={
                              !selectedScope
                                ? "Select a scope first"
                                : isMetricsLoading
                                  ? "Loading metrics..."
                                  : "Select metric"
                            }
                            data={getMetricOptions()}
                            error={fieldState.error?.message}
                            disabled={!selectedScope || isMetricsLoading}
                            required
                          />
                        )}
                      />
                      <Controller
                        name={`alerts.${index}.metric_operator`}
                        control={control}
                        render={({ field }) => (
                          <Select
                            {...field}
                            label={
                              ALERT_FORM_CONSTANTS.ALERT_METRIC_OPERATOR_LABEL
                            }
                            data={metricOperatorOptions}
                          />
                        )}
                      />
                      <Box className={classes.thresholdInputs}>
                        {getThresholdKeys(alert.metric).map((key) => (
                          <Controller
                            key={key}
                            name={`alerts.${index}.threshold.${key}`}
                            control={control}
                            render={({ field }) => (
                              <NumberInput
                                {...field}
                                label={`Threshold (${key})`}
                                placeholder="Enter threshold"
                              />
                            )}
                          />
                        ))}
                      </Box>
                    </Box>
                  </Box>
                ))}
                <Button
                  variant="subtle"
                  leftSection={<IconPlus size={16} />}
                  onClick={addAlertCondition}
                  size="sm"
                >
                  Add Condition
                </Button>
              </Box>

              <Controller
                name="condition_expression"
                control={control}
                render={({ field }) => (
                  <TextInput
                    {...field}
                    label={
                      ALERT_FORM_CONSTANTS.ALERT_CONDITION_EXPRESSION_LABEL
                    }
                    placeholder="A AND B"
                    description={
                      ALERT_FORM_CONSTANTS.ALERT_CONDITION_EXPRESSION_LABEL_INFO
                    }
                  />
                )}
              />
            </Box>
          </Box>
        );

      case 2:
        return (
          <Box className={classes.formSection}>
            <Text className={classes.formSectionTitle}>Evaluation Settings</Text>
            <Box className={classes.inputGroup}>
              <Controller
                name="evaluation_period"
                control={control}
                rules={{ required: "Evaluation period is required", min: 30 }}
                render={({ field, fieldState }) => (
                  <NumberInput
                    {...field}
                    label={ALERT_FORM_CONSTANTS.ALERT_MONITORING_PERIOD_LABEL}
                    description={
                      ALERT_FORM_CONSTANTS.ALERT_MONITORING_PERIOD_LABEL_INFO
                    }
                    placeholder="300"
                    min={30}
                    max={3600}
                    error={fieldState.error?.message}
                    required
                  />
                )}
              />
              <Controller
                name="evaluation_interval"
                control={control}
                rules={{ required: "Evaluation interval is required", min: 30 }}
                render={({ field, fieldState }) => (
                  <NumberInput
                    {...field}
                    label={ALERT_FORM_CONSTANTS.ALERT_EVALUATION_INTERVAL_LABEL}
                    description={
                      ALERT_FORM_CONSTANTS.ALERT_EVALUATION_INTERVAL_LABEL_INFO
                    }
                    placeholder="60"
                    min={30}
                    max={3600}
                    error={fieldState.error?.message}
                    required
                  />
                )}
              />
            </Box>
          </Box>
        );

      case 3:
        return (
          <Box className={classes.formSection}>
            <Text className={classes.formSectionTitle}>
              Notification Settings
            </Text>
            <Box className={classes.inputGroup}>
              <Controller
                name="severity_id"
                control={control}
                rules={{ required: "Severity is required" }}
                render={({ field, fieldState }) => (
                  <Select
                    {...field}
                    value={field.value?.toString()}
                    onChange={(val) => field.onChange(Number(val))}
                    label={ALERT_FORM_CONSTANTS.ALERT_SEVERITY_LABEL}
                    description={ALERT_FORM_CONSTANTS.ALERT_SEVERITY_LABEL_INFO}
                    placeholder={isSeveritiesLoading ? "Loading severities..." : "Select severity"}
                    data={getSeverityOptions()}
                    error={fieldState.error?.message}
                    disabled={isSeveritiesLoading}
                    required
                  />
                )}
              />
              <Controller
                name="notification_channel_id"
                control={control}
                rules={{ required: "Notification channel is required" }}
                render={({ field, fieldState }) => (
                  <Select
                    {...field}
                    value={field.value?.toString()}
                    onChange={(val) => field.onChange(Number(val))}
                    label="Notification Channel"
                    description="Select the channel for alert notifications"
                    placeholder={isChannelsLoading ? "Loading channels..." : "Select channel"}
                    data={getChannelOptions()}
                    error={fieldState.error?.message}
                    disabled={isChannelsLoading}
                    required
                  />
                )}
              />
            </Box>
          </Box>
        );

      default:
        return null;
    }
  };

  return (
    <Box style={{ position: "relative", height: "100%", overflow: "hidden" }}>
      <form
        noValidate
        onSubmit={handleSubmit(onSubmit)}
        style={{
          position: "relative",
          height: "100%",
          display: "flex",
          flexDirection: "column",
        }}
      >
        <Box className={classes.AlertFormContainerGroup}>
          <Box className={classes.AlertFormHeaderContainer}>
            <Title
              order={isInteractionDetailsFlow ? 4 : 2}
              style={{ flexGrow: 1 }}
            >
              {isUpdateFlow
                ? ALERT_FORM_CONSTANTS.UPDATE_ALERT_HEADING
                : ALERT_FORM_CONSTANTS.CREATE_ALERT_HEADING}
            </Title>
            {isUpdateFlow && (
              <Button
                onClick={toggleDeleteModalVisibility}
                size="sm"
                variant="outline"
                aria-label="delete alert"
                style={{ borderColor: theme.colors.red[6], marginRight: 8 }}
              >
                <Tooltip
                  withArrow
                  multiline
                  label={ALERT_FORM_CONSTANTS.DELETE_ALERT_BUTTON}
                >
                  <IconTrash color={theme.colors.red[6]} />
                </Tooltip>
              </Button>
            )}
            <Button
              onClick={() => {
                if (isInteractionDetailsFlow) {
                  onBackButtonClick?.();
                } else {
                  navigate(`${ROUTES["ALERTS"].basePath}`);
                }
              }}
              size="sm"
              variant="subtle"
              aria-label="close alert form"
              className={classes.closeButton}
            >
              <IconX size={20} />
            </Button>
          </Box>
          <Box className={classes.AlertForm} pos="relative">
            <LoadingOverlay
              visible={showLoader}
              className={classes.loadingOverlay}
              loaderProps={{ type: "bars" }}
              zIndex={10}
            />
            <Box className={classes.StepperContainer}>
              <Stepper
                active={stepperActiveState}
                onStepClick={setStepperActiveStep}
                orientation="vertical"
                className={classes.AlertStepperRoot}
                styles={{
                  content: {
                    flexGrow: 1,
                    display: "flex",
                    alignItems: "flex-start",
                    justifyContent: "flex-start",
                    padding: "20px",
                    borderLeftWidth: "1px",
                    borderLeftStyle: "solid",
                    borderLeftColor: "rgba(14, 201, 194, 0.2)",
                    background: "rgba(14, 201, 194, 0.02)",
                    overflow: "auto",
                  },
                  stepBody: { flexGrow: 1, width: "100%", alignContent: "center", justifyContent: "center" },
                  step: { paddingRight: "15%", width: "350px", padding: "var(--mantine-spacing-md)" },
                  stepIcon: { borderColor: "#0ec9c2" },
                }}
              >
                {ALERT_FORM_STEPS.map((step, index) => (
                  <Stepper.Step
                    key={index}
                    label={step.label}
                    description={step.description}
                    className={classes.StepperItem}
                    bg={stepperActiveState === index ? "white" : undefined}
                  >
                    {getStepContent(index)}
                  </Stepper.Step>
                ))}
              </Stepper>
            </Box>
          </Box>
          <Box className={classes.AlertFormButtonBox}>
            <Box className={classes.AlertFormButtonContainer}>
              {isInteractionDetailsFlow && (
                <Button
                  className={classes.AlertFormButton}
                  variant="default"
                  size="sm"
                  onClick={onBackButtonClick}
                >
                  Cancel
                </Button>
              )}
              {stepperActiveState > 0 && (
                <Button
                  variant="default"
                  onClick={() => handleStepChange(stepperActiveState - 1)}
                  size="sm"
                  className={classes.AlertFormButton}
                >
                  Previous step
                </Button>
              )}
              {stepperActiveState < 3 && (
                <Button
                  size="sm"
                  className={classes.AlertFormButton}
                  onClick={() => handleStepChange(stepperActiveState + 1)}
                  variant="outline"
                  style={{ borderColor: "#0ec9c2", color: "#0ec9c2" }}
                >
                  Next step
                </Button>
              )}
              {stepperActiveState === 3 && (
                <Button
                  type="submit"
                  variant="outline"
                  size="sm"
                  className={classes.AlertFormButton}
                >
                  {isUpdateFlow ? "Update alert" : "Create alert"}
                </Button>
              )}
            </Box>
          </Box>
        </Box>
        <Modal
          opened={showModal}
          title={
            isUpdateFlow
              ? ALERT_FORM_CONSTANTS.ALERT_UPDATE_MODAL_TITLE
              : ALERT_FORM_CONSTANTS.ALERT_CREATE_MODAL_TITLE
          }
          onClose={toggleModalVisibility}
          size="md"
          centered
        >
          <Box className={classes.modalContainer}>
            <Button
              color={theme.colors.red[6]}
              variant="outline"
              size="sm"
              onClick={toggleModalVisibility}
            >
              {CRITICAL_INTERACTION_FORM_CONSTANTS.CANCEL_BUTTON_TEXT}
            </Button>
            <Button size="sm" onClick={onProceedButtonClick}>
              {CRITICAL_INTERACTION_FORM_CONSTANTS.PROCEED_BUTTON_TEXT}
            </Button>
          </Box>
        </Modal>
        <Modal
          opened={showDeleteModal}
          title={ALERT_FORM_CONSTANTS.ALERT_DELETE_MODAL_TITLE}
          onClose={toggleDeleteModalVisibility}
          size="md"
          centered
        >
          <Box className={classes.modalContainer}>
            <Button
              color={theme.colors.red[6]}
              variant="outline"
              size="sm"
              onClick={toggleDeleteModalVisibility}
            >
              {CRITICAL_INTERACTION_FORM_CONSTANTS.CANCEL_BUTTON_TEXT}
            </Button>
            <Button size="sm" onClick={onProceedDeleteButtonClick}>
              {CRITICAL_INTERACTION_FORM_CONSTANTS.PROCEED_BUTTON_TEXT}
            </Button>
          </Box>
        </Modal>
      </form>
    </Box>
  );
};

export default AlertForm;

