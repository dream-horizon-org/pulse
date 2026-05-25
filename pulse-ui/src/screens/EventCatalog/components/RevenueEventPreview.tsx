import { useMemo, useState, type ReactNode } from "react";
import {
  ActionIcon,
  Box,
  Loader,
  SegmentedControl,
  SimpleGrid,
  Text,
  ThemeIcon,
  Tooltip,
} from "@mantine/core";
import {
  IconChartLine,
  IconInfoCircle,
  IconShoppingCart,
  IconUsers,
} from "@tabler/icons-react";
import { REVENUE_EVENT_PREVIEW_DAYS_OPTIONS } from "../RevenueEvent.types";
import { useRevenueEventPreview } from "../hooks/useRevenueEventPreview";
import {
  normalizeCurrencyCode,
  pickDefaultCurrency,
} from "../revenueEventHelpers";
import { RevenueTrendChart } from "./RevenueTrendChart";
import classes from "./RevenueEventPreview.module.css";

type ChartTab = "volume" | "revenue";

type RevenueEventPreviewProps = {
  eventName: string;
  valueAttribute: string;
  manualCurrency: boolean;
  fixedCurrency: string;
  currencyAttribute: string | null;
  revenueMetricsReady: boolean;
  previewDays: number;
  onPreviewDaysChange: (days: number) => void;
};

function formatCount(value: number | null): string {
  if (value === null || !Number.isFinite(value)) {
    return "—";
  }
  return value.toLocaleString(undefined, { maximumFractionDigits: 0 });
}

function formatCountDecimal(value: number | null): string {
  if (value === null || !Number.isFinite(value)) {
    return "—";
  }
  return value.toLocaleString(undefined, { maximumFractionDigits: 1 });
}

function formatMoney(value: number | null, currency?: string): string {
  if (value === null || !Number.isFinite(value)) {
    return "—";
  }
  const formatted = value.toLocaleString(undefined, {
    maximumFractionDigits: value >= 1000 ? 0 : 2,
  });
  if (currency === "INR") {
    return `₹${formatted}`;
  }
  if (currency) {
    return `${currency} ${formatted}`;
  }
  return formatted;
}

function formatPercent(value: number | null): string {
  if (value === null || !Number.isFinite(value)) {
    return "—";
  }
  return `${value.toFixed(1)}%`;
}

type VerdictTone = "good" | "warn" | "muted";

type Verdict = {
  text: string;
  tone: VerdictTone;
  info: string;
};

function buildVerdict(
  eventCount: number | null,
  fillRate: number | null,
  totalRevenue: number | null,
  valueAttribute: string,
  revenueMetricsReady: boolean,
  previewDays: number,
): Verdict {
  if (!revenueMetricsReady) {
    if (eventCount === null || eventCount === 0) {
      return {
        text: "No purchase events in this window. Try a longer lookback or pick a different event.",
        tone: "warn",
        info: `We queried the last ${previewDays} days and found zero events for this name. Try 15 or 30 days, or pick another event from the catalogue.`,
      };
    }
    return {
      text: `${formatCount(eventCount)} purchase events found. Set value attribute and currency to preview AOV and revenue.`,
      tone: "good",
      info: `${formatCount(eventCount)} purchase events in the last ${previewDays} days. Add a value attribute and currency to check AOV before confirming.`,
    };
  }
  if (eventCount === null || eventCount === 0) {
    return {
      text: "No events in this window. Try a longer lookback or a different event.",
      tone: "muted",
      info: `No matching events in the last ${previewDays} days, so we cannot assess revenue quality yet.`,
    };
  }
  if (totalRevenue === null || totalRevenue === 0) {
    return {
      text: `No revenue on "${valueAttribute}". Check the attribute key or try another field.`,
      tone: "warn",
      info: `${formatCount(eventCount)} purchase events found, but "${valueAttribute}" is missing or zero on all of them in the last ${previewDays} days. Try another attribute or enter the key manually.`,
    };
  }
  if (fillRate !== null && fillRate < 50) {
    return {
      text: `Only ${formatPercent(fillRate)} of events have a value. Double-check "${valueAttribute}".`,
      tone: "warn",
      info: `Of ${formatCount(eventCount)} purchase events in the last ${previewDays} days, only ${formatPercent(fillRate)} have "${valueAttribute}" with a value greater than zero. At least 50% is needed before this looks reliable.`,
    };
  }
  return {
    text: "Revenue signal looks healthy. Safe to confirm this configuration.",
    tone: "good",
    info: [
      `Based on the last ${previewDays} days:`,
      `• ${formatCount(eventCount)} purchase events`,
      `• ${formatPercent(fillRate)} of events have a value on "${valueAttribute}" (at least 50% needed)`,
      "• Average order value looks usable on this attribute",
      "• Enough signal to save this as your revenue event",
    ].join("\n"),
  };
}

