import { Box, Badge, Card, Group, Stack, Text } from "@mantine/core";
import { IconChartFunnel, IconRoute, IconExternalLink } from "@tabler/icons-react";
import { useNavigate } from "react-router-dom";
import type { FunnelJourneyCardProps } from "./FunnelJourneyCard.interface";
import classes from "./FunnelJourneyCard.module.css";

/**
 * Funnel/Journey card for the Root Cause "Linked Funnels & Journeys" section.
 * Displays id, name, type, status, created by, tags, description, and a View Details action.
 * The entire card is clickable when detailUrl or onCardClick is provided.
 */
export const FunnelJourneyCard = ({
  id,
  name,
  type,
  status,
  createdBy,
  createdAt,
  tags,
  description,
  detailUrl,
  onCardClick,
}: FunnelJourneyCardProps) => {
  const navigate = useNavigate();
  const canViewDetails = !!detailUrl || !!onCardClick;
  const cardClassName = canViewDetails
    ? `${classes.card} ${classes.cardClickable}`
    : classes.card;

  const handleCardClick = () => {
    if (detailUrl) {
      navigate(detailUrl);
    } else if (onCardClick) {
      onCardClick();
    }
  };

  const TypeIcon = type === "FUNNEL" ? IconChartFunnel : IconRoute;

  const getStatusColor = (status: string) => {
    switch (status) {
      case "ACTIVE":
        return "teal";
      case "CREATING":
        return "blue";
      case "UPDATING":
        return "orange";
      case "STOPPED":
        return "gray";
      default:
        return "gray";
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case "ACTIVE":
        return "Active";
      case "CREATING":
        return "Creating";
      case "UPDATING":
        return "Updating";
      case "STOPPED":
        return "Stopped";
      default:
        return status;
    }
  };

  const content = (
    <Stack gap="sm">
      <div className={classes.headerRow}>
        <Group gap="xs" wrap="nowrap">
          <TypeIcon
            size={16}
            color="var(--mantine-color-teal-7)"
            aria-hidden
          />
          <Text className={classes.name} component="span" title={name}>
            {name}
          </Text>
          <Badge
            size="sm"
            variant="light"
            color={getStatusColor(status)}
            className={classes.statusBadge}
          >
            {getStatusText(status)}
          </Badge>
        </Group>
        <Text className={classes.createdAt} component="span">
          {createdAt}
        </Text>
      </div>

      <Group gap="xs" align="center">
        <Text className={classes.typeText}>
          {type === "FUNNEL" ? "Funnel" : "Journey"}
        </Text>
        <Text className={classes.separator}>•</Text>
        <Text className={classes.createdByText}>
          by {createdBy}
        </Text>
      </Group>

      {tags.length > 0 && (
        <Group gap="xs">
          {tags.slice(0, 3).map((tag, index) => (
            <Badge
              key={index}
              size="xs"
              variant="outline"
              color="gray"
              className={classes.tagBadge}
            >
              {tag}
            </Badge>
          ))}
          {tags.length > 3 && (
            <Badge
              size="xs"
              variant="outline"
              color="gray"
              className={classes.tagBadge}
            >
              +{tags.length - 3}
            </Badge>
          )}
        </Group>
      )}

      {description && description.trim() !== "" && (
        <Box className={classes.descriptionBox}>{description}</Box>
      )}

      {canViewDetails && (
        <span className={classes.viewDetailsLink}>
          <Group gap="xs" wrap="nowrap" align="center">
            <IconExternalLink size={14} aria-hidden />
            <span>View Details</span>
          </Group>
        </span>
      )}
    </Stack>
  );

  if (canViewDetails) {
    return (
      <Card
        component="button"
        type="button"
        onClick={handleCardClick}
        withBorder
        padding="md"
        className={cardClassName}
        radius="md"
        aria-label={`View ${type.toLowerCase()} ${name}`}
      >
        {content}
      </Card>
    );
  }

  return (
    <Card withBorder padding="md" className={cardClassName} radius="md">
      {content}
    </Card>
  );
};