import {
  Badge,
  Box,
  List,
  Paper,
  Stack,
  Table,
  Text,
  Title,
} from "@mantine/core";
import type {
  ExperienceMixWire,
  HealthRating,
  InteractionReportV1Wire,
} from "../../../../hooks/useInteractionReport";

type UserExperienceCategory = "excellent" | "good" | "average" | "poor";
import classes from "./InteractionReport.module.css";

const BUSINESS_RISK_LABELS: Record<string, string> = {
  checkout_blocked: "Checkout blocked",
  payment_friction: "Payment friction",
  browse_friction: "Browse friction",
  na_test: "N/A (test)",
};

function ratingColor(rating: HealthRating | undefined): string {
  if (rating === "green") return "green";
  if (rating === "red") return "red";
  return "yellow";
}

function formatKpi(
  metric: string,
  display?: string | null,
  value?: number,
): string {
  if (display?.trim()) return display;
  if (value == null) return "—";
  if (metric === "error_rate") return `${value}%`;
  return String(value);
}

function experienceMixTotal(mix: ExperienceMixWire): number {
  return (
    mix.excellent_count + mix.good_count + mix.average_count + mix.poor_count
  );
}

function experiencePct(
  mix: ExperienceMixWire,
  category: UserExperienceCategory,
): string | null {
  const total = experienceMixTotal(mix);
  if (total === 0) return null;
  const mapping: Record<UserExperienceCategory, number> = {
    excellent: mix.excellent_count,
    good: mix.good_count,
    average: mix.average_count,
    poor: mix.poor_count,
  };
  return `${((100 * mapping[category]) / total).toFixed(1)}%`;
}

function DiagnosisLensSection({
  title,
  bullets,
}: {
  title: string;
  bullets: string[] | undefined;
}) {
  if (!bullets?.length) return null;
  return (
    <Box>
      <Text size="sm" fw={600}>
        {title}
      </Text>
      <List size="sm" className={classes.bulletList}>
        {bullets.map((b) => (
          <List.Item key={b}>{b}</List.Item>
        ))}
      </List>
    </Box>
  );
}

type InteractionReportViewProps = {
  report: InteractionReportV1Wire;
};

