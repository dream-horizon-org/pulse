import { notifications } from "@mantine/notifications";

export const showNotification = (
  title: string,
  message: string,
  icon: React.ReactNode,
  color: string = "",
) => {
  return notifications.show({
    title: title,
    message: message,
    icon: icon,
    color: color,
  });
};
