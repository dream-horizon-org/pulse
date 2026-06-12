import { Box, Text } from "@mantine/core";
import { useNavigate, useParams } from "react-router-dom";
import { useMemo } from "react";
import { ApiCallListProps, ApiCall } from "./ApiCallList.interface";
import classes from "./ApiCallList.module.css";
import { NetworkApiCard } from "../NetworkApiCard";

export const ApiCallList: React.FC<ApiCallListProps> = ({ screenName }) => {
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string }>();

  // Mock API calls data for the screen
  const apiCalls: ApiCall[] = useMemo(
    () => [
      {
        id: "1",
        endpoint: "/api/v1/users/profile",
        method: "GET",
        avgResponseTime: 245,
        requestCount: 12543,
        successRate: 99.2,
        errorRate: 0.8,
        trend: [240, 238, 245, 250, 242, 248, 245],
      },
      {
        id: "2",
        endpoint: "/api/v1/content/feed",
        method: "GET",
        avgResponseTime: 489,
        requestCount: 8234,
        successRate: 98.5,
        errorRate: 1.5,
        trend: [480, 485, 490, 495, 488, 492, 489],
      },
      {
        id: "3",
        endpoint: "/api/v1/analytics/track",
        method: "POST",
        avgResponseTime: 156,
        requestCount: 25678,
        successRate: 99.8,
        errorRate: 0.2,
        trend: [150, 152, 155, 158, 154, 157, 156],
      },
      {
        id: "4",
        endpoint: "/api/v1/notifications/list",
        method: "GET",
        avgResponseTime: 328,
        requestCount: 5432,
        successRate: 97.3,
        errorRate: 2.7,
        trend: [320, 325, 330, 335, 326, 332, 328],
      },
      {
        id: "5",
        endpoint: "/api/v1/settings/update",
        method: "PUT",
        avgResponseTime: 412,
        requestCount: 1567,
        successRate: 96.8,
        errorRate: 3.2,
        trend: [400, 405, 410, 415, 408, 414, 412],
      },
      {
        id: "6",
        endpoint: "/api/v1/media/upload",
        method: "POST",
        avgResponseTime: 1245,
        requestCount: 3421,
        successRate: 94.5,
        errorRate: 5.5,
        trend: [1200, 1220, 1240, 1260, 1235, 1255, 1245],
      },
    ],
    [],
  );

  const handleApiClick = (apiId: string) => {
    // Navigate using apiId instead of screenName/endpoint
    navigate(`/projects/${projectId}/network-apis/${apiId}`);
  };

  return (
    <Box className={classes.container}>
      <Box mb="lg">
        <Text
          size="sm"
          fw={600}
          c="#0ba09a"
          mb={4}
          style={{ fontSize: "16px", letterSpacing: "-0.3px" }}
        >
          API Calls
        </Text>
        <Text size="xs" c="dimmed" style={{ fontSize: "12px" }}>
          Network requests made by this screen
        </Text>
      </Box>

      <Box className={classes.apiList}>
        {apiCalls.map((apiCall) => (
          <NetworkApiCard
            key={apiCall.id}
            apiData={apiCall}
            onClick={() => handleApiClick(apiCall.id)}
          />
        ))}
      </Box>
    </Box>
  );
};
