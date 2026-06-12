import { Box, Text, Badge, Group } from "@mantine/core";
import classes from "./ScreenBreakdownList.module.css";

interface ScreenBreakdownItem {
  screen: string;
  occurrences: number;
  percentage: number;
}

interface ScreenBreakdownListProps {
  screenBreakdown: ScreenBreakdownItem[];
}

export const ScreenBreakdownList: React.FC<ScreenBreakdownListProps> = ({
  screenBreakdown,
}) => {
  return (
    <Box className={classes.container}>
      {screenBreakdown.map((item, index) => (
        <Box key={item.screen} className={classes.listItem}>
          <Box className={classes.itemHeader}>
            <Text className={classes.screenName}>{item.screen}</Text>
            <Group gap="md">
              <Badge size="md" variant="light" color="teal">
                {item.occurrences} occurrences
              </Badge>
              <Text className={classes.percentage}>{item.percentage}%</Text>
            </Group>
          </Box>
          <Box className={classes.progressBarContainer}>
            <Box
              className={classes.progressBar}
              style={{ width: `${item.percentage}%` }}
            />
          </Box>
        </Box>
      ))}
    </Box>
  );
};
