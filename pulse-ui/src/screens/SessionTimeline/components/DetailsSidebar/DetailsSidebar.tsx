/**
 * DetailsSidebar Component
 * 
 * Slide-out panel that displays detailed information about a selected
 * timeline item (span, log, or exception). Fetches additional details
 * from the API and displays attributes, events, and links.
 */

import { useState } from "react";
import { Box, Text, Button, Loader, ScrollArea, Badge } from "@mantine/core";
import {
  IconX,
  IconTag,
  IconList,
  IconLink,
  IconFileText,
  IconClock,
  IconHash,
} from "@tabler/icons-react";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

import { FlameChartNode, getColorForPulseType, formatPulseType } from "../../utils/flameChartTransform";
import { formatDuration, formatTimestamp } from "../../utils/formatters";
import { useGetSpanDetails } from "../../../../hooks/useGetSpanDetails";
import { ExceptionDetails, AttributeList } from "./components";
import classes from "./DetailsSidebar.module.css";
import type { AttributeValue } from "../../../../types/attributes";
import type { ExceptionDetailsResponse } from "../../../../hooks/useGetSpanDetails/useGetSpanDetails.interface";

dayjs.extend(utc);

// ============================================================================
// Types
// ============================================================================

interface DetailsSidebarProps {
  item: FlameChartNode | null;
  onClose: () => void;
}

type TabType = "attributes" | "events" | "links";

/**
 * Get status badge class
 */
function getStatusClass(statusCode: string): string {
  const status = statusCode?.toLowerCase();
  if (status === "error") return classes.statusError;
  if (status === "ok") return classes.statusOk;
  return classes.statusUnset;
}

