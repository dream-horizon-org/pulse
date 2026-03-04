import { Box, Text } from "@mantine/core";
import { IconSparkles } from "@tabler/icons-react";
import classes from "./AiChat.module.css";

/**
 * Animated AI loading indicator — sparkles, shimmer, and bouncing dots
 */
export function AiLoadingAnimation() {
  return (
    <Box className={classes.aiLoading}>
      <Box className={classes.aiLoadingOrbs}>
        <span className={classes.aiOrb} />
        <span className={classes.aiOrb} />
        <span className={classes.aiOrb} />
      </Box>
      <Box className={classes.aiLoadingShimmer} />
      <Box className={classes.aiLoadingContent}>
        <IconSparkles size={14} className={classes.aiLoadingIcon} />
        <Text size="xs" c="dimmed" component="span">
          Analyzing your data
        </Text>
        <Box className={classes.aiLoadingDots}>
          <span />
          <span />
          <span />
        </Box>
      </Box>
    </Box>
  );
}
