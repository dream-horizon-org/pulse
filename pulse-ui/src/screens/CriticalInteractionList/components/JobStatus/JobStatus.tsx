import { Badge, useMantineTheme } from "@mantine/core";
import { getBadgeColorClass } from "../../../../helpers/getBadgeColorClass";
import { Row } from "../../../../hooks/useGetInteractions";
import { COMMON_CONSTANTS } from "../../../../constants";
import { useGetJobStatus } from "../../../../hooks/useGetJobStatus";

export function JobStatus({ id }: Row) {
  const theme = useMantineTheme();
  const { data, isError, isLoading } = useGetJobStatus(id);

  if (isLoading) {
    return <Badge size="xs">Loading...</Badge>;
  }

  if (isError) {
    return <Badge size="xs">Error fetching</Badge>;
  }

  if (data?.error) {
    return <Badge size="xs">Error fetching</Badge>;
  }

  return id !== undefined && id > 0 ? (
    <Badge
      size="xs"
      color={
        getBadgeColorClass(
          data?.data?.status ?? COMMON_CONSTANTS.DEFAULT_BADGE_TEXT,
          theme,
        ) ?? theme.colors.primary[6]
      }
    >
      {data?.data?.status || "RUNNING"}
    </Badge>
  ) : (
    <Badge size="xs" color="red">
      No ID
    </Badge>
  );
}
