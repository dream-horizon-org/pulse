import { useMemo } from "react";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { COLUMN_NAME } from "../../../constants/PulseOtelSemcov";
import { useGetDataQuery } from "../../../hooks/useGetDataQuery";
import { DEFAULT_REVENUE_EVENT_PREVIEW_DAYS } from "../RevenueEvent.types";

dayjs.extend(utc);

const DEDUPE_ROLLUP_LIMIT = 50_000;

function escapeChStringLiteral(value: string): string {
  return value.replace(/\\/g, "\\\\").replace(/'/g, "''");
}

export type DailyPoint = {
  date: string;
  eventCount: number;
  avgValue: number;
};

export type RevenueEventPreviewStats = {
  eventCount: number | null;
  uniqueInstallations: number | null;
  eventsPerInstallation: number | null;
  fillRate: number | null;
  avgValue: number | null;
  totalRevenue: number | null;
  dailyPoints: DailyPoint[];
  detectedCurrencies: { code: string; count: number }[];
  isLoading: boolean;
  isError: boolean;
};

type DedupeRow = {
  installationId: string;
  eventValue: number | null;
};


function readRows(
  fields: string[],
  rows: (string | number)[][] | undefined,
): Record<string, string | number>[] {
  if (!rows?.length) {
    return [];
  }
  return rows.map((row) => {
    const record: Record<string, string | number> = {};
    fields.forEach((field, idx) => {
      record[field] = row[idx];
    });
    return record;
  });
}

export function useRevenueEventPreview(
  eventName: string,
  valueAttribute: string,
  previewDays: number = DEFAULT_REVENUE_EVENT_PREVIEW_DAYS,
  currencyAttribute?: string | null,
  enableRevenueMetrics = false,
): RevenueEventPreviewStats {
  const safeDays = Math.min(Math.max(previewDays, 1), 30);

  const timeRange = useMemo(() => {
    const end = dayjs.utc();
    const start = end.subtract(safeDays, "day");
    return {
      start: start.toISOString(),
      end: end.toISOString(),
    };
  }, [safeDays]);

  const baseFilters = useMemo(
    () => [
      {
        field: COLUMN_NAME.PULSE_TYPE,
        operator: "EQ" as const,
        value: ["custom_event"],
      },
      {
        field: COLUMN_NAME.EVENT_NAME,
        operator: "EQ" as const,
        value: [eventName],
      },
    ],
    [eventName],
  );

  const attrKey = valueAttribute.trim();
  const safeAttr = escapeChStringLiteral(attrKey);
  const safeCurrencyAttr = currencyAttribute
    ? escapeChStringLiteral(currencyAttribute.trim())
    : "";

  const dedupeSummaryQuery = useGetDataQuery({
    requestBody: {
      dataType: "LOGS",
      timeRange,
      select: [
        {
          function: "COL",
          param: { field: COLUMN_NAME.INSTALLATION_ID },
          alias: "installation_id",
        },
        {
          function: "COL",
          param: { field: COLUMN_NAME.TIMESTAMP },
          alias: "event_ts",
        },
        ...(enableRevenueMetrics && attrKey
          ? [
              {
                function: "CUSTOM" as const,
                param: {
                  expression: `any(toFloat64OrZero(${COLUMN_NAME.LOG_ATTRIBUTES}['${safeAttr}']))`,
                },
                alias: "event_value",
              },
            ]
          : []),
      ],
      filters: baseFilters,
      groupBy: ["installation_id", "event_ts"],
      limit: DEDUPE_ROLLUP_LIMIT,
    },
    enabled: !!eventName,
  });

  const dedupeDailyQuery = useGetDataQuery({
    requestBody: {
      dataType: "LOGS",
      timeRange,
      select: [
        {
          function: "TIME_BUCKET",
          param: { bucket: "1d", field: COLUMN_NAME.TIMESTAMP },
          alias: "day",
        },
        {
          function: "COL",
          param: { field: COLUMN_NAME.INSTALLATION_ID },
          alias: "installation_id",
        },
        {
          function: "COL",
          param: { field: COLUMN_NAME.TIMESTAMP },
          alias: "event_ts",
        },
        ...(enableRevenueMetrics && attrKey
          ? [
              {
                function: "CUSTOM" as const,
                param: {
                  expression: `any(toFloat64OrZero(${COLUMN_NAME.LOG_ATTRIBUTES}['${safeAttr}']))`,
                },
                alias: "event_value",
              },
            ]
          : []),
      ],
      filters: baseFilters,
      groupBy: ["day", "installation_id", "event_ts"],
      orderBy: [{ field: "day", direction: "ASC" }],
      limit: DEDUPE_ROLLUP_LIMIT,
    },
    enabled: !!eventName,
  });

  const currencyQuery = useGetDataQuery({
    requestBody: {
      dataType: "LOGS",
      timeRange,
      select: [
        {
          function: "CUSTOM",
          param: {
            expression: `upper(trim(${COLUMN_NAME.LOG_ATTRIBUTES}['${safeCurrencyAttr}']))`,
          },
          alias: "currency_code",
        },
        {
          function: "CUSTOM",
          param: { expression: "count()" },
          alias: "event_count",
        },
      ],
      filters: baseFilters,
      groupBy: ["currency_code"],
      orderBy: [{ field: "event_count", direction: "DESC" }],
      limit: 10,
    },
    enabled:
      !!eventName && enableRevenueMetrics && !!safeCurrencyAttr,
  });

  return useMemo(() => {
    const loadingParts = [
      dedupeSummaryQuery.isLoading,
      dedupeDailyQuery.isLoading,
    ];
    if (enableRevenueMetrics && safeCurrencyAttr) {
      loadingParts.push(currencyQuery.isLoading);
    }

    const errorParts = [dedupeSummaryQuery.isError, dedupeDailyQuery.isError];
    if (enableRevenueMetrics && safeCurrencyAttr) {
      errorParts.push(currencyQuery.isError);
    }

    const isLoading = loadingParts.some(Boolean);
    const isError = errorParts.some(Boolean);

    const summaryFields = dedupeSummaryQuery.data?.data?.fields ?? [];
    const summaryRows = readRows(
      summaryFields,
      dedupeSummaryQuery.data?.data?.rows,
    );

    const summaryLoaded = dedupeSummaryQuery.data?.data !== undefined;
    const dailyLoaded = dedupeDailyQuery.data?.data !== undefined;

    const dedupeRows: DedupeRow[] = summaryRows.map((row) => ({
      installationId: String(row.installation_id ?? "").trim(),
      eventValue:
        enableRevenueMetrics && attrKey
          ? Number(row.event_value)
          : null,
    }));

    const eventCount = summaryLoaded ? dedupeRows.length : null;

    const installationIds = dedupeRows
      .map((row) => row.installationId)
      .filter((id) => id.length > 0);
    const uniqueInstallations = summaryLoaded
      ? new Set(installationIds).size
      : null;

    const eventsPerInstallation =
      eventCount !== null && uniqueInstallations && uniqueInstallations > 0
        ? eventCount / uniqueInstallations
        : null;

    let totalRevenue: number | null = null;
    let fillRate: number | null = null;
    let avgValue: number | null = null;

    if (enableRevenueMetrics && attrKey && eventCount !== null && eventCount > 0) {
      const values = dedupeRows.map((row) =>
        Number.isFinite(row.eventValue ?? NaN) ? (row.eventValue as number) : 0,
      );
      totalRevenue = values.reduce((sum, value) => sum + value, 0);
      const filledCount = values.filter((value) => value > 0).length;
      fillRate = (filledCount / eventCount) * 100;
      avgValue = totalRevenue / eventCount;
    }

    const dailyFields = dedupeDailyQuery.data?.data?.fields ?? [];
    const dailyRows = readRows(dailyFields, dedupeDailyQuery.data?.data?.rows);

    const dailyStatsByDate = new Map<
      string,
      { eventCount: number; valueSum: number }
    >();
    for (const row of dailyRows) {
      const day = String(row.day ?? "");
      if (!day) {
        continue;
      }
      const existing = dailyStatsByDate.get(day) ?? {
        eventCount: 0,
        valueSum: 0,
      };
      const eventValue =
        enableRevenueMetrics && attrKey
          ? Number(row.event_value)
          : 0;
      dailyStatsByDate.set(day, {
        eventCount: existing.eventCount + 1,
        valueSum:
          existing.valueSum +
          (Number.isFinite(eventValue) ? eventValue : 0),
      });
    }

    const dailyPoints = dailyLoaded
      ? Array.from(dailyStatsByDate.entries())
          .map(([date, stats]) => ({
            date,
            eventCount: stats.eventCount,
            avgValue:
              stats.eventCount > 0 ? stats.valueSum / stats.eventCount : 0,
          }))
          .sort((a, b) => a.date.localeCompare(b.date))
      : [];

    const currencyFields = currencyQuery.data?.data?.fields ?? [];
    const currencyRows = currencyQuery.data?.data?.rows ?? [];
    const codeIdx = currencyFields.indexOf("currency_code");
    const currCountIdx = currencyFields.indexOf("event_count");
    const detectedCurrencies: { code: string; count: number }[] = [];

    if (codeIdx >= 0 && currCountIdx >= 0) {
      for (const row of currencyRows) {
        const code = String(row[codeIdx] ?? "").trim();
        if (!code) {
          continue;
        }
        detectedCurrencies.push({
          code,
          count: Number(row[currCountIdx]) || 0,
        });
      }
    }

    return {
      eventCount,
      uniqueInstallations,
      eventsPerInstallation,
      fillRate,
      avgValue,
      totalRevenue,
      dailyPoints,
      detectedCurrencies,
      isLoading,
      isError,
    };
  }, [
    attrKey,
    enableRevenueMetrics,
    safeCurrencyAttr,
    dedupeSummaryQuery.data,
    dedupeSummaryQuery.isLoading,
    dedupeSummaryQuery.isError,
    dedupeDailyQuery.data,
    dedupeDailyQuery.isLoading,
    dedupeDailyQuery.isError,
    currencyQuery.data,
    currencyQuery.isLoading,
    currencyQuery.isError,
  ]);
}
