import { Box, Button, Drawer, Loader, ScrollArea, Text } from "@mantine/core";
import { useFunnelDropoff } from "../../hooks/useFunnelDropoff";
import { CauseRow } from "./components/CauseRow";
import type { DropoffPanelProps } from "./DropoffPanel.interface";
import classes from "./DropoffPanel.module.css";

/**
 * Side-panel explaining why users/sessions dropped at a given funnel step.
 *
 * <p>Renders a ranked list of causes (crash / ANR / HTTP / frozen-frame)
 * with lift vs converters. Each row expands into a per-session evidence
 * drill-in with deep links into replay and traces.
 *
 * <p>The panel is self-contained: pass {@code funnelId} + {@code stepIndex}
 * and it fetches everything it needs. {@code mode} displayed in the subtitle
 * comes straight from the backend payload so the drawer always matches the
 * funnel's cohort semantics.
 */
export function DropoffPanel({
  opened,
  onClose,
  funnelId,
  stepIndex,
  runTime,
  onFullRcaClick,
}: DropoffPanelProps) {
  const {
    data: response,
    isLoading,
    isError,
  } = useFunnelDropoff(
    opened ? funnelId : undefined,
    opened ? stepIndex : undefined,
    runTime,
  );
  const data = response?.data ?? null;

  const subjectPlural = data?.mode === "SESSIONS" ? "sessions" : "users";
  const stepTitle =
    typeof stepIndex === "number"
      ? `Step ${stepIndex + 1}${data?.stepName ? ` · ${data.stepName}` : ""}`
      : "Drop-off";

  return (
    <Drawer
      opened={opened}
      onClose={onClose}
      position="right"
      size="lg"
      title={null}
      withCloseButton={false}
      classNames={{
        content: classes.drawerContent,
        body: classes.drawerBody,
      }}
    >
      <Box className={classes.header}>
        <Box
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "flex-start",
          }}
        >
          <Box>
            <h2 className={classes.title}>Why did {subjectPlural} drop off?</h2>
            <Text className={classes.subtitle}>{stepTitle}</Text>
          </Box>
          <Button variant="subtle" size="sm" onClick={onClose}>
            &times;
          </Button>
        </Box>
      </Box>

      {data && !isLoading && (
        <Box className={classes.kpiRow}>
          <Box className={classes.kpi}>
            <Text className={classes.kpiValue}>
              {data.dropoffCohort.toLocaleString()}
            </Text>
            <Text className={classes.kpiLabel}>Droppers</Text>
          </Box>
          <Box className={classes.kpi}>
            <Text className={classes.kpiValue}>
              {data.converterCohort.toLocaleString()}
            </Text>
            <Text className={classes.kpiLabel}>Converters</Text>
          </Box>
          <Box className={classes.kpi}>
            <Text className={classes.kpiValue}>{data.causes?.length ?? 0}</Text>
            <Text className={classes.kpiLabel}>Causes</Text>
          </Box>
        </Box>
      )}

      <ScrollArea className={classes.body}>
        {isLoading && (
          <Box
            style={{
              display: "flex",
              justifyContent: "center",
              padding: "var(--mantine-spacing-xl)",
            }}
          >
            <Loader />
          </Box>
        )}

        {isError && (
          <Box className={classes.empty}>
            <Text c="red">Failed to load drop-off causes.</Text>
          </Box>
        )}

        {!isLoading && !isError && data && data.causes?.length === 0 && (
          <Box className={classes.empty}>
            <Text>No OTel signals lined up with this step's drop-off.</Text>
            <Text size="xs" mt="xs">
              This usually means users abandoned the flow without a crash or
              error — consider UX issues, auth walls, or load-time delays.
            </Text>
          </Box>
        )}

        {onFullRcaClick &&
          !isLoading &&
          !isError &&
          data &&
          data.causes?.length > 0 && (
            <Box mb="md">
              <Button
                variant="light"
                size="sm"
                onClick={() => {
                  onClose();
                  onFullRcaClick();
                }}
              >
                Full RCA report
              </Button>
            </Box>
          )}

        {!isLoading && !isError && data && data.causes?.length > 0 && (
          <>
            <Text className={classes.sectionTitle}>
              Ranked by attribution lift
            </Text>
            {data.causes.map((cause) => (
              <CauseRow
                key={`${cause.causeKind}:${cause.causeKey}`}
                cause={cause}
                funnelId={funnelId as string}
                stepIndex={stepIndex as number}
                runTime={runTime}
              />
            ))}
          </>
        )}
      </ScrollArea>
    </Drawer>
  );
}
