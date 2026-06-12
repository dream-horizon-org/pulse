import { useEffect, useState } from "react";
import {
  COMMON_CONSTANTS,
  COOKIES_KEY,
  INTERACTION_STATUS,
  TOOLTIP_LABLES,
} from "../../../../constants";
import { ActionProps } from "./Actions.interface";
import {
  UpdateInteractionOnSettledResponse,
  useUpdateInteraction,
} from "../../../../hooks/useUpdateInteraction";
import { showNotification } from "../../../../helpers/showNotification";
import {
  IconCircleCheckFilled,
  IconPlayerPlay,
  IconPlayerStop,
  IconSquareRoundedX,
} from "@tabler/icons-react";
import { Loader, Tooltip, useMantineTheme } from "@mantine/core";
import { getCookies } from "../../../../helpers/cookies";

export function StartAndStopAction({
  successNotificationColor,
  errorNotificationColor,
  name,
  status,
  isLoading,
  refetchJobDetails,
  interactionDetails,
}: ActionProps) {
  const [showLoader, setShowLoader] = useState(false);
  const theme = useMantineTheme();
  const [showStopButton, setShowStopButton] = useState<boolean | undefined>();

  useEffect(() => {
    setShowStopButton(interactionDetails?.status === INTERACTION_STATUS.RUNNING);
  }, [interactionDetails?.status]);

  const updateJobStatusMutation = useUpdateInteraction(
    (response: UpdateInteractionOnSettledResponse) => {
      setShowLoader(false);
      if (response?.error) {
        showNotification(
          COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
          response.error.message || COMMON_CONSTANTS.DEFAULT_ERROR_MESSAGE,
          <IconSquareRoundedX />,
          errorNotificationColor,
        );

        setShowStopButton(true);
        return;
      }

      showNotification(
        COMMON_CONSTANTS.SUCCESS_NOTIFICATION_TITLE,
        `${showStopButton ? "Stopped" : "Started"} tracking ${name}`,
        <IconCircleCheckFilled />,
        successNotificationColor,
      );

      // setShowStopButton(false);
      refetchJobDetails && refetchJobDetails();
    },
  );

  const onClick = (event: React.MouseEvent) => {
    event.stopPropagation();
    setShowLoader(true);
    // setShowStopButton(showStopButton ? false : true);

    updateJobStatusMutation.mutateAsync({
      useCaseID: name ?? "",
      user: getCookies(COOKIES_KEY.USER_EMAIL) || "",
      jobDetails: {
        id: interactionDetails?.id,
        name: interactionDetails?.name || "",
        description: interactionDetails?.description || "",
        uptimeLowerLimitInMs: interactionDetails?.uptimeLowerLimitInMs || 0,
        uptimeMidLimitInMs: interactionDetails?.uptimeMidLimitInMs || 0,
        uptimeUpperLimitInMs: interactionDetails?.uptimeUpperLimitInMs || 0,
        thresholdInMs: interactionDetails?.thresholdInMs || 0,
        events: interactionDetails?.events || [],
        globalBlacklistedEvents: interactionDetails?.globalBlacklistedEvents || [],
        status: showStopButton
          ? INTERACTION_STATUS.STOPPED
          : INTERACTION_STATUS.RUNNING,
      },
    });
  };

  if (showLoader || isLoading) {
    return <Loader size={20} type="bars" />;
  }

  if (!status) return null;

  return (
    <Tooltip
      withArrow
      label={
        showStopButton
          ? TOOLTIP_LABLES.STOP_INTERACTION
          : TOOLTIP_LABLES.START_INTERACTION
      }
    >
      <span
        onClick={onClick}
        className={showLoader ? "disableActionButton" : ""}
        style={{
          display: "flex",
          alignItems: "center",
          padding: "6px",
          background: showStopButton
            ? "linear-gradient(135deg, rgba(239, 68, 68, 0.08), rgba(239, 68, 68, 0.12))"
            : "linear-gradient(135deg, rgba(14, 201, 194, 0.08), rgba(14, 201, 194, 0.12))",
          border: showStopButton
            ? "1px solid rgba(239, 68, 68, 0.2)"
            : "1px solid rgba(14, 201, 194, 0.2)",
          borderRadius: "8px",
          transition: "all 0.3s cubic-bezier(0.4, 0, 0.2, 1)",
          boxShadow: showStopButton
            ? "0 2px 6px rgba(239, 68, 68, 0.08)"
            : "0 2px 6px rgba(14, 201, 194, 0.08)",
          cursor: "pointer",
        }}
        onMouseEnter={(e) => {
          if (showStopButton) {
            (e.currentTarget as HTMLSpanElement).style.background =
              "linear-gradient(135deg, rgba(239, 68, 68, 0.12), rgba(239, 68, 68, 0.18))";
            (e.currentTarget as HTMLSpanElement).style.borderColor =
              "rgba(239, 68, 68, 0.3)";
            (e.currentTarget as HTMLSpanElement).style.boxShadow =
              "0 4px 12px rgba(239, 68, 68, 0.15)";
          } else {
            (e.currentTarget as HTMLSpanElement).style.background =
              "linear-gradient(135deg, rgba(14, 201, 194, 0.12), rgba(14, 201, 194, 0.18))";
            (e.currentTarget as HTMLSpanElement).style.borderColor =
              "rgba(14, 201, 194, 0.3)";
            (e.currentTarget as HTMLSpanElement).style.boxShadow =
              "0 4px 12px rgba(14, 201, 194, 0.15)";
          }
          (e.currentTarget as HTMLSpanElement).style.transform =
            "translateY(-1px)";
        }}
        onMouseLeave={(e) => {
          if (showStopButton) {
            (e.currentTarget as HTMLSpanElement).style.background =
              "linear-gradient(135deg, rgba(239, 68, 68, 0.08), rgba(239, 68, 68, 0.12))";
            (e.currentTarget as HTMLSpanElement).style.borderColor =
              "rgba(239, 68, 68, 0.2)";
            (e.currentTarget as HTMLSpanElement).style.boxShadow =
              "0 2px 6px rgba(239, 68, 68, 0.08)";
          } else {
            (e.currentTarget as HTMLSpanElement).style.background =
              "linear-gradient(135deg, rgba(14, 201, 194, 0.08), rgba(14, 201, 194, 0.12))";
            (e.currentTarget as HTMLSpanElement).style.borderColor =
              "rgba(14, 201, 194, 0.2)";
            (e.currentTarget as HTMLSpanElement).style.boxShadow =
              "0 2px 6px rgba(14, 201, 194, 0.08)";
          }
          (e.currentTarget as HTMLSpanElement).style.transform =
            "translateY(0)";
        }}
      >
        {showStopButton ? (
          <IconPlayerStop color={theme.colors.red[6]} size={18} />
        ) : (
          <IconPlayerPlay color="#0ba09a" size={18} />
        )}
      </span>
    </Tooltip>
  );
}