export function InteractionReportView({ report }: InteractionReportViewProps) {
  const {
    identity,
    verdict,
    user_impact,
    user_behavior,
    diagnosis,
    root_cause,
    actions,
    follow_up,
  } = report;
  const mix = user_impact.experience_mix;

  return (
    <Stack gap="md" className={classes.container}>
      <Paper withBorder p="md" className={classes.section}>
        <Title order={5} className={classes.sectionTitle}>
          1. Interaction identity
        </Title>
        <Text fw={600}>{identity.name}</Text>
        <Text size="sm">{identity.business_moment}</Text>
        <Text size="sm" c="dimmed">
          Events: {identity.start_event} → {identity.end_event}
        </Text>
        <Text size="sm" c="dimmed">
          Thresholds (ms): excellent ≤ {identity.thresholds.excellent_ms}, good
          ≤ {identity.thresholds.good_ms}, average ≤{" "}
          {identity.thresholds.average_ms}, timeout{" "}
          {identity.thresholds.timeout_ms}
        </Text>
        <Text size="sm" c="dimmed">
          Reporting period: {identity.reporting_period.start} —{" "}
          {identity.reporting_period.end}
        </Text>
      </Paper>

      <Paper withBorder p="md" className={classes.section}>
        <Title order={5} className={classes.sectionTitle}>
          2. Health verdict
        </Title>
        <Badge color={ratingColor(verdict.rating)} size="lg" variant="filled">
          {verdict.rating.toUpperCase()}
        </Badge>
        <Text size="sm" mt="xs">
          Primary: {verdict.primary_kpi.metric}{" "}
          {formatKpi(
            verdict.primary_kpi.metric,
            verdict.primary_kpi.display,
            verdict.primary_kpi.value,
          )}
          {" · "}
          Secondary: {verdict.secondary_kpi.metric}{" "}
          {formatKpi(
            verdict.secondary_kpi.metric,
            verdict.secondary_kpi.display,
            verdict.secondary_kpi.value,
          )}
          {verdict.poor_user_pct != null && (
            <> · Poor users {verdict.poor_user_pct}%</>
          )}
        </Text>
        <Text size="sm" className={classes.verdictSummary}>
          {verdict.summary}
        </Text>
      </Paper>

      <Paper withBorder p="md" className={classes.section}>
        <Title order={5} className={classes.sectionTitle}>
          3. User impact
        </Title>
        <Text size="sm">
          Volume: {user_impact.volume.toLocaleString()} · Error rate:{" "}
          {user_impact.error_rate_pct}% · Failures:{" "}
          {user_impact.failure_count.toLocaleString()}
        </Text>
        <Text size="sm" c="dimmed">
          Business risk:{" "}
          {BUSINESS_RISK_LABELS[user_impact.business_risk] ??
            user_impact.business_risk}
        </Text>
        <Text size="sm">{user_impact.funnel_link}</Text>
        <Box mt="sm" className={classes.mixGrid}>
          {(
            [
              [
                "Excellent",
                mix.excellent_count,
                experiencePct(mix, "excellent"),
              ],
              ["Good", mix.good_count, experiencePct(mix, "good")],
              ["Average", mix.average_count, experiencePct(mix, "average")],
              ["Poor", mix.poor_count, experiencePct(mix, "poor")],
            ] as const
          ).map(([label, count, pct]) => (
            <Box key={label}>
              <Text size="xs" c="dimmed">
                {label}
              </Text>
              <Text size="sm" fw={500}>
                {count.toLocaleString()}
                {pct != null ? ` (${pct})` : ""}
              </Text>
            </Box>
          ))}
        </Box>
        {user_impact.segment_highlights?.length ? (
          <Stack gap="xs" mt="md">
            <Text size="sm" fw={600}>
              Segment highlights
            </Text>
            {user_impact.segment_highlights.map((seg) => (
              <Box key={seg.label} className={classes.segmentCard}>
                <Text size="sm" fw={600}>
                  {seg.label}
                  {seg.rca_rank != null ? ` (rank ${seg.rca_rank})` : ""}
                </Text>
                <Text size="xs" c="dimmed">
                  Volume {seg.volume.toLocaleString()}
                  {seg.volume_pct_of_total != null &&
                    ` · ${seg.volume_pct_of_total}% of total`}
                  {seg.poor_user_pct != null && ` · Poor ${seg.poor_user_pct}%`}
                  {seg.delta_vs_baseline_poor_pct != null &&
                    ` · Δ poor ${seg.delta_vs_baseline_poor_pct > 0 ? "+" : ""}${seg.delta_vs_baseline_poor_pct}%`}
                </Text>
                <Text size="sm">{seg.impact_summary}</Text>
              </Box>
            ))}
          </Stack>
        ) : (
          <Text size="xs" c="dimmed" className={classes.emptyHint} mt="xs">
            No segment highlights (evenly spread or everything_good).
          </Text>
        )}
      </Paper>

      <Paper withBorder p="md" className={classes.section}>
        <Title order={5} className={classes.sectionTitle}>
          4. User behavior
        </Title>
        <Text size="sm" fw={600}>
          Happy path
        </Text>
        <Text size="sm">{user_behavior.flow_pattern.happy_path}</Text>
        {user_behavior.flow_pattern.deviant_paths?.length ? (
          <>
            <Text size="sm" fw={600} mt="xs">
              Deviant paths
            </Text>
            <List size="sm" className={classes.bulletList}>
              {user_behavior.flow_pattern.deviant_paths.map((p) => (
                <List.Item key={p}>{p}</List.Item>
              ))}
            </List>
          </>
        ) : null}
        {user_behavior.behavioral_signals?.length ? (
          <Stack gap="xs" mt="sm">
            <Text size="sm" fw={600}>
              Behavioral signals
            </Text>
            {user_behavior.behavioral_signals.map((s) => (
              <Box key={s.signal}>
                <Text size="sm" fw={500}>
                  {s.signal}
                </Text>
                <Text size="sm">{s.meaning}</Text>
              </Box>
            ))}
          </Stack>
        ) : (
          <Text size="xs" c="dimmed" className={classes.emptyHint} mt="xs">
            No behavioral signals recorded.
          </Text>
        )}
        {user_behavior.behavior_metric_links?.length ? (
          <Table mt="sm" withTableBorder>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>User action</Table.Th>
                <Table.Th>Effect on metrics</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {user_behavior.behavior_metric_links.map((row) => (
                <Table.Tr key={row.user_action}>
                  <Table.Td>{row.user_action}</Table.Td>
                  <Table.Td>{row.effect_on_metrics}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        ) : null}
        {user_behavior.cohort_behavior?.length ? (
          <List size="sm" mt="sm" className={classes.bulletList}>
            {user_behavior.cohort_behavior.map((c) => (
              <List.Item key={c.cohort}>
                <Text span fw={500}>
                  {c.cohort}:{" "}
                </Text>
                {c.observation}
              </List.Item>
            ))}
          </List>
        ) : null}
      </Paper>

      <Paper withBorder p="md" className={classes.section}>
        <Title order={5} className={classes.sectionTitle}>
          5. Diagnosis
        </Title>
        <Stack gap="sm">
          <DiagnosisLensSection
            title="Reliability"
            bullets={diagnosis.reliability}
          />
          <DiagnosisLensSection title="Latency" bullets={diagnosis.latency} />
          <DiagnosisLensSection
            title="Measurement"
            bullets={diagnosis.measurement}
          />
        </Stack>
      </Paper>

      <Paper withBorder p="md" className={classes.section}>
        <Title order={5} className={classes.sectionTitle}>
          6. Root cause
        </Title>
        <Text size="sm" fw={600}>
          {root_cause.primary_cause}
        </Text>
        <Text size="xs" c="dimmed">
          Confidence: {root_cause.confidence}
        </Text>
        {root_cause.contributing_factors?.length ? (
          <List size="sm" className={classes.bulletList} mt="xs">
            {root_cause.contributing_factors.map((f) => (
              <List.Item key={f}>{f}</List.Item>
            ))}
          </List>
        ) : null}
        {root_cause.ruled_out?.length ? (
          <>
            <Text size="sm" fw={600} mt="xs">
              Ruled out
            </Text>
            <List size="sm" className={classes.bulletList}>
              {root_cause.ruled_out.map((r) => (
                <List.Item key={r}>{r}</List.Item>
              ))}
            </List>
          </>
        ) : null}
        {root_cause.evidence?.length ? (
          <List size="sm" className={classes.bulletList} mt="xs">
            {root_cause.evidence.map((e) => (
              <List.Item key={`${e.source}-${e.detail}`}>
                [{e.source}] {e.detail}
              </List.Item>
            ))}
          </List>
        ) : null}
      </Paper>

      <Paper withBorder p="md" className={classes.section}>
        <Title order={5} className={classes.sectionTitle}>
          7. Actions
        </Title>
        {actions.length ? (
          <Stack gap="xs">
            {actions.map((a) => (
              <Box
                key={`${a.priority}-${a.action}`}
                className={classes.actionRow}
              >
                <Box style={{ display: "flex", alignItems: "center", gap: 6 }}>
                  <Badge size="xs" variant="outline">
                    {a.priority}
                  </Badge>
                  <Text size="sm" fw={600}>
                    {a.action}
                  </Text>
                </Box>
                <Text size="xs" c="dimmed">
                  {a.type} · {a.owner} · effort {a.effort} · target{" "}
                  {a.target_metric} · {a.expected_lift}
                </Text>
              </Box>
            ))}
          </Stack>
        ) : (
          <Text size="sm" c="dimmed" className={classes.emptyHint}>
            No actions listed.
          </Text>
        )}
      </Paper>

      <Paper withBorder p="md" className={classes.section}>
        <Title order={5} className={classes.sectionTitle}>
          8. Proof & follow-up
        </Title>
        <Text size="sm" fw={600}>
          Sample sessions
        </Text>
        <Text size="sm">{follow_up.sample_bad_session_ids.join(", ")}</Text>
        {follow_up.pulse_drill_down_filters?.length ? (
          <Text size="sm" mt="xs">
            Drill-down filters: {follow_up.pulse_drill_down_filters.join(", ")}
          </Text>
        ) : null}
        <Text size="sm" mt="xs">
          Next period targets:{" "}
          {[
            follow_up.next_period_targets.apdex_min != null &&
              `Apdex ≥ ${follow_up.next_period_targets.apdex_min}`,
            follow_up.next_period_targets.error_rate_max_pct != null &&
              `Error ≤ ${follow_up.next_period_targets.error_rate_max_pct}%`,
            follow_up.next_period_targets.poor_user_max_pct != null &&
              `Poor ≤ ${follow_up.next_period_targets.poor_user_max_pct}%`,
          ]
            .filter(Boolean)
            .join(" · ") || "—"}
        </Text>
        <Text size="sm" c="dimmed">
          Review date: {follow_up.review_date}
        </Text>
      </Paper>

      {report.generated_at && (
        <Text size="xs" c="dimmed">
          Generated {report.generated_at}
        </Text>
      )}
    </Stack>
  );
}

/** Exported for tests — maps health rating to Mantine alert/badge color. */
export { ratingColor };
