import { useEffect } from "react";
import { useMantineTheme } from "@mantine/core";
import { IconSquareRoundedX } from "@tabler/icons-react";
import { ErrorAndEmptyState } from "../../../../../../components/ErrorAndEmptyState";
import { showNotification } from "../../../../../../helpers/showNotification";
import commonStyles from "../../common.module.css";
import { ErrorAndEmptyStateWithNotificationProps } from "./ErrorAndEmptyStateWithNotification.interface";

export function ErrorAndEmptyStateWithNotification({
  message,
  showNotification: shouldShowNotification = true,
  isError = true,
  errorDetails,
}: ErrorAndEmptyStateWithNotificationProps) {
  const theme = useMantineTheme();

  useEffect(() => {
    if (shouldShowNotification && isError) {
      const notificationMessage = errorDetails || "Something went wrong";
      showNotification(
        "Error",
        notificationMessage,
        <IconSquareRoundedX />,
        theme.colors.red[6],
      );
    }
  }, [
    message,
    shouldShowNotification,
    isError,
    errorDetails,
    theme.colors.red,
  ]);

  return (
    <ErrorAndEmptyState
      classes={[commonStyles.centeredContainer]}
      message={message}
    />
  );
}
