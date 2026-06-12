/**
 * ExceptionDetails Component
 * 
 * Displays detailed information for exception items including:
 * - Exception type and message
 * - Screen context and active interactions
 * - Device and app information
 * - Stack trace
 * - Grouping information (Group ID, Fingerprint)
 */

import { Box, Text } from "@mantine/core";
import { ExceptionDetailsResponse } from "../../../../../hooks/useGetSpanDetails/useGetSpanDetails.interface";
import { FlameChartNode } from "../../../utils/flameChartTransform";
import classes from "../DetailsSidebar.module.css";

interface ExceptionDetailsProps {
  item: FlameChartNode;
  exceptionDetails: ExceptionDetailsResponse | null;
}

export function ExceptionDetails({ item, exceptionDetails }: ExceptionDetailsProps) {
  const metadataScreenName =
    item.metadata?.screenName !== undefined && item.metadata?.screenName !== null
      ? String(item.metadata.screenName)
      : null;
  const metadataExceptionType =
    item.metadata?.exceptionType !== undefined && item.metadata?.exceptionType !== null
      ? String(item.metadata.exceptionType)
      : null;
  const metadataExceptionMessage =
    item.metadata?.exceptionMessage !== undefined && item.metadata?.exceptionMessage !== null
      ? String(item.metadata.exceptionMessage)
      : null;
  const metadataGroupId =
    item.metadata?.groupId !== undefined && item.metadata?.groupId !== null
      ? String(item.metadata.groupId)
      : null;
  const screenName = exceptionDetails?.screenName || metadataScreenName || null;
  const interactionIds =
    exceptionDetails?.interactionIds || exceptionDetails?.interactions || [];
  const hasContext = Boolean(screenName) || interactionIds.length > 0;

  const stackTrace =
    typeof exceptionDetails?.exceptionStackTrace === "string"
      ? exceptionDetails.exceptionStackTrace
      : exceptionDetails?.exceptionStackTrace
      ? JSON.stringify(exceptionDetails.exceptionStackTrace, null, 2)
      : null;

  return (
    <Box mt="md">
      {/* Exception Type */}
      {(exceptionDetails?.exceptionType || metadataExceptionType) && (
        <Box className={classes.exceptionSection}>
          <Text className={classes.exceptionSectionLabel}>Exception Type</Text>
          <Text className={classes.exceptionType}>
            {exceptionDetails?.exceptionType || metadataExceptionType}
          </Text>
        </Box>
      )}

      {/* Exception Message */}
      {(exceptionDetails?.exceptionMessage || metadataExceptionMessage) && (
        <Box className={classes.exceptionSection}>
          <Text className={classes.exceptionSectionLabel}>Message</Text>
          <Text className={classes.exceptionMessage}>
            {exceptionDetails?.exceptionMessage || metadataExceptionMessage}
          </Text>
        </Box>
      )}

      {/* Screen & Interactions Row */}
      {hasContext && (
        <Box className={classes.exceptionSection}>
          <Text className={classes.exceptionSectionLabel}>Context</Text>
          <Box className={classes.infoCard}>
            {screenName && (
              <Box className={classes.infoItem} mb="xs">
                <Text className={classes.infoLabel}>Screen</Text>
                <Text className={classes.screenBadge}>
                  {screenName}
                </Text>
              </Box>
            )}
            {interactionIds.length > 0 && (
              <Box className={classes.infoItem}>
                <Text className={classes.infoLabel}>Active Interactions</Text>
                <Box className={classes.interactionsList}>
                  {interactionIds.map((interaction, idx) => (
                    <Text key={idx} className={classes.interactionBadge}>
                      {interaction}
                    </Text>
                  ))}
                </Box>
              </Box>
            )}
          </Box>
        </Box>
      )}

      {/* Device/App Info - Grid Layout */}
      {exceptionDetails &&
        (exceptionDetails.platform ||
          exceptionDetails.osVersion ||
          exceptionDetails.deviceModel ||
          exceptionDetails.appVersion ||
          exceptionDetails.sdkVersion ||
          exceptionDetails.userId) && (
          <Box className={classes.exceptionSection}>
            <Text className={classes.exceptionSectionLabel}>Device & App</Text>
            <Box className={classes.infoCard}>
              <Box className={classes.infoGrid}>
                {exceptionDetails.platform && (
                  <Box className={classes.infoItem}>
                    <Text className={classes.infoLabel}>Platform</Text>
                    <Text className={classes.infoValue}>{exceptionDetails.platform}</Text>
                  </Box>
                )}
                {exceptionDetails.osVersion && (
                  <Box className={classes.infoItem}>
                    <Text className={classes.infoLabel}>OS Version</Text>
                    <Text className={classes.infoValue}>{exceptionDetails.osVersion}</Text>
                  </Box>
                )}
                {exceptionDetails.deviceModel && (
                  <Box className={classes.infoItem}>
                    <Text className={classes.infoLabel}>Device</Text>
                    <Text className={classes.infoValue}>{exceptionDetails.deviceModel}</Text>
                  </Box>
                )}
                {exceptionDetails.appVersion && (
                  <Box className={classes.infoItem}>
                    <Text className={classes.infoLabel}>App Version</Text>
                    <Text className={classes.infoValue}>{exceptionDetails.appVersion}</Text>
                  </Box>
                )}
                {exceptionDetails.sdkVersion && (
                  <Box className={classes.infoItem}>
                    <Text className={classes.infoLabel}>SDK Version</Text>
                    <Text className={classes.infoValue}>{exceptionDetails.sdkVersion}</Text>
                  </Box>
                )}
                {exceptionDetails.userId && (
                  <Box className={classes.infoItem}>
                    <Text className={classes.infoLabel}>User ID</Text>
                    <Text className={classes.infoValue}>{exceptionDetails.userId}</Text>
                  </Box>
                )}
              </Box>
            </Box>
          </Box>
        )}

      {/* Stack Trace */}
      {stackTrace && (
        <Box className={classes.exceptionSection}>
          <Text className={classes.exceptionSectionLabel}>Stack Trace</Text>
          <Box className={classes.stackTrace}>{stackTrace}</Box>
        </Box>
      )}

      {/* Grouping Info */}
      {((exceptionDetails?.groupId || metadataGroupId) ||
        exceptionDetails?.fingerprint) && (
        <Box className={classes.exceptionSection}>
          <Text className={classes.exceptionSectionLabel}>Grouping</Text>
          <Box className={classes.infoCard}>
            {(exceptionDetails?.groupId || metadataGroupId) && (
              <Box className={classes.infoItem} mb="xs">
                <Text className={classes.infoLabel}>Group ID</Text>
                <Text className={classes.monoValue}>
                  {exceptionDetails?.groupId || metadataGroupId}
                </Text>
              </Box>
            )}
            {exceptionDetails?.fingerprint && (
              <Box className={classes.infoItem}>
                <Text className={classes.infoLabel}>Fingerprint</Text>
                <Text className={classes.monoValue}>{exceptionDetails.fingerprint}</Text>
              </Box>
            )}
          </Box>
        </Box>
      )}
    </Box>
  );
}