function VerdictBanner({ text, tone, info }: Verdict) {
  return (
    <div
      className={`${classes.verdictBanner} ${
        tone === "good"
          ? classes.verdictGood
          : tone === "warn"
            ? classes.verdictWarn
            : classes.verdictMuted
      }`}
    >
      <Text component="p" className={classes.verdictText}>
        {text}
      </Text>
      <Tooltip
        label={info}
        multiline
        w={280}
        withArrow
        position="top-start"
        styles={{ tooltip: { whiteSpace: "pre-line" } }}
      >
        <ActionIcon
          variant="subtle"
          color="gray"
          size="sm"
          aria-label="Why this assessment"
          className={classes.verdictInfoBtn}
        >
          <IconInfoCircle size={16} />
        </ActionIcon>
      </Tooltip>
    </div>
  );
}

type StatCardAccent = "teal" | "violet" | "cyan";

const STAT_CARD_GRADIENTS: Record<
  StatCardAccent,
  { from: string; to: string }
> = {
  teal: { from: "#0ec9c2", to: "#0ba09a" },
  violet: { from: "#9775fa", to: "#7950f2" },
  cyan: { from: "#22b8cf", to: "#1098ad" },
};

function StatCard({
  label,
  value,
  metricLabel,
  info,
  icon,
  accent = "teal",
}: {
  label: string;
  value: string;
  metricLabel: string;
  info: string;
  icon: ReactNode;
  accent?: StatCardAccent;
}) {
  return (
    <Box className={classes.statCard} data-accent={accent}>
      <div className={classes.statCardTop}>
        <ThemeIcon
          size={28}
          radius="md"
          variant="gradient"
          gradient={{ ...STAT_CARD_GRADIENTS[accent], deg: 135 }}
          className={classes.statIcon}
        >
          {icon}
        </ThemeIcon>
        <div className={classes.statCardMeta}>
          <Text component="span" className={classes.statLabel}>
            {label}
          </Text>
          <Text component="span" className={classes.statMetricLabel}>
            {metricLabel}
          </Text>
        </div>
        <Tooltip label={info} multiline w={260} withArrow position="top">
          <ActionIcon
            variant="subtle"
            color="gray"
            size="xs"
            aria-label={`About ${label}`}
            className={classes.statInfoBtn}
          >
            <IconInfoCircle size={14} />
          </ActionIcon>
        </Tooltip>
      </div>
      <Text component="p" className={classes.statValue}>
        {value}
      </Text>
    </Box>
  );
}

