import { Box, Text, Paper, Group } from "@mantine/core";
import { IconUser } from "@tabler/icons-react";
import classes from "./UserInformation.module.css";

interface User {
  userId: string;
  email: string;
  name: string;
  accountCreated: Date;
  sessionId: string;
  locale: string;
  timezone: string;
  appVersion: string;
  buildNumber: string;
}

interface UserInformationProps {
  user: User;
}

export const UserInformation: React.FC<UserInformationProps> = ({ user }) => {
  return (
    <Paper className={classes.userCard}>
      <Box className={classes.topAccent} />

      {/* Header */}
      <Group gap="sm" mb="md">
        <IconUser size={20} color="#0ba09a" />
        <Text className={classes.cardTitle}>User Information</Text>
      </Group>

      {/* 4x2 Grid - 8 Fields */}
      <Box className={classes.infoGrid}>
        <Box className={classes.infoItem}>
          <Text size="11px" fw={500} className={classes.infoLabel}>
            User ID
          </Text>
          <Text size="14px" fw={600} className={classes.infoValue}>
            {user.userId}
          </Text>
        </Box>

        <Box className={classes.infoItem}>
          <Text size="11px" fw={500} className={classes.infoLabel}>
            Name
          </Text>
          <Text size="14px" fw={600} className={classes.infoValue}>
            {user.name}
          </Text>
        </Box>

        <Box className={classes.infoItem}>
          <Text size="11px" fw={500} className={classes.infoLabel}>
            Email
          </Text>
          <Text size="14px" fw={600} className={classes.infoValue}>
            {user.email}
          </Text>
        </Box>

        <Box className={classes.infoItem}>
          <Text size="11px" fw={500} className={classes.infoLabel}>
            Session ID
          </Text>
          <Text size="14px" fw={600} className={classes.infoValue}>
            {user.sessionId}
          </Text>
        </Box>

        <Box className={classes.infoItem}>
          <Text size="11px" fw={500} className={classes.infoLabel}>
            Locale
          </Text>
          <Text size="14px" fw={600} className={classes.infoValue}>
            {user.locale}
          </Text>
        </Box>

        <Box className={classes.infoItem}>
          <Text size="11px" fw={500} className={classes.infoLabel}>
            Timezone
          </Text>
          <Text size="14px" fw={600} className={classes.infoValue}>
            {user.timezone}
          </Text>
        </Box>

        <Box className={classes.infoItem}>
          <Text size="11px" fw={500} className={classes.infoLabel}>
            App Version
          </Text>
          <Text size="14px" fw={600} className={classes.infoValue}>
            {user.appVersion}
          </Text>
        </Box>

        <Box className={classes.infoItem}>
          <Text size="11px" fw={500} className={classes.infoLabel}>
            Build Number
          </Text>
          <Text size="14px" fw={600} className={classes.infoValue}>
            {user.buildNumber}
          </Text>
        </Box>
      </Box>
    </Paper>
  );
};
