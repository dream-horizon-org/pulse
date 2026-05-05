import { useEffect, useMemo, useState } from "react";
import { Button, Group, Modal, Stack, Text, TextInput, Textarea } from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { useCreateProject, useCreateTenant } from "../../../../hooks";
import type { TenantResponse } from "../../../../hooks/useCreateTenant";

interface CreateTenantModalProps {
  opened: boolean;
  onClose: () => void;
  onEnterWorkspace: (tenant: TenantResponse) => void;
}

type Step = "tenant" | "project" | "done";

export function CreateTenantModal({ opened, onClose, onEnterWorkspace }: CreateTenantModalProps) {
  const [step, setStep] = useState<Step>("tenant");
  const [tenantName, setTenantName] = useState("");
  const [tenantDescription, setTenantDescription] = useState("");
  const [projectName, setProjectName] = useState("");
  const [projectDescription, setProjectDescription] = useState("");
  const [createdTenant, setCreatedTenant] = useState<TenantResponse | null>(null);

  const createTenantMutation = useCreateTenant();
  const createProjectMutation = useCreateProject();
  const isSubmitting = createTenantMutation.isPending || createProjectMutation.isPending;

  const modalTitle = useMemo(() => {
    if (step === "tenant") {
      return "Create Tenant";
    }
    if (step === "project") {
      return "Create First Project";
    }
    return "Tenant Ready";
  }, [step]);

  useEffect(() => {
    if (!opened) {
      setStep("tenant");
      setTenantName("");
      setTenantDescription("");
      setProjectName("");
      setProjectDescription("");
      setCreatedTenant(null);
    }
  }, [opened]);

  const handleCreateTenant = () => {
    const trimmedName = tenantName.trim();
    if (!trimmedName) {
      notifications.show({
        title: "Tenant name required",
        message: "Please provide a tenant name.",
        color: "red",
      });
      return;
    }

    createTenantMutation.mutate(
      {
        name: trimmedName,
        description: tenantDescription.trim() || undefined,
      },
      {
        onSuccess: (response) => {
          if (response.error || !response.data) {
            notifications.show({
              title: "Could not create tenant",
              message: response.error?.message || "Tenant creation failed. Please try again.",
              color: "red",
            });
            return;
          }
          setCreatedTenant(response.data);
          setStep("project");
        },
        onError: (error) => {
          notifications.show({
            title: "Could not create tenant",
            message: error instanceof Error ? error.message : "Tenant creation failed. Please try again.",
            color: "red",
          });
        },
      },
    );
  };

  const handleCreateProject = () => {
    if (!createdTenant) {
      return;
    }
    const trimmedProjectName = projectName.trim();
    if (!trimmedProjectName) {
      notifications.show({
        title: "Project name required",
        message: "Please provide a project name or skip this step.",
        color: "red",
      });
      return;
    }

    createProjectMutation.mutate(
      {
        name: trimmedProjectName,
        description: projectDescription.trim() || undefined,
        tenantId: createdTenant.tenantId,
      },
      {
        onSuccess: (response) => {
          if (response.error || !response.data) {
            notifications.show({
              title: "Could not create project",
              message: response.error?.message || "Project creation failed. You can create it later.",
              color: "red",
            });
            return;
          }
          setStep("done");
        },
        onError: (error) => {
          notifications.show({
            title: "Could not create project",
            message: error instanceof Error ? error.message : "Project creation failed. You can create it later.",
            color: "red",
          });
        },
      },
    );
  };

  const handleEnterWorkspace = () => {
    if (!createdTenant) {
      return;
    }
    onEnterWorkspace(createdTenant);
  };

  const renderTenantStep = () => (
    <Stack gap="sm">
      <TextInput
        label="Tenant Name"
        placeholder="Acme Mobile Team"
        value={tenantName}
        onChange={(event) => setTenantName(event.currentTarget.value)}
        required
      />
      <Textarea
        label="Description (Optional)"
        placeholder="Used for internal testing and onboarding"
        value={tenantDescription}
        onChange={(event) => setTenantDescription(event.currentTarget.value)}
        minRows={3}
      />
      <Group justify="flex-end" mt="sm">
        <Button variant="default" onClick={onClose} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button color="teal" onClick={handleCreateTenant} loading={createTenantMutation.isPending}>
          Create Tenant
        </Button>
      </Group>
    </Stack>
  );

  const renderProjectStep = () => (
    <Stack gap="sm">
      <Text size="sm" c="dimmed">
        Tenant &quot;{createdTenant?.name}&quot; created successfully. Add a project now or skip and create it later.
      </Text>
      <TextInput
        label="Project Name"
        placeholder="Pulse iOS App"
        value={projectName}
        onChange={(event) => setProjectName(event.currentTarget.value)}
      />
      <Textarea
        label="Description (Optional)"
        placeholder="Primary observability project"
        value={projectDescription}
        onChange={(event) => setProjectDescription(event.currentTarget.value)}
        minRows={3}
      />
      <Group justify="space-between" mt="sm">
        <Button variant="default" onClick={() => setStep("done")} disabled={isSubmitting}>
          Skip
        </Button>
        <Button color="teal" onClick={handleCreateProject} loading={createProjectMutation.isPending}>
          Create Project
        </Button>
      </Group>
    </Stack>
  );

  const renderDoneStep = () => (
    <Stack gap="sm">
      <Text size="sm">
        Workspace is ready for tenant &quot;{createdTenant?.name}&quot;. Continue to the tenant workspace.
      </Text>
      <Group justify="flex-end" mt="sm">
        <Button variant="default" onClick={onClose} disabled={isSubmitting}>
          Close
        </Button>
        <Button color="teal" onClick={handleEnterWorkspace}>
          Enter Workspace
        </Button>
      </Group>
    </Stack>
  );

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={modalTitle}
      centered
      size="lg"
      closeOnClickOutside={!isSubmitting}
      closeOnEscape={!isSubmitting}
      withCloseButton={!isSubmitting}
    >
      {step === "tenant" && renderTenantStep()}
      {step === "project" && renderProjectStep()}
      {step === "done" && renderDoneStep()}
    </Modal>
  );
}
