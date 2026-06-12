import { useMantineTheme } from "@mantine/core";
import { Row } from "../../../../hooks/useGetInteractions";
import { StartAndStopAction } from "./StartAndStopAction";

export function Actions({ status, id }: Row) {
  const theme = useMantineTheme();

  return (
    <StartAndStopAction
      status={status}
      successNotificationColor={theme.colors.teal[6]}
      errorNotificationColor={theme.colors.red[6]}
      iconColor={theme.colors.red[6]}
      jobId={id}
    />
  );
}
