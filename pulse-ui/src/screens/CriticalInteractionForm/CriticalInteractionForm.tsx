import {
  Box,
  Button,
  Divider,
  Group,
  LoadingOverlay,
  Modal,
  Stepper,
  Title,
  useMantineTheme,
} from "@mantine/core";
import classes from "./CiritcalInteractionForm.module.css";
import { useEffect, useRef, useState } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { JourneyName } from "./components/JourneyName";
import { IconCircleCheckFilled, IconSquareRoundedX } from "@tabler/icons-react";
import { EventsSequenceSection } from "./components/EventsSequenceSection";
import {
  CriticalInteractionFormData,
  CriticalInteractionFormStepsRecords,
  CriticalInteractionsFetchingState,
  EventFilters,
  EventSequenceData,
} from "./CriticalInteractionForm.interface";
import { GlobalBlackListEventSequence } from "./components/GlobalBlacklistEventSequence/GlobalBlacklistEventSequence";
import { EventThresholds } from "./components/EventThresholds";
import {
  COOKIES_KEY,
  CRITICAL_INTERACTION_FORM_CONSTANTS,
  COMMON_CONSTANTS,
  ROUTES,
  DEFAULT_CRITICAL_INTERACTION_FORM_STEPS_RECORD,
  DEFAULT_EVENT_WHITELISTING_DATA,
  DEFAULT_EVENT_BLACKLISTING_DATA,
  CRITICAL_INTERACTION_FORM_STEPS,
} from "../../constants";
import { makeCriticalInteractionFormRequestBody } from "../../helpers/makeCriticalInteractionFormRequestBody";
import {
  createJob as createStreamverseJob,
  CriticalInteractionFormRequestBodyParams,
} from "../../helpers/createJob";
import { getCookies } from "../../helpers/cookies";
import { useNavigate, useParams } from "react-router-dom";
import { makeCriticalInteractionFormDataUsingJobDetails } from "../../helpers/makeCriticalInteractionFormDataUsingJobDetails";
import { LoaderWithMessage } from "../../components/LoaderWithMessage";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import { showNotification } from "../../helpers/showNotification";
import {
  UpdateInteractionOnSettledResponse,
  useUpdateInteraction,
} from "../../hooks/useUpdateInteraction";
import { useGetJobStatus } from "../../hooks/useGetJobStatus";
import { useGetInteractionDetails } from "../../hooks/useGetInteractionDetails";
import { logEvent } from "../../helpers/googleAnalytics";

