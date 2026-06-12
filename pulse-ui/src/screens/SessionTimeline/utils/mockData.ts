import {
  SessionTimelineEvent,
  SessionSummary,
} from "../SessionTimeline.interface";

export const generateMockSessionData = (
  sessionId: string,
): {
  summary: SessionSummary;
  events: SessionTimelineEvent[];
} => {
  const events: SessionTimelineEvent[] = [
    {
      id: "1",
      name: "app.launch",
      type: "trace",
      timestamp: 0,
      duration: 2500,
      attributes: {
        resource: {
          "service.name": "android-app",
          "service.version": "1.2.3",
          "device.model": "Pixel 7",
          "device.manufacturer": "Google",
          "os.version": "Android 13",
          "device.screen.resolution": "2400 x 1080",
          "device.memory.total": "8 GB",
          "geo.state": "Maharashtra",
        },
        span: {
          "span.kind": "server",
          "http.method": "GET",
          "http.status_code": 200,
        },
        events: [
          {
            name: "app.initialized",
            timestamp: 100,
            attributes: { "init.time": "120ms" },
          },
        ],
      },
    },
    {
      id: "2",
      name: "User Authentication",
      type: "log",
      timestamp: 500,
    },
    {
      id: "3",
      name: "screen.view",
      type: "span",
      timestamp: 1200,
    },
    {
      id: "4",
      name: "UI Freeze",
      type: "frozen_frame",
      timestamp: 3000,
      duration: 850,
    },
    {
      id: "5",
      name: "messages.load",
      type: "trace",
      timestamp: 4500,
      duration: 800,
      attributes: {
        resource: {
          "service.name": "android-app",
        },
        span: {
          "span.kind": "client",
          "db.system": "sqlite",
          "db.query": "SELECT * FROM messages",
        },
      },
      children: [
        {
          id: "5-1",
          name: "database.query",
          type: "span",
          timestamp: 4500,
          duration: 200,
          attributes: {
            span: {
              "db.system": "sqlite",
              "db.name": "messages.db",
              "db.operation": "SELECT",
            },
          },
        },
        {
          id: "5-2",
          name: "ui.render",
          type: "span",
          timestamp: 4700,
          duration: 150,
        },
        {
          id: "5-3",
          name: "validate.input",
          type: "span",
          timestamp: 4850,
          duration: 100,
        },
        {
          id: "5-4",
          name: "network.request",
          type: "span",
          timestamp: 4950,
          duration: 350,
        },
        {
          id: "5-5",
          name: "ui.update",
          type: "span",
          timestamp: 5300,
          duration: 0,
        },
      ],
    },
    {
      id: "6",
      name: "Application Not Respo...",
      type: "anr",
      timestamp: 6000,
      duration: 5200,
    },
    {
      id: "7",
      name: "button.click",
      type: "log",
      timestamp: 12000,
    },
    {
      id: "8",
      name: "message.send",
      type: "span",
      timestamp: 12500,
      duration: 300,
    },
    {
      id: "9",
      name: "Memory Warning",
      type: "log",
      timestamp: 15000,
    },
    {
      id: "10",
      name: "Error Loading Images",
      type: "log",
      timestamp: 16000,
    },
    {
      id: "11",
      name: "OutOfMemoryError",
      type: "crash",
      timestamp: 180000,
      attributes: {
        resource: {
          "service.name": "android-app",
          "device.memory.total": "8GB",
          "device.memory.available": "512MB",
        },
        span: {
          "error.type": "OutOfMemoryError",
          "error.message": "Java heap space",
          "error.stack": "at com.example.App.onCreate(...)",
        },
        events: [
          {
            name: "memory.warning",
            timestamp: 179000,
            attributes: { "memory.used": "95%" },
          },
        ],
      },
    },
  ];

  return {
    summary: {
      sessionId,
      platform: "android",
      status: "crashed",
      duration: 180000,
      crashes: 1,
      anrs: 2,
      frozenFrames: 5,
      totalEvents: 25,
    },
    events,
  };
};
