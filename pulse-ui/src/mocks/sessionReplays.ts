import type {
  SessionReplayResponse,
  SessionReplayData,
} from "../hooks/useGetSessionReplays/useGetSessionReplays.interface";

interface GenerateMockSessionReplaysParams {
  interactionName: string;
  startTime: string;
  endTime: string;
  page: number;
  pageSize: number;
  eventTypes?: (
    | "crash"
    | "anr"
    | "networkError"
    | "frozenFrame"
    | "nonFatal"
    | "completed"
  )[];
  device?: "all" | "ios" | "android";
}

export const generateMockSessionReplays = ({
  interactionName,
  startTime,
  endTime,
  page,
  pageSize,
  eventTypes = ["crash", "anr", "networkError", "frozenFrame", "nonFatal", "completed"],
  device = "all",
}: GenerateMockSessionReplaysParams): SessionReplayResponse => {
  const start = new Date(startTime).getTime();
  const end = new Date(endTime).getTime();
  const timeRange = end - start;

  // Realistic device distribution (weighted towards popular devices in India)
  const deviceOptions = [
    { name: "Samsung Galaxy S23", weight: 0.25, os: "Android" },
    { name: "Samsung Galaxy A54", weight: 0.20, os: "Android" },
    { name: "Redmi Note 12", weight: 0.18, os: "Android" },
    { name: "OnePlus 11", weight: 0.12, os: "Android" },
    { name: "iPhone 14 Pro", weight: 0.10, os: "iOS" },
    { name: "iPhone 13", weight: 0.08, os: "iOS" },
    { name: "Google Pixel 7", weight: 0.04, os: "Android" },
    { name: "Vivo V27", weight: 0.03, os: "Android" },
  ];
  
  const osVersions: Record<"Android" | "iOS", string[]> = {
    Android: ["Android 13", "Android 12", "Android 14", "Android 11"],
    iOS: ["iOS 17.0", "iOS 16.5", "iOS 16.6", "iOS 15.7"],
  };

  // Realistic event type distribution (most sessions complete successfully)
  const eventTypeWeights = [
    { type: "completed" as const, weight: 0.75 },
    { type: "networkError" as const, weight: 0.10 },
    { type: "nonFatal" as const, weight: 0.08 },
    { type: "frozenFrame" as const, weight: 0.04 },
    { type: "crash" as const, weight: 0.02 },
    { type: "anr" as const, weight: 0.01 },
  ];

  const getWeightedRandom = <T>(items: { item: T; weight: number }[]): T => {
    const totalWeight = items.reduce((sum, item) => sum + item.weight, 0);
    let random = Math.random() * totalWeight;
    for (const { item, weight } of items) {
      random -= weight;
      if (random <= 0) return item;
    }
    return items[0].item;
  };

  const totalSessions = 247;
  const allSessions: SessionReplayData[] = [];

  for (let i = 0; i < totalSessions; i++) {
    const sessionStart = new Date(start + Math.random() * timeRange);
    const eventType = getWeightedRandom(
      eventTypeWeights.map((e) => ({ item: e.type, weight: e.weight }))
    );
    const selectedDevice = getWeightedRandom(
      deviceOptions.map((d) => ({ item: d, weight: d.weight }))
    );
    const isIOS = selectedDevice.os === "iOS";

    if (device !== "all") {
      const matchesDevice =
        (device === "ios" && isIOS) || (device === "android" && !isIOS);
      if (!matchesDevice) {
        continue;
      }
    }

    // Realistic duration based on event type
    let duration_ms: number;
    if (eventType === "completed") {
      duration_ms = Math.floor(Math.random() * 180000) + 20000; // 20-200s for completed
    } else if (eventType === "crash" || eventType === "anr") {
      duration_ms = Math.floor(Math.random() * 30000) + 5000; // 5-35s for crashes
    } else {
      duration_ms = Math.floor(Math.random() * 120000) + 10000; // 10-130s for other errors
    }

    // Realistic event and screen counts based on duration
    const eventCount = Math.floor((duration_ms / 1000) * (2 + Math.random() * 3)); // 2-5 events per second
    const screenCount = Math.floor((duration_ms / 1000) * (0.1 + Math.random() * 0.2)); // 0.1-0.3 screens per second

    allSessions.push({
      id: `session_${String(i + 1).padStart(6, "0")}`,
      user_id: `user_${String(Math.floor(Math.random() * 50000) + 10000).padStart(5, "0")}`,
      phone_number: `+91${String(Math.floor(Math.random() * 9000000000) + 1000000000)}`,
      device: selectedDevice.name,
      os_version: osVersions[selectedDevice.os as "Android" | "iOS"][Math.floor(Math.random() * osVersions[selectedDevice.os as "Android" | "iOS"].length)],
      start_time: sessionStart.toISOString(),
      duration_ms: duration_ms,
      event_count: Math.max(1, eventCount),
      screen_count: Math.max(1, screenCount),
      event_type: eventType,
      event_names: undefined,
      interaction_name: interactionName,
      screens_visited: "",
      trace_id: `trace_${String(i + 1).padStart(6, "0")}`,
    });
  }

  const filteredSessions = allSessions.filter((session) =>
    eventTypes.includes(session.event_type),
  );

  const sortedSessions = filteredSessions.sort(
    (a, b) =>
      new Date(a.start_time).getTime() - new Date(b.start_time).getTime(),
  );

  const startIndex = page * pageSize;
  const endIndex = startIndex + pageSize;
  const paginatedSessions = sortedSessions.slice(startIndex, endIndex);

  const crashedCount = filteredSessions.filter((s) =>
    ["crash", "anr", "frozenFrame", "nonFatal"].includes(s.event_type),
  ).length;
  const completedCount = filteredSessions.filter(
    (s) => s.event_type === "networkError",
  ).length;

  return {
    data: paginatedSessions,
    pagination: {
      page,
      pageSize,
      totalItems: filteredSessions.length,
      totalPages: Math.ceil(filteredSessions.length / pageSize),
      hasNextPage: endIndex < filteredSessions.length,
      hasPreviousPage: page > 0,
    },
    stats: {
      total: filteredSessions.length,
      completed: completedCount,
      crashed: crashedCount,
      avgDuration:
        filteredSessions.reduce((sum, s) => sum + s.duration_ms, 0) /
        filteredSessions.length,
    },
  };
};

