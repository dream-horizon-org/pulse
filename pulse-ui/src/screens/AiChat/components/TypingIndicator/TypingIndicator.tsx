import { Box, Text } from "@mantine/core";
import { IconSparkles } from "@tabler/icons-react";
import classes from "./TypingIndicator.module.css";

export const TypingIndicator = () => (
  <Box className={classes.container}>
    <Box className={classes.shimmer} />
    <Box className={classes.content}>
      <IconSparkles size={16} className={classes.icon} />
      <Text size="xs" c="dimmed" className={classes.label}>
        Thinking
      </Text>
      <Box className={classes.dots}>
        <Box className={classes.dot} />
        <Box className={classes.dot} />
        <Box className={classes.dot} />
      </Box>
    </Box>
  </Box>
);
