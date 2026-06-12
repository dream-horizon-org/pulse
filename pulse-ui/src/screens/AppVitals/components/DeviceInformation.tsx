import { Box, Text, Paper, Group } from "@mantine/core";
import { IconDeviceMobile } from "@tabler/icons-react";
import classes from "./DeviceInformation.module.css";

interface Device {
  manufacturer: string;
  model: string;
  osVersion: string;
  screenResolution: string;
  ramTotal: string;
  ramAvailable: string;
  storageTotal: string;
  storageFree: string;
  batteryLevel: string;
  connectionType: string;
  carrier: string;
}

interface DeviceInformationProps {
  device: Device;
}

export const DeviceInformation: React.FC<DeviceInformationProps> = ({
  device,
}) => {
  return (
    <Paper className={classes.deviceCard}>
      <Box className={classes.topAccent} />

      {/* Header */}
      <Group gap="sm" mb="md">
        <IconDeviceMobile size={20} color="#0ba09a" />
        <Text className={classes.cardTitle}>Device Information</Text>
      </Group>

      {/* 4x2 Grid */}
      <Box className={classes.infoGrid}>
        <Box className={classes.infoItem}>
          <Text size="11px" fw={500} className={classes.infoLabel}>
            Device
          </Text>
          <Text size="14px" fw={600} className={classes.infoValue}>
            {device.manufacturer} {device.model}
          </Text>
        </Box>

        <Box className={classes.infoItem}>
          <Text size="11px" fw={500} className={classes.infoLabel}>
            OS Version
          </Text>
          <Text size="14px" fw={600} className={classes.infoValue}>
            {device.osVersion}
          </Text>
        </Box>

        <Box className={classes.infoItem}>
          <Text size="11px" fw={500} className={classes.infoLabel}>
            Screen Resolution
          </Text>
          <Text size="14px" fw={600} className={classes.infoValue}>
            {device.screenResolution}
          </Text>
        </Box>

        <Box className={classes.infoItem}>
          <Text size="11px" fw={500} className={classes.infoLabel}>
            Carrier
          </Text>
          <Text size="14px" fw={600} className={classes.infoValue}>
            {device.carrier}
          </Text>
        </Box>

        <Box className={classes.infoItem}>
          <Text size="11px" fw={500} className={classes.infoLabel}>
            RAM
          </Text>
          <Text size="14px" fw={600} className={classes.infoValue}>
            {device.ramAvailable} / {device.ramTotal}
          </Text>
        </Box>

        <Box className={classes.infoItem}>
          <Text size="11px" fw={500} className={classes.infoLabel}>
            Storage
          </Text>
          <Text size="14px" fw={600} className={classes.infoValue}>
            {device.storageFree} free
          </Text>
        </Box>

        <Box className={classes.infoItem}>
          <Text size="11px" fw={500} className={classes.infoLabel}>
            Battery
          </Text>
          <Text size="14px" fw={600} className={classes.infoValue}>
            {device.batteryLevel}
          </Text>
        </Box>

        <Box className={classes.infoItem}>
          <Text size="11px" fw={500} className={classes.infoLabel}>
            Connection
          </Text>
          <Text size="14px" fw={600} className={classes.infoValue}>
            {device.connectionType}
          </Text>
        </Box>
      </Box>
    </Paper>
  );
};