export function CriticalInteractionForm() {
  const theme = useMantineTheme();
  const navigate = useNavigate();
  const { projectId, "*": wildcardParam } = useParams<{
    projectId: string;
    "*": string;
  }>();
  const useCaseName = wildcardParam;
  const isUpdateFlow = !!(useCaseName && useCaseName !== "*");
  const [jobId, setJobId] = useState<number>(0);

  const [stepperActiveState, setStepperActiveStep] = useState<number>(0);
  const [showModal, setShowModal] = useState(false);
  const [showLoader, setShowLoader] = useState(false);
  const [updateFlowStarted, setUpdateFlowStarted] = useState(false);
  const [jobIsReadyToUpdate, setJobIsReadyToUpdate] = useState<boolean>(false);
  const {
    data: jobStatus,
    refetch: refetchJobStatus,
    isFetching,
    isLoading: isLoadingJobStatus,
  } = useGetJobStatus(jobId, false);

  const [fetchingDetailsFromStreamverse, setFetchingDetailsFromStreamverse] =
    useState<CriticalInteractionsFetchingState>({
      isFetching: isUpdateFlow,
      error: "",
    });

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const streamverseJobId = useRef<number>(0);
  const jobVersion = useRef<string>("");
  const completedStepsRecord = useRef<CriticalInteractionFormStepsRecords>(
    DEFAULT_CRITICAL_INTERACTION_FORM_STEPS_RECORD,
  );

  const modalMessage = useRef<string>(
    isUpdateFlow
      ? CRITICAL_INTERACTION_FORM_CONSTANTS.UPDATE_MODAL_MESSAGE
      : CRITICAL_INTERACTION_FORM_CONSTANTS.CREATE_MODAL_MESSAGE,
  );

  const formMethods = useForm<CriticalInteractionFormData>({
    defaultValues: {
      name: "",
      description: "",
      uptimeLowerLimitInMs: parseInt(
        CRITICAL_INTERACTION_FORM_CONSTANTS.LOWER_THRESHOLD_VALUE,
      ),
      uptimeUpperLimitInMs: parseInt(
        CRITICAL_INTERACTION_FORM_CONSTANTS.UPPER_THRESHOLD_VALUE,
      ),
      uptimeMidLimitInMs: parseInt(
        CRITICAL_INTERACTION_FORM_CONSTANTS.MIDDLE_THRESHOLD_VALUE,
      ),
      thresholdInMs: parseInt(
        CRITICAL_INTERACTION_FORM_CONSTANTS.DEFAULT_INTERACTION_THRESHOLD,
      ),
      events: DEFAULT_EVENT_WHITELISTING_DATA,
      globalBlacklistedEvents: DEFAULT_EVENT_BLACKLISTING_DATA,
    },
  });

  const interactionDetails = useGetInteractionDetails({
    queryParams: { name: useCaseName || null },
  });

  useEffect(() => {
    if (fetchingDetailsFromStreamverse.isFetching && interactionDetails.data) {
      fetchJobDetailsFromStreamverse();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [interactionDetails.data, interactionDetails.error]);

  useEffect(() => {
    if (!updateFlowStarted) {
      return;
    }

    if (isFetching || isLoadingJobStatus) {
      return;
    }

    if (jobStatus?.error) {
      setShowLoader(false);
      showNotification(
        COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
        jobStatus.error.message,
        <IconSquareRoundedX />,
        theme.colors.red[6],
      );

      return;
    }

    if (jobStatus?.data?.status === "running" && !jobIsReadyToUpdate) {
      // updateJobStatusMutation.mutate({
      //   useCaseId: useCaseName || "",
      //   user: getCookies(COOKIES_KEY.USER_EMAIL),
      //   action: "JOB_STATE_CANCELLED",
      // });
      return;
    }

    setJobIsReadyToUpdate(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobStatus?.data, isFetching, isLoadingJobStatus]);

  useEffect(() => {
    if (updateFlowStarted) {
      refetchJobStatus();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [updateFlowStarted]);

  useEffect(() => {
    if (jobIsReadyToUpdate) {
      const formData = makeCriticalInteractionFormRequestBody(formMethods);
      updateJob(formData);
    } else {
      setJobIsReadyToUpdate(false);
      setUpdateFlowStarted(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobIsReadyToUpdate]);

  const updateJobMutation = useUpdateInteraction(
    (response: UpdateInteractionOnSettledResponse) => {
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
        `${useCaseName} updated successfully. Redirecting to all interactions page.`,
        <IconCircleCheckFilled />,
        theme.colors.teal[6],
      );
      navigateToCriticalInteractionListingPage();
      setUpdateFlowStarted(false);
    },
  );

  const fetchJobDetailsFromStreamverse = async () => {
    // Use the interactionDetails from the hook instead of making a separate API call
    if (interactionDetails.error) {
      setFetchingDetailsFromStreamverse((prevState) => ({
        ...prevState,
        isFetching: false,
        error:
          interactionDetails.error.message ||
          "Failed to fetch interaction details",
      }));
      return;
    }

    if (interactionDetails.data?.data) {
      const data = interactionDetails.data.data;
      streamverseJobId.current = data.id;
      makeCriticalInteractionFormDataUsingJobDetails(data, formMethods);
      setFetchingDetailsFromStreamverse((prevState) => ({
        ...prevState,
        isFetching: false,
        error: "",
      }));

      setJobId(data.id);
    }
  };

  const onNextClick = () => {
    setStepperActiveStep((prev) => prev + 1);
  };

  const onBackClick = () => {
    setStepperActiveStep((prev) => prev - 1);
  };

  const onChangeInActiveState = (isStateValid: boolean) => {
    completedStepsRecord.current[stepperActiveState].isCompleted = isStateValid;
  };

  const onStepClick = (clickedIndex: number) => {
    if (
      !completedStepsRecord.current[stepperActiveState].isCompleted &&
      clickedIndex > stepperActiveState
    ) {
      showNotification(
        COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
        completedStepsRecord.current[stepperActiveState].errorMessage,
        <IconSquareRoundedX />,
        theme.colors.red[6],
      );
      return;
    }

    if (!validateFormData(clickedIndex)) {
      return;
    }

    setStepperActiveStep(clickedIndex);
  };

  const validateFormData = (clickedIndex: number) => {
    for (let i = 0; i < clickedIndex; i++) {
      if (!completedStepsRecord.current[i].isCompleted) {
        showNotification(
          COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
          completedStepsRecord.current[i].errorMessage,
          <IconSquareRoundedX />,
          theme.colors.red[6],
        );
        setStepperActiveStep(i);
        return false;
      }
    }
    return true;
  };

  const onEventsSequenceWhitelistingDataChange = (
    data: Array<EventSequenceData>,
  ) => {
    formMethods.setValue("events", data);
  };

  const onEventsGlobalBlackListingDataChange = (data: Array<EventFilters>) => {
    formMethods.setValue("globalBlacklistedEvents", data);
  };

  const onCreateClick = () => {
    onProcedButtonClick();
  };

  const toogleModalVisiblity = () => {
    setShowModal((prev) => !prev);
  };

  const onCancelButtonClick = () => {
    toogleModalVisiblity();
  };

  const navigateToCriticalInteractionListingPage = () => {
    setTimeout(() => {
      navigate(
        ROUTES.PROJECT_INTERACTIONS.basePath.replace(
          ":projectId",
          projectId || "",
        ),
      );
    }, 3000);
  };

  const createJob = async (
    formData: CriticalInteractionFormRequestBodyParams,
  ) => {
    logEvent("Create interaction", ROUTES.PROJECT_INTERACTION_FORM.key);
    const { error } = await createStreamverseJob(formData);
    setShowLoader(false);

    if (error) {
      showNotification(
        COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
        error.message,
        <IconSquareRoundedX />,
        theme.colors.red[6],
      );
      return;
    }

    showNotification(
      COMMON_CONSTANTS.SUCCESS_NOTIFICATION_TITLE,
      CRITICAL_INTERACTION_FORM_CONSTANTS.CREATE_JOB_SUCCESS_NOTIFICATION_MESSAGE,
      <IconCircleCheckFilled />,
      theme.colors.teal[6],
    );
    navigateToCriticalInteractionListingPage();
  };

  const updateJob = (formData: CriticalInteractionFormRequestBodyParams) => {
    logEvent("Update interaction", ROUTES.PROJECT_INTERACTION_FORM.key);
    updateJobMutation.mutate({
      useCaseID: `${useCaseName}`,
      jobDetails: {
        ...formData,
      },
      user: getCookies(COOKIES_KEY.USER_EMAIL) || "",
    });
  };

  const onProcedButtonClick = async () => {
    setShowLoader(true);

    const formData = makeCriticalInteractionFormRequestBody(formMethods);
    if (isUpdateFlow) {
      updateJob(formData);
      return;
    }

    createJob(formData);
  };

  if (fetchingDetailsFromStreamverse.isFetching) {
    return (
      <LoaderWithMessage
        loadingMessage={`Fetching data for job id ${useCaseName}`}
      />
    );
  }

  if (fetchingDetailsFromStreamverse.error) {
    return (
      <ErrorAndEmptyState message={fetchingDetailsFromStreamverse.error} />
    );
  }

  return (
    <FormProvider {...formMethods}>
      <Box className={classes.criticalInteractionFormContainer}>
        <Box className={classes.criticalInteractionFormHeading}>
          <Title order={2}>
            {isUpdateFlow
              ? CRITICAL_INTERACTION_FORM_CONSTANTS.UPDATE_INTERACTION_HEADING
              : CRITICAL_INTERACTION_FORM_CONSTANTS.CREATE_INTERACTION_HEADING}
          </Title>
        </Box>
        <Divider className={classes.criticalInteractionFormDivider} />
        <Box className={classes.criticalInteractionForm} pos={"relative"}>
          <LoadingOverlay
            visible={showLoader}
            className={classes.loadingOverlay}
            loaderProps={{
              type: "bars",
            }}
            zIndex={10}
          />
          <Box className={classes.stepperContainer}>
            <Stepper
              className={classes.stepper}
              active={stepperActiveState}
              onStepClick={onStepClick}
              orientation="vertical"
            >
              {CRITICAL_INTERACTION_FORM_STEPS.map((step, index) => (
                <Stepper.Step
                  classNames={{
                    stepWrapper: classes.stepperWrapper,
                  }}
                  className={classes.stepperItem}
                  bg={stepperActiveState === index ? "white" : undefined}
                  key={index}
                  label={step.label}
                  description={step.description}
                />
              ))}
            </Stepper>
          </Box>
          <Box className={classes.form}>
            {stepperActiveState === 0 && (
              <JourneyName
                jobVersion={jobVersion.current}
                isUpdateFlow={isUpdateFlow}
                formMethods={formMethods}
                interactionName={formMethods.getValues("name")}
                onNextClick={onNextClick}
                onChangeInActiveState={onChangeInActiveState}
                interactionDescription={formMethods.getValues("description")}
              />
            )}
            {stepperActiveState === 1 && (
              <EventsSequenceSection
                defaultState={formMethods.getValues("events")}
                onEventsSequenceWhitelistingDataChange={
                  onEventsSequenceWhitelistingDataChange
                }
                onNextClick={onNextClick}
                onBackClick={onBackClick}
                onChangeInActiveState={onChangeInActiveState}
              />
            )}
            {stepperActiveState === 2 && (
              <GlobalBlackListEventSequence
                defaultState={formMethods.getValues("globalBlacklistedEvents")}
                onEventsGlobalBlackListingDataChange={
                  onEventsGlobalBlackListingDataChange
                }
                onNextClick={onNextClick}
                onBackClick={onBackClick}
                onChangeInActiveState={onChangeInActiveState}
              />
            )}
            {stepperActiveState === 3 && (
              <EventThresholds
                isUpdateFlow={isUpdateFlow}
                onBackClick={onBackClick}
                onCreateClick={onCreateClick}
              />
            )}
          </Box>
        </Box>
        <Modal
          opened={showModal}
          title={modalMessage.current}
          onClose={toogleModalVisiblity}
          size={"lg"}
          styles={{
            title: {
              flexGrow: 1,
              textAlign: "center",
            },
          }}
        >
          <Group className={classes.modalContainer}>
            <Button
              color={theme.colors.red[6]}
              variant="outline"
              size={CRITICAL_INTERACTION_FORM_CONSTANTS.BUTTON_SIZE}
              onClick={onCancelButtonClick}
            >
              {CRITICAL_INTERACTION_FORM_CONSTANTS.CANCEL_BUTTON_TEXT}
            </Button>
            <Button
              size={CRITICAL_INTERACTION_FORM_CONSTANTS.BUTTON_SIZE}
              onClick={onProcedButtonClick}
            >
              {CRITICAL_INTERACTION_FORM_CONSTANTS.PROCEED_BUTTON_TEXT}
            </Button>
          </Group>
        </Modal>
      </Box>
    </FormProvider>
  );
}
