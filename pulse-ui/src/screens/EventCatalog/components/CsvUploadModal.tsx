import { useState } from "react";
import {
  Modal,
  Button,
  Group,
  Text,
  Box,
  Code,
  Badge,
  Stack,
  FileInput,
  Alert,
  ScrollArea,
} from "@mantine/core";
import {
  IconUpload,
  IconFileTypeCsv,
  IconDownload,
  IconAlertCircle,
  IconCheck,
  IconExclamationCircle,
} from "@tabler/icons-react";
import { useBulkUploadEventDefinitions } from "../hooks/useBulkUploadEventDefinitions";
import classes from "./CsvUploadModal.module.css";

type CsvUploadModalProps = {
  opened: boolean;
  onClose: () => void;
};

const CSV_TEMPLATE = `event_name,event_description,category,attribute_name,attribute_description,attribute_type,attribute_required
cart_viewed,User viewed shopping cart,commerce,itemCount,Number of items in cart,int,true
cart_viewed,User viewed shopping cart,commerce,cartValue,Total value in cents,double,false
checkout_started,User initiated checkout,commerce,paymentMethod,Selected payment method,string,true
login_success,User logged in successfully,auth,,,,`;

export function CsvUploadModal({ opened, onClose }: CsvUploadModalProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const uploadMutation = useBulkUploadEventDefinitions();

  const handleUpload = () => {
    if (!selectedFile) return;
    uploadMutation.mutate(selectedFile);
  };

  const handleClose = () => {
    setSelectedFile(null);
    uploadMutation.reset();
    onClose();
  };

  const result = uploadMutation.data?.data;
  const apiError = uploadMutation.data?.error;

  const handleDownloadTemplate = () => {
    const blob = new Blob([CSV_TEMPLATE], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "event_definitions_template.csv";
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <Modal
      opened={opened}
      onClose={handleClose}
      title="Bulk Upload Events"
      size="lg"
      centered
      styles={{
        title: {
          fontWeight: 700,
          fontSize: 18,
        },
        header: {
          borderBottom: "2px solid #0ec9c2",
          paddingBottom: 12,
        },
      }}
    >
      <Stack gap="md" mt="md">
        {!result && (
          <>
            <Box className={classes.uploadSection}>
              <Stack gap="sm">
                <FileInput
                  label="Select CSV file"
                  placeholder="Click to browse..."
                  accept=".csv,text/csv"
                  leftSection={<IconFileTypeCsv size={18} color="#0ba09a" />}
                  value={selectedFile}
                  onChange={setSelectedFile}
                  size="sm"
                />

                <Button
                  variant="subtle"
                  size="xs"
                  leftSection={<IconDownload size={14} />}
                  className={classes.templateButton}
                  onClick={handleDownloadTemplate}
                >
                  Download CSV template
                </Button>
              </Stack>
            </Box>

            <Box className={classes.formatHint}>
              <Text className={classes.formatHintLabel}>Expected Format</Text>
              <Code
                block
                style={{
                  fontSize: 11,
                  background: "rgba(14, 201, 194, 0.04)",
                  border: "1px solid rgba(14, 201, 194, 0.1)",
                }}
              >
                {`event_name,event_description,category,attribute_name,attribute_description,attribute_type,attribute_required`}
              </Code>
              <Text size="xs" mt="xs" c="dark.3">
                Rows with the same event_name are merged. Leave attribute fields
                empty for events with no attributes.
              </Text>
            </Box>
          </>
        )}

        {result && (
          <Stack gap="md">
            {(result.created > 0 || result.updated > 0) && (
              <Box className={classes.successSummary}>
                <IconCheck size={18} className={classes.successIcon} />
                <Text size="sm" fw={500}>
                  {[
                    result.created > 0 &&
                      `${result.created} event${result.created !== 1 ? "s" : ""} created`,
                    result.updated > 0 &&
                      `${result.updated} event${result.updated !== 1 ? "s" : ""} updated`,
                  ]
                    .filter(Boolean)
                    .join(", ")}
                </Text>
              </Box>
            )}

            {result.errors?.length > 0 && (
              <Box className={classes.errorsContainer}>
                <Group gap="xs" className={classes.errorsHeader}>
                  <IconExclamationCircle size={16} color="#dc2626" />
                  <Text size="sm" fw={600} c="red.8">
                    {result.errors.length} event
                    {result.errors.length !== 1 ? "s" : ""} failed
                  </Text>
                </Group>

                <ScrollArea.Autosize mah={280}>
                  <Stack gap={0}>
                    {result.errors.map((err, i) => (
                      <Box
                        key={i}
                        className={classes.errorItem}
                      >
                        <Group gap="xs" wrap="nowrap" align="flex-start">
                          <Badge
                            size="xs"
                            variant="light"
                            color="red"
                            className={classes.errorEventBadge}
                          >
                            {err.eventName || "unknown"}
                          </Badge>
                          {err.line > 0 && (
                            <Text size="xs" c="dimmed" className={classes.errorLine}>
                              line {err.line}
                            </Text>
                          )}
                        </Group>
                        <Text size="xs" c="dark.4" mt={4}>
                          {err.message}
                        </Text>
                      </Box>
                    ))}
                  </Stack>
                </ScrollArea.Autosize>
              </Box>
            )}

            {result.errors?.length === 0 &&
              result.created === 0 &&
              result.updated === 0 && (
                <Text size="sm" c="dimmed" ta="center" py="md">
                  No changes were made. The CSV may be empty or contain only
                  headers.
                </Text>
              )}
          </Stack>
        )}

        {apiError && (
          <Alert
            color="red"
            title="Upload Failed"
            icon={<IconAlertCircle size={16} />}
          >
            {apiError.message || "An error occurred during upload"}
          </Alert>
        )}

        <Group justify="flex-end" className={classes.footerSection}>
          <Button
            variant="outline"
            size="sm"
            className={classes.cancelButton}
            onClick={handleClose}
          >
            {result ? "Close" : "Cancel"}
          </Button>
          {!result && (
            <Button
              size="sm"
              className={classes.uploadSubmitButton}
              onClick={handleUpload}
              loading={uploadMutation.isPending}
              disabled={!selectedFile}
              leftSection={<IconUpload size={16} />}
            >
              Upload
            </Button>
          )}
        </Group>
      </Stack>
    </Modal>
  );
}