export function DetailsSidebar({ item, onClose }: DetailsSidebarProps) {
  const [activeTab, setActiveTab] = useState<TabType>("attributes");

  const isLog = item?.type === "log" || item?.type === "orphan-log";
  const isException = item?.type === "exception";
  const getMetadataString = (value?: AttributeValue | null) =>
    value === null || value === undefined ? "" : String(value);

  // Determine dataType for fetching details
  const getDataType = (): "TRACES" | "LOGS" | "EXCEPTIONS" => {
    if (isException) return "EXCEPTIONS";
    if (isLog) return "LOGS";
    return "TRACES";
  };

  // Fetch detailed attributes on demand
  const { data: details, isLoading } = useGetSpanDetails({
    dataType: getDataType(),
    traceId: item?.traceId || "",
    spanId: item?.spanId || "",
    timestamp: getMetadataString(item?.metadata?.timestamp),
    groupId: getMetadataString(item?.metadata?.groupId),
    enabled: !!item,
  });

  if (!item) return null;

  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  // Get attributes based on type - merge API response with pre-fetched metadata attributes
  
  // For resource attributes: prefer API response, fallback to pre-fetched from metadata
  const apiResourceAttributes = details && "resourceAttributes" in details ? details.resourceAttributes : {};
  const metadataResourceAttributes =
    (item.metadata?.resourceAttributes as Record<string, AttributeValue>) || {};
  const resourceAttributes = Object.keys(apiResourceAttributes).length > 0 
    ? apiResourceAttributes 
    : metadataResourceAttributes;
  
  // For main attributes (log/span/exception attributes): prefer API response, fallback to pre-fetched
  const apiMainAttributes = isException
    ? (details && "logAttributes" in details ? details.logAttributes : {})
    : isLog
      ? (details && "logAttributes" in details ? details.logAttributes : {})
      : (details && "spanAttributes" in details ? details.spanAttributes : {});
  const metadataMainAttributes = isLog 
    ? ((item.metadata?.logAttributes as Record<string, AttributeValue>) || {})
    : {};
  
  // Build simple metadata attributes from item.metadata (excluding internal/complex fields)
  const simpleMetadataAttributes: Record<string, AttributeValue> = {};
  if (item.metadata) {
    const excludeKeys = ["timestamp", "pulseType", "logAttributes", "resourceAttributes"]; // These are shown elsewhere or handled separately
    for (const [key, value] of Object.entries(item.metadata)) {
      if (!excludeKeys.includes(key) && value !== undefined && value !== null && value !== "") {
        simpleMetadataAttributes[key] = value as AttributeValue;
      }
    }
  }
  
  // Merge: API attributes take precedence, then pre-fetched attributes, then simple metadata
  const mainAttributes = { 
    ...simpleMetadataAttributes, 
    ...metadataMainAttributes, 
    ...apiMainAttributes 
  };
  
  // Exception-specific details from API
  const exceptionDetails =
    isException && details && "exceptionStackTrace" in details
      ? (details as ExceptionDetailsResponse)
      : null;
  const pulseType = getMetadataString(item.metadata?.pulseType);
  const statusCode = getMetadataString(item.metadata?.statusCode);
  const timestamp = getMetadataString(item.metadata?.timestamp);
  const spanKind = getMetadataString(item.metadata?.spanKind);
  const serviceName = getMetadataString(item.metadata?.serviceName);
  const severityText = getMetadataString(item.metadata?.severityText);
  const logBody = getMetadataString(item.metadata?.body);
  
  const events = !isLog && !isException && details && "events" in details ? details.events : [];
  const links = !isLog && !isException && details && "links" in details ? details.links : [];


  const renderEvents = () => {
    if (!events || events.length === 0) {
      return <Box className={classes.emptyAttributes}>No events attached to this span</Box>;
    }

    return (
      <Box>
        {events.map((event, idx) => (
          <Box key={idx} className={classes.eventItem}>
            <Box className={classes.eventHeader}>
              <Text className={classes.eventName}>{event.name}</Text>
              <Text className={classes.eventTimestamp}>{formatTimestamp(event.timestamp)}</Text>
            </Box>
            {Object.keys(event.attributes).length > 0 && (
              <AttributeList attributes={event.attributes} />
            )}
          </Box>
        ))}
      </Box>
    );
  };

  const renderLinks = () => {
    if (!links || links.length === 0) {
      return <Box className={classes.emptyAttributes}>No links attached to this span</Box>;
    }

    return (
      <Box>
        {links.map((link, idx) => (
          <Box key={idx} className={classes.linkItem}>
            <Text className={classes.linkTraceId}>TraceId: {link.traceId}</Text>
            <Text className={classes.linkSpanId}>SpanId: {link.spanId}</Text>
            {Object.keys(link.attributes).length > 0 && (
              <Box style={{ marginTop: 8 }}>
                <AttributeList attributes={link.attributes} />
              </Box>
            )}
          </Box>
        ))}
      </Box>
    );
  };

  return (
    <>
      <Box className={classes.backdrop} onClick={handleBackdropClick} />
      <Box className={classes.sidebar}>
        {/* Header */}
        <Box className={classes.header}>
          <Box className={classes.headerTitle}>
            <Box className={classes.titleIndicator} />
            <Text className={classes.title}>
              {isException ? "Exception Details" : isLog ? "Log Details" : "Span Details"}
            </Text>
          </Box>
          <Button
            variant="subtle"
            size="xs"
            className={classes.closeButton}
            onClick={onClose}
          >
            <IconX size={18} />
          </Button>
        </Box>

        {/* Content */}
        <ScrollArea className={classes.content}>
          {/* Basic Info */}
          <Box className={classes.section}>
            <Box className={classes.sectionHeader}>
              <IconFileText size={16} className={classes.sectionIcon} />
              <Text className={classes.sectionTitle}>Overview</Text>
            </Box>

            <Box className={classes.metaGrid}>
              <Box className={classes.metaItem} style={{ gridColumn: "1 / -1" }}>
                <Text className={classes.metaLabel}>Name</Text>
                <Text className={classes.metaValue}>{item.name}</Text>
              </Box>

              <Box className={classes.metaItem}>
                <Text className={classes.metaLabel}>Type</Text>
                <Badge 
                  className={classes.typeBadge}
                  style={{ 
                    backgroundColor: getColorForPulseType(pulseType),
                    color: "#ffffff",
                  }}
                >
                  {formatPulseType(pulseType)}
                </Badge>
              </Box>

              {!isLog && statusCode && (
                <Box className={classes.metaItem}>
                  <Text className={classes.metaLabel}>Status</Text>
                  <Badge className={`${classes.statusBadge} ${getStatusClass(statusCode)}`}>
                    {statusCode}
                  </Badge>
                </Box>
              )}

              {/* Duration - only for spans */}
              {!isLog && (
                <Box className={classes.metaItem}>
                  <Text className={classes.metaLabel}>
                    <IconClock size={12} style={{ marginRight: 4 }} />
                    Duration
                  </Text>
                  <Text className={classes.metaValue}>{formatDuration(item.duration)}</Text>
                </Box>
              )}

              <Box className={classes.metaItem}>
                <Text className={classes.metaLabel}>Start Offset</Text>
                <Text className={classes.metaValue}>{formatDuration(item.start)}</Text>
              </Box>

              {/* Timestamp for logs */}
              {isLog && timestamp && (
                <Box className={classes.metaItem} style={{ gridColumn: "1 / -1" }}>
                  <Text className={classes.metaLabel}>
                    <IconClock size={12} style={{ marginRight: 4 }} />
                    Timestamp
                  </Text>
                  <Text className={classes.metaValueMono}>
                    {formatTimestamp(timestamp)}
                  </Text>
                </Box>
              )}

              {/* Start Time and End Time for spans */}
              {!isLog && timestamp && (
                <>
                  <Box className={classes.metaItem} style={{ gridColumn: "1 / -1" }}>
                    <Text className={classes.metaLabel}>
                      <IconClock size={12} style={{ marginRight: 4 }} />
                      Start Time
                    </Text>
                    <Text className={classes.metaValueMono}>
                      {formatTimestamp(timestamp)}
                    </Text>
                  </Box>
                  <Box className={classes.metaItem} style={{ gridColumn: "1 / -1" }}>
                    <Text className={classes.metaLabel}>
                      <IconClock size={12} style={{ marginRight: 4 }} />
                      End Time
                    </Text>
                    <Text className={classes.metaValueMono}>
                      {(() => {
                        // Parse as UTC, add duration, then convert to local time
                        const startTime = dayjs.utc(timestamp);
                        if (!startTime.isValid()) return "—";
                        // Duration is in milliseconds, add to start time and convert to local
                        const endTime = startTime.add(item.duration, "milliseconds").local();
                        return endTime.format("MMM D, YYYY HH:mm:ss.SSS");
                      })()}
                    </Text>
                  </Box>
                </>
              )}

              <Box className={classes.metaItem} style={{ gridColumn: "1 / -1" }}>
                <Text className={classes.metaLabel}>
                  <IconHash size={12} style={{ marginRight: 4 }} />
                  Trace ID
                </Text>
                <Text className={classes.metaValueMono}>
                  {item.traceId}
                </Text>
              </Box>

              <Box className={classes.metaItem} style={{ gridColumn: "1 / -1" }}>
                <Text className={classes.metaLabel}>Span ID</Text>
                <Text className={classes.metaValueMono}>
                  {item.spanId}
                </Text>
              </Box>

              {item.parentSpanId && (
                <Box className={classes.metaItem} style={{ gridColumn: "1 / -1" }}>
                  <Text className={classes.metaLabel}>Parent Span ID</Text>
                  <Text className={classes.metaValueMono}>
                    {item.parentSpanId}
                  </Text>
                </Box>
              )}

              {serviceName && (
                <Box className={classes.metaItem}>
                  <Text className={classes.metaLabel}>Service</Text>
                  <Text className={classes.metaValue}>{serviceName}</Text>
                </Box>
              )}

              {spanKind && (
                <Box className={classes.metaItem}>
                  <Text className={classes.metaLabel}>Kind</Text>
                  <Text className={classes.metaValue}>{spanKind}</Text>
                </Box>
              )}

              {isLog && severityText && (
                <Box className={classes.metaItem}>
                  <Text className={classes.metaLabel}>Severity</Text>
                  <Text className={classes.metaValue}>{severityText}</Text>
                </Box>
              )}
            </Box>

            {/* Log Body */}
            {isLog && logBody && (
              <Box style={{ marginTop: 16 }}>
                <Text className={classes.metaLabel} mb={4}>Body</Text>
                <Box className={classes.bodyContent}>
                  {logBody}
                </Box>
              </Box>
            )}

            {/* Exception Details - Extracted to sub-component */}
            {isException && (
              <ExceptionDetails item={item} exceptionDetails={exceptionDetails} />
            )}

          </Box>

          {/* Tabs for Attributes/Events/Links */}
          <Box className={classes.tabs}>
            <button
              className={`${classes.tab} ${activeTab === "attributes" ? classes.tabActive : ""}`}
              onClick={() => setActiveTab("attributes")}
            >
              <IconTag size={14} style={{ marginRight: 4 }} />
              Attributes
            </button>
            {!isLog && !isException && (
              <>
                <button
                  className={`${classes.tab} ${activeTab === "events" ? classes.tabActive : ""}`}
                  onClick={() => setActiveTab("events")}
                >
                  <IconList size={14} style={{ marginRight: 4 }} />
                  Events ({events?.length || 0})
                </button>
                <button
                  className={`${classes.tab} ${activeTab === "links" ? classes.tabActive : ""}`}
                  onClick={() => setActiveTab("links")}
                >
                  <IconLink size={14} style={{ marginRight: 4 }} />
                  Links ({links?.length || 0})
                </button>
              </>
            )}
          </Box>

          {/* Tab Content */}
          {isLoading ? (
            <Box className={classes.loadingContainer}>
              <Loader color="teal" size="md" />
              <Text size="sm" c="dimmed">Loading details...</Text>
            </Box>
          ) : (
            <>
              {activeTab === "attributes" && (
                <>
                  <Box className={classes.section}>
                    <Box className={classes.sectionHeader}>
                      <Text className={classes.sectionTitle}>
                        {isException ? "Log Attributes" : isLog ? "Log Attributes" : "Span Attributes"}
                      </Text>
                      <Badge className={classes.sectionBadge}>
                        {Object.keys(mainAttributes).length}
                      </Badge>
                    </Box>
                    <AttributeList attributes={mainAttributes} emptyMessage="No attributes found" />
                  </Box>

                  <Box className={classes.section}>
                    <Box className={classes.sectionHeader}>
                      <Text className={classes.sectionTitle}>Resource Attributes</Text>
                      <Badge className={classes.sectionBadge}>
                        {Object.keys(resourceAttributes).length}
                      </Badge>
                    </Box>
                    <AttributeList attributes={resourceAttributes} emptyMessage="No resource attributes found" />
                  </Box>
                </>
              )}

              {activeTab === "events" && !isLog && (
                <Box className={classes.section}>
                  <Box className={classes.sectionHeader}>
                    <Text className={classes.sectionTitle}>Span Events</Text>
                    <Badge className={classes.sectionBadge}>
                      {events?.length || 0}
                    </Badge>
                  </Box>
                  {renderEvents()}
                </Box>
              )}

              {activeTab === "links" && !isLog && (
                <Box className={classes.section}>
                  <Box className={classes.sectionHeader}>
                    <Text className={classes.sectionTitle}>Span Links</Text>
                    <Badge className={classes.sectionBadge}>
                      {links?.length || 0}
                    </Badge>
                  </Box>
                  {renderLinks()}
                </Box>
              )}
            </>
          )}
        </ScrollArea>
      </Box>
    </>
  );
}