export function RevenueEventPreview({
  eventName,
  valueAttribute,
  manualCurrency,
  fixedCurrency,
  currencyAttribute,
  revenueMetricsReady,
  previewDays,
  onPreviewDaysChange,
}: RevenueEventPreviewProps) {
  const [chartTab, setChartTab] = useState<ChartTab>("volume");

  const preview = useRevenueEventPreview(
    eventName,
    revenueMetricsReady ? valueAttribute : "",
    previewDays,
    revenueMetricsReady && !manualCurrency ? currencyAttribute : null,
    revenueMetricsReady,
  );

  const displayCurrency = manualCurrency
    ? fixedCurrency
    : pickDefaultCurrency(
        preview.detectedCurrencies
          .map((c) => normalizeCurrencyCode(c.code))
          .filter((c): c is string => c !== null),
      );

  const avgDailyEvents = useMemo(() => {
    if (preview.eventCount === null || previewDays <= 0) {
      return null;
    }
    return preview.eventCount / previewDays;
  }, [preview.eventCount, previewDays]);

  const verdict = buildVerdict(
    preview.eventCount,
    preview.fillRate,
    preview.totalRevenue,
    valueAttribute,
    revenueMetricsReady,
    previewDays,
  );

  const previewDaysControl = (
    <SegmentedControl
      className={classes.previewDaysControl}
      size="xs"
      value={String(previewDays)}
      onChange={(val) => onPreviewDaysChange(Number(val))}
      data={[...REVENUE_EVENT_PREVIEW_DAYS_OPTIONS]}
    />
  );

  const hasVolumeData = preview.dailyPoints.some((p) => p.eventCount > 0);
  const hasAovData = preview.dailyPoints.some((p) => p.avgValue > 0);
  const showChart = hasVolumeData || (revenueMetricsReady && hasAovData);
  const chartMode = chartTab === "revenue" ? "aov" : "volume";

  const purchaseEventsInfo = [
    `How many times this event fired in the last ${previewDays} days.`,
    "Use this to confirm you picked an event that actually represents purchases — not a rare or test event.",
    avgDailyEvents !== null
      ? `About ${formatCountDecimal(avgDailyEvents)} per day on average.`
      : null,
  ]
    .filter(Boolean)
    .join(" ");

  const uniqueInstallationsInfo = [
    "How many distinct app installations triggered this event.",
    "Compare with purchase events: a lower number usually means repeat buyers; close to purchase count means mostly one purchase per installation.",
    preview.eventsPerInstallation !== null
      ? `About ${formatCountDecimal(preview.eventsPerInstallation)} purchase events per installation on average.`
      : null,
  ]
    .filter(Boolean)
    .join(" ");

  const aovInfo = revenueMetricsReady
    ? [
        `Average "${valueAttribute}" per purchase event in the selected window.`,
        "Check that this looks like a realistic order amount for your app — if not, change the value attribute before saving.",
        displayCurrency ? `Shown in ${displayCurrency}.` : null,
      ]
        .filter(Boolean)
        .join(" ")
    : "";

  if (!eventName) {
    return (
      <Box className={classes.revenuePreview}>
        <div className={classes.previewPanelHeader}>
          <div>
            <Text className={classes.previewPanelTitle}>Event insights</Text>
            <Text className={classes.previewPanelSubtitle}>
              Updates live as you configure
            </Text>
          </div>
        </div>

        <div className={classes.emptyGuide}>
          <Text className={classes.emptyGuideTitle}>
            Configure with confidence
          </Text>
          <ol className={classes.emptyGuideSteps}>
            <li>
              <span className={classes.stepLead}>Pick a revenue event</span>
              <span className={classes.stepDetail}>
                See purchase volume and unique installations
              </span>
            </li>
            <li>
              <span className={classes.stepLead}>
                Set value attribute and currency
              </span>
              <span className={classes.stepDetail}>
                Unlock AOV and daily trends
              </span>
            </li>
            <li>
              <span className={classes.stepLead}>Confirm</span>
              <span className={classes.stepDetail}>
                When the numbers look right for your funnel
              </span>
            </li>
          </ol>
        </div>
      </Box>
    );
  }

  return (
    <Box className={classes.revenuePreview}>
      <div className={classes.previewPanelHeader}>
        <div>
          <Text className={classes.previewPanelTitle}>Event insights</Text>
          <Text className={classes.previewPanelSubtitle}>
            Last {previewDays} days
            <span className={classes.eventNameDot}> · </span>
            <span className={classes.eventName}>{eventName}</span>
          </Text>
        </div>
        {previewDaysControl}
      </div>

      {preview.isLoading ? (
        <Box py="xl" ta="center">
          <Loader size="sm" color="teal" />
          <Text className={classes.loadingText}>Loading event data…</Text>
        </Box>
      ) : preview.isError ? (
        <Text className={classes.emptyPreview} c="red">
          Could not load preview. Check the event name and try again.
        </Text>
      ) : (
        <div className={classes.insightSection}>
          <Text className={classes.insightSectionTitle}>Purchase activity</Text>

          <SimpleGrid
            cols={revenueMetricsReady ? { base: 2, lg: 3 } : 2}
            spacing="sm"
            className={classes.statGrid}
          >
            <StatCard
              label="Purchase events"
              value={formatCount(preview.eventCount)}
              metricLabel="Count"
              info={purchaseEventsInfo}
              icon={<IconShoppingCart size={15} stroke={1.75} />}
              accent="teal"
            />
            {revenueMetricsReady ? (
              <>
                <StatCard
                  label="Unique installations"
                  value={formatCount(preview.uniqueInstallations)}
                  metricLabel="Count"
                  info={uniqueInstallationsInfo}
                  icon={<IconUsers size={15} stroke={1.75} />}
                  accent="teal"
                />
                <StatCard
                  label="Average order value"
                  value={formatMoney(preview.avgValue, displayCurrency)}
                  metricLabel="Avg"
                  info={aovInfo}
                  icon={<IconChartLine size={15} stroke={1.75} />}
                  accent="teal"
                />
              </>
            ) : (
              <StatCard
                label="Unique installations"
                value={formatCount(preview.uniqueInstallations)}
                metricLabel="Count"
                info={uniqueInstallationsInfo}
                icon={<IconUsers size={15} stroke={1.75} />}
                accent="teal"
              />
            )}
          </SimpleGrid>

          {showChart && (
            <Box className={classes.chartSection}>
              <div className={classes.chartTitleRow}>
                <Text className={classes.chartTitle}>Daily trend</Text>
                {revenueMetricsReady ? (
                  <SegmentedControl
                    className={classes.chartModeControl}
                    size="xs"
                    value={chartTab}
                    onChange={(val) => setChartTab(val as ChartTab)}
                    data={[
                      { value: "volume", label: "Volume" },
                      { value: "revenue", label: "Revenue" },
                    ]}
                  />
                ) : (
                  <Text className={classes.chartBadge}>Events per day</Text>
                )}
              </div>
              {chartTab === "revenue" && !hasAovData ? (
                <Text className={classes.chartEmpty}>
                  No order values on &quot;{valueAttribute}&quot; in the last{" "}
                  {previewDays} days.
                </Text>
              ) : (
                <RevenueTrendChart
                  points={preview.dailyPoints}
                  currency={displayCurrency}
                  mode={chartMode}
                  height={188}
                />
              )}
            </Box>
          )}

          {verdict.text && <VerdictBanner {...verdict} />}
        </div>
      )}
    </Box>
  );
}
