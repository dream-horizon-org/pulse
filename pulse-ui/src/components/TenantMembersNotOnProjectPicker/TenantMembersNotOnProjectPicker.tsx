import { useMemo, useState, useEffect } from "react";
import { Button, Group, Loader, Select, Stack, Text } from "@mantine/core";
import { useTenantMembers } from "../../hooks";

export interface TenantMembersNotOnProjectPickerProps {
  /** Current tenant; picker hidden if null */
  tenantId: string | null;
  /** Project member user IDs — tenant users in this set are excluded */
  projectMemberUserIds: Set<string>;
  /** Emails already in the invite list (case-insensitive match for "Added" state) */
  inviteEmails: string[];
  /** Append one email to the invite list (parent should dedupe) */
  onAddEmail: (email: string) => void;
}

/**
 * Dropdown of organization members who are not on the project yet.
 * Searchable select + Add puts their email into the same invite list as manual entry.
 */
export function TenantMembersNotOnProjectPicker({
  tenantId,
  projectMemberUserIds,
  inviteEmails,
  onAddEmail,
}: TenantMembersNotOnProjectPickerProps) {
  const [selectedEmail, setSelectedEmail] = useState<string | null>(null);
  const { data, isLoading, isError } = useTenantMembers(tenantId ?? "");

  const eligible = useMemo(() => {
    const members = data?.data?.members ?? [];
    return members.filter(
      (m) => m.userId && m.email && !projectMemberUserIds.has(m.userId),
    );
  }, [data?.data?.members, projectMemberUserIds]);

  const inviteLower = useMemo(
    () => new Set(inviteEmails.map((e) => e.toLowerCase().trim())),
    [inviteEmails],
  );

  /** Members not yet added to the pending invite list */
  const selectableMembers = useMemo(
    () =>
      eligible.filter((m) => !inviteLower.has(m.email.toLowerCase().trim())),
    [eligible, inviteLower],
  );

  const selectData = useMemo(
    () =>
      selectableMembers.map((m) => ({
        value: m.email.trim(),
        label: m.name?.trim() ? `${m.name} — ${m.email}` : m.email.trim(),
      })),
    [selectableMembers],
  );

  const validValues = useMemo(
    () => new Set(selectData.map((d) => d.value)),
    [selectData],
  );

  useEffect(() => {
    if (selectedEmail && !validValues.has(selectedEmail)) {
      setSelectedEmail(null);
    }
  }, [selectedEmail, validValues]);

  if (!tenantId) {
    return null;
  }

  if (isLoading) {
    return (
      <Group gap="xs">
        <Loader size="sm" />
        <Text size="sm" c="dimmed">
          Loading organization members…
        </Text>
      </Group>
    );
  }

  if (isError) {
    return (
      <Text size="sm" c="dimmed">
        Could not load organization members. You can still invite by email
        below.
      </Text>
    );
  }

  if (eligible.length === 0) {
    return (
      <Text size="sm" c="dimmed">
        Everyone in your organization is already on this project, or invite
        others by email below.
      </Text>
    );
  }

  if (selectableMembers.length === 0) {
    return (
      <Stack gap="xs">
        <div>
          <Text size="sm" fw={600}>
            Add from organization
          </Text>
          <Text size="xs" c="dimmed">
            Everyone eligible is already in your invite list — add more by email
            below.
          </Text>
        </div>
      </Stack>
    );
  }

  const handleAdd = () => {
    if (!selectedEmail?.trim()) {
      return;
    }
    onAddEmail(selectedEmail.trim());
    setSelectedEmail(null);
  };

  return (
    <Stack gap="xs">
      <div>
        <Text size="sm" fw={600}>
          Add from organization
        </Text>
        <Text size="xs" c="dimmed">
          Search and pick someone not on this project yet
        </Text>
      </div>
      <Group align="flex-end" wrap="nowrap" gap="sm">
        <Select
          style={{ flex: 1, minWidth: 0 }}
          placeholder="Search by name or email…"
          searchable
          clearable
          nothingFoundMessage="No matching members"
          data={selectData}
          value={selectedEmail}
          onChange={setSelectedEmail}
          scrollAreaProps={{ type: "auto", mah: 280, offsetScrollbars: true }}
          comboboxProps={{ withinPortal: true, shadow: "md" }}
        />
        <Button
          size="sm"
          variant="light"
          color="teal"
          disabled={!selectedEmail}
          onClick={handleAdd}
        >
          Add
        </Button>
      </Group>
    </Stack>
  );
}
