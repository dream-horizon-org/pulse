import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { registerAppVitalsTools } from "./appVitals.js";
import { registerSessionTools } from "./sessions.js";
import { registerInteractionTools } from "./interactions.js";
import { registerMetricsTools } from "./metrics.js";
import { registerEventTools } from "./events.js";
import { registerFunnelTools } from "./funnels.js";
import { registerJourneyTools } from "./journeys.js";
import { registerAlertTools } from "./alerts.js";
import { registerHeatmapTools } from "./heatmap.js";
import { registerSdkConfigTools } from "./sdkConfig.js";

export const CATEGORY_NAMES = [
  "crashes",
  "sessions",
  "interactions",
  "events",
  "funnels",
  "journeys",
  "alerts",
  "heatmap",
  "sdk",
] as const;

type Category = (typeof CATEGORY_NAMES)[number];

const CATEGORIES: Record<Category, (server: McpServer) => void> = {
  crashes: registerAppVitalsTools,
  sessions: registerSessionTools,
  interactions: (server) => {
    registerInteractionTools(server);
    registerMetricsTools(server);
  },
  events: registerEventTools,
  funnels: registerFunnelTools,
  journeys: registerJourneyTools,
  alerts: registerAlertTools,
  heatmap: registerHeatmapTools,
  sdk: registerSdkConfigTools,
};

const registeredCategories = new Set<Category>();

export function registerRegisterTool(server: McpServer): void {
  server.tool(
    "register_tools",
    `Unlock tool categories for this session before using domain-specific tools.
Available categories:
- crashes: app vitals — crash groups, ANRs, non-fatals, stack traces, trends
- sessions: session replay listing
- interactions: critical interactions, RCA, APDEX score, error rate, response time
- events: event definitions, categories, search
- funnels: funnel analysis and metrics
- journeys: user journey flows
- alerts: alert rules, evaluation history, notification channels
- heatmap: touch heatmap data
- sdk: SDK configuration and rules

Call this first with the categories relevant to the user's request, then proceed with the unlocked tools.`,
    {
      categories: z
        .array(z.enum(CATEGORY_NAMES))
        .min(1)
        .describe("Categories to unlock, e.g. ['crashes', 'interactions']"),
    },
    async ({ categories }) => {
      const newlyRegistered: Category[] = [];
      const alreadyActive: Category[] = [];

      for (const category of categories) {
        if (registeredCategories.has(category)) {
          alreadyActive.push(category);
        } else {
          CATEGORIES[category](server);
          registeredCategories.add(category);
          newlyRegistered.push(category);
        }
      }

      if (newlyRegistered.length > 0) {
        await server.server.sendToolListChanged();
      }

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify({
              registered: newlyRegistered,
              already_active: alreadyActive,
              active_categories: [...registeredCategories],
            }),
          },
        ],
      };
    },
  );
}
