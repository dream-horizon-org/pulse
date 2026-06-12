import { Box, Card, Divider, Stack, Text } from "@mantine/core";
import { Link } from "react-router-dom";
import type { RelatedAttributionEntry } from "../../../../hooks/useGetErrorAttribution/useGetErrorAttribution.interface";
import { encodeNetworkId } from "../../../NetworkList/utils/networkIdUtils";
import { ERROR_ATTRIBUTION_MESSAGES } from "./ErrorAttribution.constants";
import classes from "./ErrorAttribution.module.css";
import rcaClasses from "../RootCause/RcaReportView.module.css";

export function UnifiedRelatedAttributionsList({
  rows,
  projectId,
  linkSuffix,
}: {
  rows: RelatedAttributionEntry[];
  projectId: string;
  linkSuffix: string;
}) {
  return (
    <Card
      withBorder
      padding="md"
      radius="md"
      className={rcaClasses.segmentCard}
    >
      <Stack gap={0}>
        {rows.map((row, idx) => {
          const rank = idx + 1;
          if (row.rowKind === "api") {
            const apiId = encodeNetworkId(
              row.url ?? "",
              row.graphqlOperationName ?? undefined,
              row.graphqlOperationType ?? undefined,
            );
            const operationName = row.graphqlOperationName?.trim() ?? "";
            const hasOperationName = operationName !== "";
            const urlDisplay = row.url?.trim() || "(no URL)";
            const operationType = row.graphqlOperationType?.trim() ?? "";
            const methodStatus =
              [row.httpMethod, row.httpStatusCode]
                .filter(Boolean)
                .join(" · ") || null;
            const to = `/projects/${encodeURIComponent(projectId)}/network-apis/${encodeURIComponent(apiId)}${linkSuffix}`;
            return (
              <Box key={`api-${apiId}-${idx}`}>
                {idx > 0 ? (
                  <Divider size="xs" className={classes.compactDivider} />
                ) : null}
                <div className={classes.compactRow}>
                  <div className={classes.compactRankBadge}>{rank}</div>
                  <Stack gap={2} style={{ flex: 1, minWidth: 0 }}>
                    <Link to={to} className={classes.drillDownLink}>
                      <Stack gap={2}>
                        <Text fw={600} size="sm" lh={1.35} lineClamp={2}>
                          {urlDisplay}
                        </Text>
                        {hasOperationName ? (
                          <Text size="xs" c="dimmed" lh={1.2} lineClamp={2}>
                            {[operationType, operationName]
                              .filter((part) => part !== "")
                              .join(" · ")}
                          </Text>
                        ) : null}
                        {methodStatus ? (
                          <Text size="xs" c="dimmed" lh={1.2}>
                            {methodStatus}
                          </Text>
                        ) : null}
                      </Stack>
                    </Link>
                  </Stack>
                  <Text size="xs" c="dimmed" style={{ flexShrink: 0 }} lh={1.2}>
                    {row.occurrences.toLocaleString()}{" "}
                    {ERROR_ATTRIBUTION_MESSAGES.DRILL_DOWN_SESSIONS}
                  </Text>
                </div>
              </Box>
            );
          }

          const to = `/projects/${encodeURIComponent(projectId)}/app-vitals/${encodeURIComponent(row.groupId ?? "")}${linkSuffix}`;
          const label =
            row.title && row.title.trim() !== ""
              ? row.title
              : row.groupId || "(issue)";
          const typeSuffix =
            row.sourceSignal === "non_fatal" && row.exceptionType
              ? ` (${row.exceptionType})`
              : "";
          return (
            <Box key={`issue-${row.groupId}-${row.exceptionType ?? ""}-${idx}`}>
              {idx > 0 ? (
                <Divider size="xs" className={classes.compactDivider} />
              ) : null}
              <div className={classes.compactRow}>
                <div className={classes.compactRankBadge}>{rank}</div>
                <Text
                  component={Link}
                  to={to}
                  fw={600}
                  size="sm"
                  className={classes.drillDownLink}
                  style={{ flex: 1, minWidth: 0 }}
                  lineClamp={2}
                  lh={1.35}
                >
                  {label}
                  {typeSuffix}
                </Text>
                <Text size="xs" c="dimmed" style={{ flexShrink: 0 }} lh={1.2}>
                  {row.occurrences.toLocaleString()}{" "}
                  {ERROR_ATTRIBUTION_MESSAGES.DRILL_DOWN_SESSIONS}
                </Text>
              </div>
            </Box>
          );
        })}
      </Stack>
    </Card>
  );
}
