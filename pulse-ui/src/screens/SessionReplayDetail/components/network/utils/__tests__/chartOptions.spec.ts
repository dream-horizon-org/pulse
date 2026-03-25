import {
  createWaterfallOption,
  createStatusDistributionOption,
  createDurationOption,
} from "../chartOptions";
import type { NetworkRequest } from "../../../../../../services/sessionReplay/mockSessionDetail";
import { CHART_LABELS } from "../../../../constants/strings";

describe("chartOptions", () => {
  const mockRequests: NetworkRequest[] = [
    {
      timestamp: 0,
      method: "GET",
      url: "https://api.example.com/users",
      status: 200,
      duration: 100,
    },
    {
      timestamp: 200,
      method: "POST",
      url: "https://api.example.com/orders",
      status: 201,
      duration: 250,
    },
    {
      timestamp: 600,
      method: "GET",
      url: "https://api.example.com/health",
      status: 500,
      duration: 50,
    },
  ];

  describe("createWaterfallOption", () => {
    it("returns empty structure for empty requests", () => {
      const option = createWaterfallOption([]);
      expect(option.xAxis).toEqual({ type: "value" });
      expect(option.yAxis).toEqual({ type: "category", data: [] });
      expect(option.series).toEqual([]);
    });

    it("returns option with tooltip, axes and series for non-empty requests", () => {
      const option = createWaterfallOption(mockRequests);
      expect(option.tooltip).toBeDefined();
      expect(option.tooltip!.trigger).toBe("axis");
      expect(option.xAxis.type).toBe("value");
      expect(option.xAxis.name).toBe(CHART_LABELS.TIME_MS);
      expect(option.yAxis.type).toBe("category");
      expect(option.yAxis.data).toHaveLength(3);
      expect(option.yAxis.inverse).toBe(true);
      expect(option.series).toHaveLength(1);
      expect(option.series[0].type).toBe("bar");
      expect(option.series[0].name).toBe(CHART_LABELS.REQUEST_DURATION);
      expect(option.series[0].data).toHaveLength(3);
    });

    it("uses request url path or truncated url for bar names", () => {
      const option = createWaterfallOption(mockRequests);
      const names = option.yAxis.data as string[];
      expect(names[0]).toBe("users");
      expect(names[1]).toBe("orders");
      expect(names[2]).toBe("health");
    });

    it("assigns colors by status (2xx teal, 5xx red)", () => {
      const option = createWaterfallOption(mockRequests);
      const data = option.series[0].data as Array<{ itemStyle: { color: string } }>;
      expect(data[0].itemStyle.color).toBe("#0ec9c2");
      expect(data[1].itemStyle.color).toBe("#0ec9c2");
      expect(data[2].itemStyle.color).toBe("#ef4444");
    });
  });

  describe("createStatusDistributionOption", () => {
    it("aggregates status counts into 2xx, 4xx, 5xx groups", () => {
      const option = createStatusDistributionOption(mockRequests);
      expect(option.series).toHaveLength(1);
      expect(option.series[0].type).toBe("pie");
      expect(option.series[0].data).toHaveLength(2); // 2xx (2 reqs) and 5xx (1 req)
      const data = option.series[0].data as Array<{ name: string; value: number }>;
      const names = data.map((d) => d.name);
      expect(names).toContain(CHART_LABELS.STATUS_2XX_SUCCESS);
      expect(names).toContain(CHART_LABELS.STATUS_5XX_SERVER_ERROR);
    });

    it("uses correct label constants for status names", () => {
      const requestsWith4xx: NetworkRequest[] = [
        { timestamp: 0, method: "GET", url: "/a", status: 404, duration: 10 },
      ];
      const option = createStatusDistributionOption(requestsWith4xx);
      const data = option.series[0].data as Array<{ name: string }>;
      expect(data.some((d) => d.name === CHART_LABELS.STATUS_4XX_CLIENT_ERROR)).toBe(true);
    });
  });

  describe("createDurationOption", () => {
    it("returns option with category xAxis and duration values", () => {
      const option = createDurationOption(mockRequests);
      expect(option.xAxis.type).toBe("category");
      expect(option.xAxis.data).toHaveLength(3);
      expect(option.yAxis.type).toBe("value");
      expect(option.yAxis.name).toBe(CHART_LABELS.DURATION_MS);
      expect(option.series[0].type).toBe("bar");
      expect(option.series[0].name).toBe(CHART_LABELS.DURATION);
      expect(option.series[0].data).toEqual([100, 250, 50]);
    });

    it("rotates axis label when more than 4 requests", () => {
      const many = Array.from({ length: 5 }, (_, i) => ({
        timestamp: i * 100,
        method: "GET",
        url: `https://example.com/${i}`,
        status: 200,
        duration: 100,
      })) as NetworkRequest[];
      const option = createDurationOption(many);
      expect(option.xAxis.axisLabel.rotate).toBe(25);
    });
  });
});
