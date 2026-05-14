import { useEffect, useState } from "react";
import { Button, Group, Modal, Stack, TextInput, Textarea } from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { useCreateAdminTenant } from "../../../../hooks";
import type { TenantResponse } from "../../../../hooks/useCreateTenant";

interface CreateTenantModalProps {
  opened: boolean;
  onClose: () => void;
  onEnterWorkspace: (tenant: TenantResponse) => void;
}

export function CreateTenantModal({ opened, onClose, onEnterWorkspace }: CreateTenantModalProps) {
  const [tenantName, setTenantName] = useState("");
  const [projectName, setProjectName] = useState("");
  const [description, setDescription] = useState("");

  const createMutation = useCreateAdminTenant();

  useEffect(() => {
    if (!opened) {
      setTenantName("");
      setProjectName("");
      setDescription("");
    }
  }, [opened]);

  const handleSubmit = () => {
    const trimmedTenantName = tenantName.trim();
    const trimmedProjectName = projectName.trim();

    if (!trimmedTenantName) {
      notifications.show({
        title: "Tenant name required",
        message: "Please provide a tenant name.",
        color: "red",
      });
      return;
    }
    if (!trimmedProjectName) {
      notifications.show({
        title: "Project name required",
        message: "Please provide a project name.",
        color: "red",
      });
      return;
    }

    createMutation.mutate(
      {
        tenantName: trimmedTenantName,
        projectName: trimmedProjectName,
        description: description.trim() || undefined,
      },
      {
        onSuccess: (response) => {
          if (response.error || !response.data) {
            notifications.show({
              title: "Could not create workspace",
              message: response.error?.message || "Workspace creation failed. Please try again.",
              color: "red",
            });
            return;
          }
          onEnterWorkspace({
            tenantId: response.data.tenantId,
            name: trimmedTenantName,
          });
        },
        onError: (error) => {
          notifications.show({
            title: "Could not create workspace",
            message: error instanceof Error ? error.message : "Workspace creation failed. Please try again.",
            color: "red",
          });
        },
      },
    );
  };

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title="Create Tenant"
      centered
      size="lg"
      closeOnClickOutside={!createMutation.isPending}
      closeOnEscape={!createMutation.isPending}
      withCloseButton={!createMutation.isPending}
    >
      <Stack gap="sm">
        <TextInput
          label="Tenant Name"
          placeholder="Enter tenant name"
          value={tenantName}
          onChange={(event) => setTenantName(event.currentTarget.value)}
          required
        />
        <TextInput
          label="Project Name"
          placeholder="Pulse iOS App"
          value={projectName}
          onChange={(event) => setProjectName(event.currentTarget.value)}
          required
        />
        <Textarea
          label="Description (Optional)"
          placeholder="Add a short description for this tenant"
          value={description}
          onChange={(event) => setDescription(event.currentTarget.value)}
          minRows={3}
        />
        <Group justify="flex-end" mt="sm">
          <Button variant="default" onClick={onClose} disabled={createMutation.isPending}>
            Cancel
          </Button>
          <Button color="teal" onClick={handleSubmit} loading={createMutation.isPending}>
            Create Workspace
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
