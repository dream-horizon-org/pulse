import { Box, Card, Group, Progress, Stack, Text, Badge } from "@mantine/core";
import { VitalCardProps } from "./VitalCard.interface";
import {
  formatVitalP75Display,
  getVitalRating,
} from "../../WebVitals.constants";
import { ratingToColorName } from "../utils/ratingToColor";
import { formatPercent } from "../../../../utils";
import classes from "./VitalCard.module.css";

export function VitalCard({
  name,
  p75,
  goodPct,
  needsImprovementPct,
  poorPct,
  isSelected = false,
  onSelect,
}: VitalCardProps) {
  const rating = getVitalRating(p75, name);
  const colorName = ratingToColorName(rating);

  return (
    <Card
      withBorder
      radius="md"
      shadow="xs"
      className={`${classes.card} ${isSelected ? classes.selected : ""}`}
      onClick={onSelect}
      style={{ cursor: onSelect ? "pointer" : "default" }}
    >
      <Stack gap="md">
        <Group justify="space-between" align="center">
          <Text fw={600} size="lg">
            {name}
          </Text>
          <Badge color={colorName} variant="light" size="sm">
            {rating === "good"
              ? "Good"
              : rating === "needsImprovement"
                ? "Needs Improvement"
                : "Poor"}
          </Badge>
        </Group>

        <Box>
          <Text size="sm" c="dimmed" mb="xs">
            P75: {formatVitalP75Display(p75, name)}
          </Text>
          <Progress.Root size="md" radius="md">
            {goodPct > 0 && (
              <Progress.Section value={goodPct} color="green" key="good" />
            )}
            {needsImprovementPct > 0 && (
              <Progress.Section
                value={needsImprovementPct}
                color="yellow"
                key="ni"
              />
            )}
            {poorPct > 0 && (
              <Progress.Section value={poorPct} color="red" key="poor" />
            )}
          </Progress.Root>
          <Group justify="space-between" mt="xs" gap="xs">
            <Text size="xs" c="green">
              Good: {formatPercent(goodPct)}
            </Text>
            <Text size="xs" c="yellow">
              NI: {formatPercent(needsImprovementPct)}
            </Text>
            <Text size="xs" c="red">
              Poor: {formatPercent(poorPct)}
            </Text>
          </Group>
        </Box>
      </Stack>
    </Card>
  );
}
