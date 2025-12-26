import { Box, Text, Group, Badge, Progress } from "@mantine/core";
import { useMemo } from "react";
import { ErrorBreakdownProps, ErrorDetail } from "./ErrorBreakdown.interface";
import classes from "./ErrorBreakdown.module.css";
import { useGetDataQuery } from "../../../../hooks/useGetDataQuery";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import { SkeletonLoader } from "../../../../components/Skeletons";

export const ErrorBreakdown: React.FC<ErrorBreakdownProps> = ({
  type,
  method,
  url,
  startTime,
  endTime,
  additionalFilters = [],
}) => {
  // Build status code filter based on type (4xx or 5xx)
  const statusCodePattern = type === "4xx" ? "network.4%" : "network.5%";

  const { data, isLoading, error } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES",
      timeRange: {
        start: startTime,
        end: endTime,
      },
      select: [
        {
          function: "CUSTOM" as const,
          param: {
            expression: "count()",
          },
          alias: "error_count",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "SpanAttributes['http.status_code']",
          },
          alias: "status_code",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "SpanAttributes['error.type']",
          },
          alias: "error_type",
        },
      ],
      groupBy: ["status_code", "error_type"],
      filters: [
        {
          field: "PulseType",
          operator: "LIKE" as const,
          value: [statusCodePattern],
        },
        {
          field: "SpanAttributes['http.method']",
          operator: "EQ" as const,
          value: [method],
        },
        {
          field: "SpanAttributes['http.url']",
          operator: "EQ" as const,
          value: [url],
        },
        ...additionalFilters,
      ],
      orderBy: [
        {
          field: "error_count",
          direction: "DESC" as const,
        },
      ],
      limit: 10,
    },
    enabled: !!method && !!url && !!startTime && !!endTime,
  });

  // Transform API response to ErrorDetail format
  const errorData: ErrorDetail[] = useMemo((): ErrorDetail[] => {
    if (!data?.data?.rows || data.data.rows.length === 0) {
      return [];
    }

    const fields = data.data.fields;
    const errorCountIndex = fields.indexOf("error_count");
    const statusCodeIndex = fields.indexOf("status_code");
    const errorTypeIndex = fields.indexOf("error_type");

    const errors = data.data.rows.map((row) => {
      const statusCode = row[statusCodeIndex] || "";
      const errorType = row[errorTypeIndex] || "_OTHER";
      const count = parseFloat(row[errorCountIndex]) || 0;

      return {
        statusCode: parseInt(statusCode, 10) || 0,
        errorType: String(errorType),
        count: Math.round(count),
      };
    });

    // Calculate total for percentage calculation
    const total = errors.reduce((sum, err) => sum + err.count, 0);

    // Format error type for display
    const formatErrorType = (errorType: string): string => {
      if (errorType === "_OTHER") {
        return "Other Error";
      }
      // Convert snake_case or camelCase to Title Case
      return errorType
        .replace(/_/g, " ")
        .replace(/([A-Z])/g, " $1")
        .trim()
        .split(" ")
        .map(
          (word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase(),
        )
        .join(" ");
    };

    return errors
      .map((err) => {
        const errorTypeDisplay = formatErrorType(err.errorType);
        return {
          statusCode: err.statusCode,
          errorType: err.errorType,
          name: errorTypeDisplay,
          count: err.count,
          percentage: total > 0 ? Math.round((err.count / total) * 100) : 0,
          description: `HTTP ${err.statusCode} - ${errorTypeDisplay}`,
        };
      })
      .sort((a, b) => b.count - a.count); // Sort by count descending
  }, [data]);

  const totalErrors = useMemo(
    () => errorData.reduce((sum, error) => sum + error.count, 0),
    [errorData],
  );

  const colorScheme = type === "4xx" ? "orange" : "red";
  const subtleColor = type === "4xx" ? "#f59e0b" : "#ef4444";
  const title = type === "4xx" ? "Client Errors (4xx)" : "Server Errors (5xx)";

  if (error || data?.error) {
    return (
      <ErrorAndEmptyState message="Failed to load error breakdown. Please try again." />
    );
  }

  if (isLoading) {
    return (
      <Box className={classes.container}>
        <Box mb="md">
          <Group justify="space-between" align="center">
            <Box>
              <SkeletonLoader height={16} width={150} radius="sm" />
              <SkeletonLoader height={12} width={200} radius="xs" />
            </Box>
            <SkeletonLoader height={24} width={80} radius="md" />
          </Group>
        </Box>
        <Box className={classes.errorList}>
          {Array.from({ length: 3 }).map((_, index) => (
            <Box key={index} className={classes.errorCard}>
              <Group justify="space-between" align="flex-start" mb="xs">
                <Group gap="sm" wrap="nowrap">
                  <SkeletonLoader height={28} width={50} radius="md" />
                  <Box>
                    <SkeletonLoader height={14} width={120} radius="sm" />
                    <SkeletonLoader height={12} width={180} radius="xs" />
                  </Box>
                </Group>
                <Box>
                  <SkeletonLoader height={14} width={40} radius="sm" />
                  <SkeletonLoader height={12} width={30} radius="xs" />
                </Box>
              </Group>
              <SkeletonLoader height={6} width="100%" radius="sm" />
            </Box>
          ))}
        </Box>
      </Box>
    );
  }

  if (errorData.length === 0) {
    return (
      <ErrorAndEmptyState message={`No ${type} errors found for this API`} />
    );
  }

  return (
    <Box className={classes.container}>
      <Box mb="md">
        <Group justify="space-between" align="center">
          <Box>
            <Text size="sm" fw={600} c="#0ba09a" mb={4}>
              {title}
            </Text>
            <Text size="xs" c="dimmed">
              Breakdown of HTTP {type} error codes
            </Text>
          </Box>
          <Badge
            size="sm"
            variant="light"
            color={colorScheme}
            style={{ opacity: 0.8 }}
          >
            {totalErrors.toLocaleString()} Total
          </Badge>
        </Group>
      </Box>

      <Box className={classes.errorList}>
        {errorData.map((error) => (
          <Box
            key={`${error.statusCode}-${error.errorType}`}
            className={classes.errorCard}
          >
            <Group justify="space-between" align="flex-start" mb="xs">
              <Group gap="sm" wrap="nowrap">
                <Badge
                  size="md"
                  variant="light"
                  color={colorScheme}
                  className={classes.codeBadge}
                >
                  {error.statusCode}
                </Badge>
                <Box>
                  <Text size="sm" fw={600} className={classes.errorName}>
                    {error.name}
                  </Text>
                  <Text
                    size="xs"
                    c="dimmed"
                    className={classes.errorDescription}
                  >
                    {error.description}
                  </Text>
                </Box>
              </Group>
              <Box className={classes.statsBox}>
                <Text
                  size="sm"
                  fw={600}
                  style={{ color: subtleColor, opacity: 0.85 }}
                  ta="right"
                >
                  {error.count.toLocaleString()}
                </Text>
                <Text size="xs" c="dimmed" ta="right">
                  {error.percentage}%
                </Text>
              </Box>
            </Group>

            <Progress
              value={error.percentage}
              color={colorScheme}
              size="xs"
              radius="sm"
              className={classes.progressBar}
              styles={{
                root: {
                  backgroundColor: "rgba(14, 201, 194, 0.05)",
                },
                section: {
                  opacity: 0.7,
                },
              }}
            />
          </Box>
        ))}
      </Box>

      {/* Summary Stats */}
      <Box className={classes.summary}>
        <Text size="xs" c="dimmed" ta="center">
          Most common error:{" "}
          <Text
            component="span"
            fw={600}
            style={{ color: subtleColor, opacity: 0.8 }}
          >
            {errorData[0].statusCode} {errorData[0].name}
          </Text>{" "}
          ({errorData[0].percentage}% of all {type} errors)
        </Text>
      </Box>
    </Box>
  );
};
