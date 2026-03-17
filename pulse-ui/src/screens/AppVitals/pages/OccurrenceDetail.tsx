import { useParams, useNavigate } from "react-router-dom";
import { Box, Button, SimpleGrid } from "@mantine/core";
import { IconArrowLeft } from "@tabler/icons-react";
import {
  OccurrenceHeader,
  DeviceInformation,
  UserInformation,
  PerformanceMetricsSection,
  LogsAndStackTrace,
} from "../components";
import classes from "./OccurrenceDetail.module.css";

// Mock occurrence data
const generateOccurrenceData = (occurrenceId: string) => {
  return {
    timestamp: new Date("2025-11-07T14:23:45"),

    // Device Information
    device: {
      manufacturer: "Samsung",
      model: "Galaxy S23",
      osVersion: "Android 13",
      screenResolution: "2400 x 1080",
      ramTotal: "8 GB",
      ramAvailable: "3.2 GB",
      storageTotal: "128 GB",
      storageFree: "45 GB",
      batteryLevel: "67%",
      connectionType: "WiFi",
      carrier: "Verizon",
    },

    // User Information
    user: {
      userId: "user_12345",
      email: "john.doe@example.com",
      name: "John Doe",
      accountCreated: new Date("2023-05-15"),
      sessionId: "session_1026",
      locale: "en_US",
      timezone: "America/New_York",
      appVersion: "2.4.0",
      buildNumber: "145",
    },

    // App State
    appState: {
      foreground: true,
      screenName: "MainActivity",
      orientation: "Portrait",
      lowMemoryWarning: false,
      debugMode: false,
    },
  };
};

// Generate performance metrics data
const generatePerformanceData = () => {
  const data = [];
  for (let i = 0; i < 60; i++) {
    const timeLabel = `${60 - i}s`;

    const cpuBase = 30 + (i / 60) * 40;
    const cpuVariance = Math.random() * 10 - 5;
    const cpu = Math.min(100, Math.max(0, cpuBase + cpuVariance));

    const memoryBase = 50 + (i / 60) * 35;
    const memoryVariance = Math.random() * 8 - 4;
    const memory = Math.min(100, Math.max(0, memoryBase + memoryVariance));

    const fpsBase = 60 - (i / 60) * 35;
    const fpsVariance = Math.random() * 8 - 4;
    const fps = Math.max(0, fpsBase + fpsVariance);

    data.push({
      time: timeLabel,
      cpu: parseFloat(cpu.toFixed(1)),
      memory: parseFloat(memory.toFixed(1)),
      fps: parseFloat(fps.toFixed(1)),
    });
  }
  return data.reverse();
};

// Mock logs data
const MOCK_LOGS = [
  {
    timestamp: "14:23:42.123",
    level: "INFO",
    tag: "MainActivity",
    message: "Activity started successfully",
  },
  {
    timestamp: "14:23:42.456",
    level: "DEBUG",
    tag: "NetworkManager",
    message: "Fetching data from API endpoint: /api/v1/users/profile",
  },
  {
    timestamp: "14:23:43.001",
    level: "INFO",
    tag: "NetworkManager",
    message: "API response received: 200 OK",
  },
  {
    timestamp: "14:23:43.234",
    level: "WARN",
    tag: "DatabaseHelper",
    message: "Database query took longer than expected: 850ms",
  },
  {
    timestamp: "14:23:43.567",
    level: "DEBUG",
    tag: "ImageLoader",
    message: "Loading image from cache: user_avatar_12345.jpg",
  },
  {
    timestamp: "14:23:44.123",
    level: "INFO",
    tag: "MainActivity",
    message: "User interaction detected: Button clicked (id: btn_submit)",
  },
  {
    timestamp: "14:23:44.456",
    level: "ERROR",
    tag: "DataProcessor",
    message: "Failed to parse JSON response: Unexpected token at position 145",
  },
  {
    timestamp: "14:23:44.567",
    level: "DEBUG",
    tag: "ErrorHandler",
    message: "Attempting to recover from parsing error",
  },
  {
    timestamp: "14:23:44.789",
    level: "ERROR",
    tag: "MainActivity",
    message:
      "NullPointerException: Attempting to invoke virtual method on null object reference",
  },
  {
    timestamp: "14:23:45.001",
    level: "ERROR",
    tag: "CrashHandler",
    message: "Application crash detected. Generating crash report...",
  },
];

// Mock stack trace data
const MOCK_STACK_TRACE = [
  {
    className: "com.example.app.MainActivity",
    method: "onCreate()",
    file: "MainActivity.java",
    line: 127,
  },
  {
    className: "com.example.app.utils.DataProcessor",
    method: "processUserData()",
    file: "DataProcessor.java",
    line: 89,
  },
  {
    className: "com.example.app.network.ApiClient",
    method: "parseResponse()",
    file: "ApiClient.java",
    line: 234,
  },
  {
    className: "com.example.app.models.UserProfile",
    method: "fromJson()",
    file: "UserProfile.java",
    line: 45,
  },
  {
    className: "org.json.JSONObject",
    method: "getString()",
    file: "JSONObject.java",
    line: 612,
  },
  {
    className: "android.app.Activity",
    method: "performCreate()",
    file: "Activity.java",
    line: 7802,
  },
  {
    className: "android.app.ActivityThread",
    method: "handleLaunchActivity()",
    file: "ActivityThread.java",
    line: 3405,
  },
  {
    className: "android.app.ActivityThread",
    method: "access$1100()",
    file: "ActivityThread.java",
    line: 230,
  },
  {
    className: "android.app.ActivityThread$H",
    method: "handleMessage()",
    file: "ActivityThread.java",
    line: 1899,
  },
  {
    className: "android.os.Looper",
    method: "loop()",
    file: "Looper.java",
    line: 216,
  },
];

export const OccurrenceDetail: React.FC = () => {
  const { issueId, occurrenceId, projectId } = useParams<{
    issueId: string;
    occurrenceId: string;
    projectId: string;
  }>();
  const navigate = useNavigate();

  const occurrenceData = generateOccurrenceData(occurrenceId || "0");
  const performanceData = generatePerformanceData();

  const handleSessionReplayClick = () => {
    if (occurrenceData.user.sessionId) {
      // navigate(`/replays/${occurrenceData.user.sessionId}`);
    }
  };

  return (
    <Box className={classes.pageContainer}>
      {/* Back Button */}
      <Button
        variant="subtle"
        color="teal"
        leftSection={<IconArrowLeft size={16} />}
        onClick={() => navigate(`/projects/${projectId}/app-vitals/${issueId}`)}
        className={classes.backButton}
      >
        Back to Issue Details
      </Button>

      {/* Header */}
      <OccurrenceHeader
        timestamp={occurrenceData.timestamp}
        issueId={issueId || ""}
        occurrenceId={occurrenceId || "0"}
        occurrenceData={occurrenceData}
      />

      {/* Device and User Information */}
      <SimpleGrid cols={2} spacing="md" mb="md">
        <DeviceInformation device={occurrenceData.device} />
        <UserInformation user={occurrenceData.user} />
      </SimpleGrid>

      {/* Performance Metrics & Session Replay */}
      <PerformanceMetricsSection
        performanceData={performanceData}
        sessionId={occurrenceData.user.sessionId}
        onSessionReplayClick={handleSessionReplayClick}
      />

      {/* Logs and Stack Trace */}
      <LogsAndStackTrace logs={MOCK_LOGS} stackTrace={MOCK_STACK_TRACE} />
    </Box>
  );
};
