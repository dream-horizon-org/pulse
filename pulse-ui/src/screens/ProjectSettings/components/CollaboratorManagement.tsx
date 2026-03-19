import { useState } from 'react';
import { Stack, Text, Table, Button, Group, Modal, TextInput, Select, Badge, Loader, ActionIcon, Box } from '@mantine/core';
import { IconUserPlus, IconTrash, IconCheck, IconX, IconEdit, IconUsers } from '@tabler/icons-react';
import { COOKIES_KEY } from '../../../constants';
import { usePermissions } from '../../../hooks';
import { useProjectContext } from '../../../contexts';
import { showNotification } from '../../../helpers/showNotification';
import { getCookies } from '../../../helpers/cookies';
import { ConfirmationModal } from '../../../components/ConfirmationModal';
import { 
  useProjectMembers, 
  useInviteProjectMember, 
  useRemoveProjectMember, 
  useUpdateProjectMemberRole 
} from '../../../hooks';
import { ApiResponse } from '../../../helpers/makeRequest';
import { ProjectMember } from '../../../types/members';
import classes from './CollaboratorManagement.module.css';
import { PROJECT_ROLES, PROJECT_ROLE_LABELS, ProjectRole } from '../../../constants/Roles';

export function CollaboratorManagement() {
  const { projectId } = useProjectContext();
  const { canInviteProjectMembers, canRemoveProjectMembers, projectRole } = usePermissions();
  
  // React Query hooks
  const { data, isLoading } = useProjectMembers(projectId ?? '');
  const inviteMutation = useInviteProjectMember();
  const removeMutation = useRemoveProjectMember();
  const updateRoleMutation = useUpdateProjectMemberRole();

  const collaborators = data?.data?.members ?? [];
  const loading = isLoading;
  const inviting = inviteMutation.isPending;
  
  const [inviteModalOpen, setInviteModalOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState<ProjectRole>(PROJECT_ROLES.VIEWER);
  const [removeConfirmUser, setRemoveConfirmUser] = useState<{ userId: string; userName: string } | null>(null);
  const [editingRoleUserId, setEditingRoleUserId] = useState<string | null>(null);
  const [newRole, setNewRole] = useState<ProjectRole | ''>('');
  
  // Get current user ID to prevent self-role changes
  const currentUserId = getCookies(COOKIES_KEY.USER_ID);

  const handleInvite = () => {
    if (!inviteEmail.trim() || !projectId) return;
    
    inviteMutation.mutate(
      {
        projectId,
        email: inviteEmail.trim(),
        role: inviteRole,
      },
      {
        onSuccess: (response: ApiResponse<ProjectMember>) => {
          if (response?.data && !response?.error) {
            showNotification('Success', `Invitation sent to ${inviteEmail}`, <IconUserPlus />, '#0ec9c2');
            setInviteModalOpen(false);
            setInviteEmail('');
            setInviteRole(PROJECT_ROLES.VIEWER);
          } else {
            showNotification('Error', response?.error?.message || 'Failed to invite member', <IconUserPlus />, '#fa5252');
          }
        },
        onError: (error: any) => {
          showNotification('Error', error.message || 'Failed to invite member', <IconUserPlus />, '#fa5252');
        },
      }
    );
  };

  const handleRemove = (userId: string, userName: string) => {
    setRemoveConfirmUser({ userId, userName });
  };

  const confirmRemove = () => {
    if (!removeConfirmUser || !projectId) return;
    
    const { userId, userName } = removeConfirmUser;
    
    removeMutation.mutate(
      {
        projectId,
        userId,
      },
      {
        onSuccess: (response: ApiResponse<void>) => {
          if ((response?.data !== undefined || !response?.error) && !response?.error) {
            showNotification('Success', `${userName} removed from project`, <IconTrash />, '#0ec9c2');
            setRemoveConfirmUser(null);
          } else {
            showNotification('Error', response?.error?.message || 'Failed to remove member', <IconTrash />, '#fa5252');
          }
        },
        onError: (error: any) => {
          showNotification('Error', error.message || 'Failed to remove member', <IconTrash />, '#fa5252');
          setRemoveConfirmUser(null);
        },
      }
    );
  };

  const handleRoleUpdate = (userId: string, currentRole: string) => {
    if (!projectId || !newRole || newRole === currentRole) {
      setEditingRoleUserId(null);
      return;
    }
    
    updateRoleMutation.mutate(
      {
        projectId,
        userId,
        newRole,
      },
      {
        onSuccess: (response: ApiResponse<ProjectMember>) => {
          if ((response?.data !== undefined || !response?.error) && !response?.error) {
            showNotification('Success', `Updated collaborator role to ${newRole}`, <IconUserPlus />, '#0ec9c2');
            setEditingRoleUserId(null);
            setNewRole('');
          } else {
            showNotification('Error', response?.error?.message || 'Failed to update role', <IconUserPlus />, '#fa5252');
          }
        },
        onError: (error: any) => {
          showNotification('Error', error.message || 'Failed to update role', <IconUserPlus />, '#fa5252');
        },
      }
    );
  };

  const getRoleBadgeColor = (role: string) => {
    switch (role.toLowerCase()) {
      case PROJECT_ROLES.ADMIN.toLowerCase():
        return 'blue';
      case PROJECT_ROLES.EDITOR.toLowerCase():
        return 'green';
      case PROJECT_ROLES.VIEWER.toLowerCase():
        return 'gray';
      default:
        return 'gray';
    }
  };

  if (loading) {
    return (
      <Box className={classes.pageContainer}>
        <Box className={classes.pageHeader}>
          <Box className={classes.titleSection}>
            <Text className={classes.pageTitle}>Manage project access members</Text>
          </Box>
        </Box>
        <Box className={classes.contentTable}>
          <Box className={classes.tableHeader}>
            <Box className={classes.tableHeaderContent}>
              <IconUsers size={18} color="#0ba09a" />
              <Text className={classes.tableHeaderTitle}>Members</Text>
            </Box>
          </Box>
          <Box className={classes.tableWrapper} style={{ textAlign: 'center' }}>
            <Loader size="lg" />
            <Text c="dimmed" mt="md">Loading team members...</Text>
          </Box>
        </Box>
      </Box>
    );
  }

  return (
    <Box className={classes.pageContainer}>
      {/* Page Header */}
      <Box className={classes.pageHeader}>
        <Box className={classes.headerGroup}>
          <Box className={classes.titleSection}>
            <Text className={classes.pageTitle}>Manage project access </Text>
          </Box>
          {canInviteProjectMembers && (
            <Button
              leftSection={<IconUserPlus size={16} />}
              onClick={() => setInviteModalOpen(true)}
              variant="filled"
              color="teal"
            >
              Invite Member
            </Button>
          )}
        </Box>
      </Box>

      {/* Members Table */}
      <Box className={classes.contentTable}>
        <Box className={classes.tableHeader}>
          <Box className={classes.tableHeaderContent}>
            <IconUsers size={18} color="#0ba09a" />
            <Text className={classes.tableHeaderTitle}>Project Members</Text>
            <Badge size="sm" variant="light" color="teal" ml="auto">
              {collaborators.length} member{collaborators.length !== 1 ? 's' : ''}
            </Badge>
          </Box>
        </Box>
        
        {collaborators.length > 0 ? (
          <Box className={classes.tableWrapper}>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Name</Table.Th>
                  <Table.Th>Email</Table.Th>
                  <Table.Th>Role</Table.Th>
                  <Table.Th>Actions</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
            {collaborators.map((collab) => {
              const isCurrentUser = collab.userId === currentUserId;
              return (
                <Table.Tr 
                  key={collab.userId}
                  style={{ backgroundColor: isCurrentUser ? '#f8f9fa' : undefined }}
                >
                  <Table.Td>
                    {collab.name}
                    {isCurrentUser && <Text span c="dimmed" size="xs" ml="xs">(You)</Text>}
                  </Table.Td>
                  <Table.Td>{collab.email}</Table.Td>
                  <Table.Td>
                    {editingRoleUserId === collab.userId && projectRole === PROJECT_ROLES.ADMIN ? (
                      <Group gap="xs">
                        <Select
                          size="xs"
                          value={newRole}
                          onChange={(val) => setNewRole((val as ProjectRole) || collab.role)}
                          data={[
                            { value: PROJECT_ROLES.ADMIN, label: PROJECT_ROLE_LABELS[PROJECT_ROLES.ADMIN] },
                            { value: PROJECT_ROLES.EDITOR, label: PROJECT_ROLE_LABELS[PROJECT_ROLES.EDITOR] },
                            { value: PROJECT_ROLES.VIEWER, label: PROJECT_ROLE_LABELS[PROJECT_ROLES.VIEWER] },
                          ]}
                          style={{ width: 120 }}
                          disabled={isCurrentUser || updateRoleMutation.isPending}
                        />
                        <ActionIcon 
                          size="sm" 
                          color="teal" 
                          onClick={() => handleRoleUpdate(collab.userId, collab.role)}
                          disabled={isCurrentUser || updateRoleMutation.isPending}
                          loading={updateRoleMutation.isPending}
                        >
                          <IconCheck size={14} />
                        </ActionIcon>
                        <ActionIcon 
                          size="sm" 
                          color="gray" 
                          onClick={() => setEditingRoleUserId(null)}
                          disabled={updateRoleMutation.isPending}
                        >
                          <IconX size={14} />
                        </ActionIcon>
                      </Group>
                    ) : (
                      <Group gap="xs">
                        <Badge 
                          color={getRoleBadgeColor(collab.role)} 
                          variant="light"
                          title={isCurrentUser ? "You cannot change your own role" : undefined}
                        >
                          {collab.role}
                        </Badge>
                        {projectRole === PROJECT_ROLES.ADMIN && !isCurrentUser && (
                          <ActionIcon
                            size="xs"
                            variant="subtle"
                            onClick={() => {
                              setEditingRoleUserId(collab.userId);
                              setNewRole(collab.role);
                            }}
                          >
                            <IconEdit size={12} />
                          </ActionIcon>
                        )}
                      </Group>
                    )}
                  </Table.Td>
                  <Table.Td>
                    {canRemoveProjectMembers && !isCurrentUser && (
                      <Button
                        size="xs"
                        variant="subtle"
                        color="red"
                        leftSection={<IconTrash size={14} />}
                        onClick={() => handleRemove(collab.userId, collab.name)}
                        loading={removeMutation.isPending && removeMutation.variables?.userId === collab.userId}
                        disabled={removeMutation.isPending}
                      >
                        Remove
                      </Button>
                    )}
                  </Table.Td>
                </Table.Tr>
              );
            })}
          </Table.Tbody>
        </Table>
          </Box>
        ) : (
          <Box className={classes.emptyState}>
            <Text c="dimmed" ta="center" py="xl">
              No collaborators yet. Invite team members to get started.
            </Text>
          </Box>
        )}
      </Box>

      <Modal
        opened={inviteModalOpen}
        onClose={() => setInviteModalOpen(false)}
        title="Invite Team Member"
      >
        <Stack gap="md">
          <TextInput
            label="Email"
            placeholder="user@example.com"
            value={inviteEmail}
            onChange={(e) => setInviteEmail(e.target.value)}
          />
          <Select
            label="Role"
            data={[
              { value: PROJECT_ROLES.ADMIN, label: PROJECT_ROLE_LABELS[PROJECT_ROLES.ADMIN] },
              { value: PROJECT_ROLES.EDITOR, label: PROJECT_ROLE_LABELS[PROJECT_ROLES.EDITOR] },
              { value: PROJECT_ROLES.VIEWER, label: PROJECT_ROLE_LABELS[PROJECT_ROLES.VIEWER] },
            ]}
            value={inviteRole}
            onChange={(val) => setInviteRole((val as ProjectRole) || PROJECT_ROLES.VIEWER)}
          />
          <Button 
            onClick={handleInvite} 
            disabled={!inviteEmail.trim() || inviting}
            loading={inviting}
          >
            Send Invite
          </Button>
        </Stack>
      </Modal>

      <ConfirmationModal
        opened={removeConfirmUser !== null}
        onClose={() => setRemoveConfirmUser(null)}
        onConfirm={confirmRemove}
        title="Remove Member?"
        message={`Are you sure you want to remove ${removeConfirmUser?.userName} from this project? They will lose access immediately.`}
        confirmLabel="Yes, Remove"
        cancelLabel="Cancel"
        confirmColor="red"
        loading={removeMutation.isPending}
        severity="warning"
      />
    </Box>
  );
}
