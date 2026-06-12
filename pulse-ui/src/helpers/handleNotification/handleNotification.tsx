import { MantineTheme } from "@mantine/core";
import { IconCheck, IconX } from "@tabler/icons-react";
import { showNotification } from "../showNotification";

export enum NotificationTypes {
  SUCCESS = "SUCCESS",
  ERROR = "ERROR",
}

type NotificationProps = {
  type: NotificationTypes;
  message: string;
  description: string;
  theme: MantineTheme;
};
export const handleNotification = ({
  type,
  message,
  description,
  theme,
}: NotificationProps) => {
  const isSuccess = type === NotificationTypes.SUCCESS;

  const finalMessage =
    message?.trim() ??
    (isSuccess
      ? "Operation completed successfully."
      : "An unexpected error occurred.");

  const finaldescription =
    description?.trim() ?? "No additional information available.";

  showNotification(
    finalMessage,
    finaldescription,
    isSuccess ? <IconCheck /> : <IconX />,
    isSuccess ? theme.colors.green[6] : theme.colors.red[6],
  );
};
