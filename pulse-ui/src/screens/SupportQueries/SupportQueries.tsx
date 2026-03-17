import { useState } from "react";
import {
  Badge,
  Box,
  Button,
  Group,
  Modal,
  Stack,
  Table,
  Text,
} from "@mantine/core";
import { IconPlus } from "@tabler/icons-react";
import { useDisclosure } from "@mantine/hooks";
import classes from "./SupportQueries.module.css";
import { useGetIncidents } from "../../hooks/useGetIncidents";
import { ContactUsModal } from "../../components/ContactUsModal/ContactUsModal";
import { LoaderWithMessage } from "../../components/LoaderWithMessage";
import { IncidentItem } from "./SupportQueries.interface";

const STATUS_COLOR: Record<string, string> = {
  OPEN: "blue",
  ACKNOWLEDGED: "yellow",
  RECOVERED: "teal",
  CLOSED: "gray",
};

function formatDate(dateStr: string | null): string {
  if (!dateStr) return "—";
  const d = new Date(dateStr);
  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function SupportQueries() {
  const { data: response, isLoading, refetch } = useGetIncidents(true);
  const [modalOpened, { open: openModal, close: closeModal }] =
    useDisclosure(false);
  const [selectedQuery, setSelectedQuery] = useState<IncidentItem | null>(null);

  const incidents: IncidentItem[] = response?.data ?? [];

  const handleModalClose = () => {
    closeModal();
    refetch();
  };

  const renderContent = () => {
    if (isLoading) {
      return (
        <Box className={classes.loader}>
          <LoaderWithMessage loadingMessage="Fetching your queries..." />
        </Box>
      );
    }

    if (incidents.length === 0) {
      return (
        <Box className={classes.emptyState}>
          <Box className={classes.emptyStateIcon}>📬</Box>
          <Text className={classes.emptyStateTitle}>No queries yet</Text>
          <Text className={classes.emptyStateDescription}>
            You haven&apos;t raised any queries. Click the button above to
            report an issue or ask a question.
          </Text>
          <Button
            leftSection={<IconPlus size={16} />}
            onClick={openModal}
            className={classes.raiseButton}
          >
            Raise New Query
          </Button>
        </Box>
      );
    }

    return (
      <Box className={classes.tableContainer}>
        <Table striped highlightOnHover withTableBorder>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>ID</Table.Th>
              <Table.Th>Title</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th>Reported By</Table.Th>
              <Table.Th>Created At</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {incidents.map((item) => (
              <Table.Tr
                key={item.id}
                className={classes.clickableRow}
                onClick={() => setSelectedQuery(item)}
              >
                <Table.Td>
                  <Text size="sm" fw={500}>
                    #{item.id}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="sm" fw={500} lineClamp={1}>
                    {item.title}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Badge
                    color={STATUS_COLOR[item.status] ?? "gray"}
                    variant="filled"
                    size="sm"
                  >
                    {item.status}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  <Text size="sm">{item.reporterName}</Text>
                  <Text size="xs" c="dimmed">
                    {item.reporterEmail}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="sm">{formatDate(item.createdAt)}</Text>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Box>
    );
  };

  return (
    <div className={classes.container}>
      <Box className={classes.pageHeader}>
        <Group className={classes.titleSection}>
          <h1 className={classes.pageTitle}>Support Queries</h1>
          <Badge size="lg" variant="light" color="teal" className={classes.totalBadge}>
            {incidents.length} Total
          </Badge>
        </Group>
        <Button
          leftSection={<IconPlus size={16} />}
          onClick={openModal}
          className={classes.raiseButton}
        >
          Raise New Query
        </Button>
      </Box>

      {renderContent()}

      <ContactUsModal opened={modalOpened} onClose={handleModalClose} />

      <Modal
        opened={selectedQuery !== null}
        onClose={() => setSelectedQuery(null)}
        title={
          <Group gap="sm">
            <Text fw={700} size="lg">Query #{selectedQuery?.id}</Text>
            {selectedQuery && (
              <Badge
                color={STATUS_COLOR[selectedQuery.status] ?? "gray"}
                variant="filled"
                size="sm"
              >
                {selectedQuery.status}
              </Badge>
            )}
          </Group>
        }
        centered
        size="md"
      >
        {selectedQuery && (
          <Stack gap="md">
            <Box>
              <Text size="sm" c="dimmed" fw={600} mb={4}>Title</Text>
              <Text size="sm">{selectedQuery.title}</Text>
            </Box>
            <Box>
              <Text size="sm" c="dimmed" fw={600} mb={4}>Description</Text>
              <Text size="sm" style={{ whiteSpace: "pre-wrap" }}>
                {selectedQuery.description}
              </Text>
            </Box>
            <Group gap="xl">
              <Box>
                <Text size="sm" c="dimmed" fw={600} mb={4}>Reported By</Text>
                <Text size="sm">{selectedQuery.reporterName}</Text>
                <Text size="xs" c="dimmed">{selectedQuery.reporterEmail}</Text>
              </Box>
              <Box>
                <Text size="sm" c="dimmed" fw={600} mb={4}>Created At</Text>
                <Text size="sm">{formatDate(selectedQuery.createdAt)}</Text>
              </Box>
            </Group>
          </Stack>
        )}
      </Modal>
    </div>
  );
}
