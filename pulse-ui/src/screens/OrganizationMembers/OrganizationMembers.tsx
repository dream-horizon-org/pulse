import { useState } from 'react';
import { Box, Table, Button, Group, Text, Modal, TextInput, Select, Badge, ActionIcon, Loader, Stack } from '@mantine/core';
import { IconUserPlus, IconTrash, IconCheck, IconX, IconEdit, IconUsers } from '@tabler/icons-react';
import { useTenantContext } from '../../contexts';
import { usePermissions } from '../../hooks';
import { showNotification } from '../../helpers/showNotification';
import { COOKIES_KEY } from '../../constants';
import { TENANT_ROLES, TENANT_ROLE_LABELS, TenantRole } from '../../constants/Roles';
import { getCookies } from '../../helpers/cookies';
import { ConfirmationModal } from '../../components/ConfirmationModal';
import { ApiResponse } from '../../helpers/makeRequest';
import { TenantMember } from '../../types/members';
import { 
  useTenantMembers, 
  useInviteTenantMember, 
  useRemoveTenantMember, 
  useUpdateTenantMemberRole 
} from '../../hooks';
import classes from './OrganizationMembers.module.css';

export function OrganizationMembers() {
  const { tenantId } = useTenantContext();
  const { canInviteTenantMembers, canRemoveTenantMembers, canUpdateTenantRoles } = usePermissions();
  
  // React Query hooks
  const { data, isLoading } = useTenantMembers(tenantId ?? '');
  const inviteMutation = useInviteTenantMember();
  const removeMutation = useRemoveTenantMember();
  const updateRoleMutation = useUpdateTenantMemberRole();

  const members = data?.data?.members ?? [];
  const loading = isLoading;
  const inviting = inviteMutation.isPending;
  const updatingRole = updateRoleMutation.isPending;
  
  const [inviteModalOpen, setInviteModalOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState<TenantRole>(TENANT_ROLES.MEMBER);
  const [editingRoleUserId, setEditingRoleUserId] = useState<string | null>(null);
  const [newRole, setNewRole] = useState<TenantRole | ''>('');
  const [removeConfirmUser, setRemoveConfirmUser] = useState<{ userId: string; userName: string } | null>(null);
  
  // Get current user ID to prevent self-role changes
  const currentUserId = getCookies(COOKIES_KEY.USER_ID);

  const handleInviteMember = () => {
    if (!inviteEmail.trim() || !tenantId) return;
    
    inviteMutation.mutate(
      {
        tenantId,
        email: inviteEmail.trim(),
        role: inviteRole,
      },
      {
        onSuccess: (response: ApiResponse<TenantMember>) => {
          if (response?.data && !response?.error) {
            showNotification(
              'Success',
              `Invitation sent to ${inviteEmail}`,
              <IconUserPlus />,
              '#0ec9c2'
            );
            setInviteModalOpen(false);
            setInviteEmail('');
            setInviteRole(TENANT_ROLES.MEMBER);
          } else {
            showNotification(
              'Error',
              response?.error?.message || 'Failed to invite member',
              <IconUserPlus />,
              '#fa5252'
            );
          }
        },
        onError: (error: any) => {
          showNotification(
            'Error',
            error.message || 'Failed to invite member',
            <IconUserPlus />,
            '#fa5252'
          );
        },
      }
    );
  };

  const handleRemoveMember = (userId: string, userName: string) => {
    setRemoveConfirmUser({ userId, userName });
  };

  const confirmRemoveMember = () => {
    if (!removeConfirmUser || !tenantId) return;
    
    const { userId, userName } = removeConfirmUser;
    
    removeMutation.mutate(
      {
        tenantId,
        userId,
      },
      {
        onSuccess: (response: ApiResponse<void>) => {
          if ((response?.data !== undefined || !response?.error) && !response?.error) {
            showNotification(
              'Success',
              `${userName} removed from organization`,
              <IconTrash />,
              '#0ec9c2'
            );
            setRemoveConfirmUser(null);
          } else {
            showNotification(
              'Error',
              response?.error?.message || 'Failed to remove member',
              <IconTrash />,
              '#fa5252'
            );
          }
        },
        onError: (error: any) => {
          showNotification(
            'Error',
            error.message || 'Failed to remove member',
            <IconTrash />,
            '#fa5252'
          );
          setRemoveConfirmUser(null);
        },
      }
    );
  };

  const handleRoleUpdate = (userId: string, currentRole: string, userName: string) => {
    if (!tenantId || !newRole || newRole === currentRole) {
      setEditingRoleUserId(null);
      return;
    }
    
    updateRoleMutation.mutate(
      {
        tenantId,
        userId,
        newRole,
      },
      {
        onSuccess: (response: ApiResponse<TenantMember>) => {
          if ((response?.data !== undefined || !response?.error) && !response?.error) {
            showNotification('Success', `Updated ${userName}'s role to ${newRole}`, <IconUserPlus />, '#0ec9c2');
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
      case 'admin':
        return 'blue';
      case 'member':
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
            <Text className={classes.pageTitle}>Manage organization access</Text>
          </Box>
        </Box>
        <Box className={classes.contentTable}>
          <Box className={classes.tableHeader}>
            <Box className={classes.tableHeaderContent}>
              <IconUsers size={18} color="#0ba09a" />
              <Text className={classes.tableHeaderTitle}>Organization Members</Text>
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
            <Text className={classes.pageTitle}>Manage organization access</Text>
          </Box>
          {canInviteTenantMembers && (
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
            <Text className={classes.tableHeaderTitle}>Organization Members</Text>
            <Badge size="sm" variant="light" color="teal" ml="auto">
              {members.length} member{members.length !== 1 ? 's' : ''}
            </Badge>
          </Box>
        </Box>
        
        {members.length > 0 ? (
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
                {members.map((member) => {
                  const isCurrentUser = member.userId === currentUserId;
                  return (
                    <Table.Tr 
                      key={member.userId}
                      style={{ backgroundColor: isCurrentUser ? '#f8f9fa' : undefined }}
                    >
                      <Table.Td>
                        {member.name}
                        {isCurrentUser && <Text span c="dimmed" size="xs" ml="xs">(You)</Text>}
                      </Table.Td>
                      <Table.Td>{member.email}</Table.Td>
                      <Table.Td>
                        {editingRoleUserId === member.userId && canUpdateTenantRoles ? (
                          <Group gap="xs">
                            <Select
                              size="xs"
                              value={newRole}
                              onChange={(val) => setNewRole((val as TenantRole) || member.role)}
                              data={[
                                { value: TENANT_ROLES.ADMIN, label: TENANT_ROLE_LABELS[TENANT_ROLES.ADMIN] },
                                { value: TENANT_ROLES.MEMBER, label: TENANT_ROLE_LABELS[TENANT_ROLES.MEMBER] },
                              ]}
                              style={{ width: 120 }}
                              disabled={updatingRole || isCurrentUser}
                            />
                            <ActionIcon 
                              size="sm" 
                              color="teal" 
                              onClick={() => handleRoleUpdate(member.userId, member.role, member.name)}
                              loading={updatingRole}
                              disabled={updatingRole || isCurrentUser}
                            >
                              <IconCheck size={14} />
                            </ActionIcon>
                            <ActionIcon 
                              size="sm" 
                              color="gray" 
                              onClick={() => setEditingRoleUserId(null)}
                              disabled={updatingRole}
                            >
                              <IconX size={14} />
                            </ActionIcon>
                          </Group>
                        ) : (
                          <Group gap="xs">
                            <Badge 
                              color={getRoleBadgeColor(member.role)} 
                              variant="light"
                              title={isCurrentUser ? "You cannot change your own role" : undefined}
                            >
                              {member.role}
                            </Badge>
                            {canUpdateTenantRoles && !isCurrentUser && (
                              <ActionIcon
                                size="xs"
                                variant="subtle"
                                onClick={() => {
                                  setEditingRoleUserId(member.userId);
                                  setNewRole(member.role as TenantRole);
                                }}
                              >
                                <IconEdit size={12} />
                              </ActionIcon>
                            )}
                          </Group>
                        )}
                      </Table.Td>
                      <Table.Td>
                        {canRemoveTenantMembers && !isCurrentUser && (
                          <Button
                            size="xs"
                            variant="subtle"
                            color="red"
                            leftSection={<IconTrash size={14} />}
                            onClick={() => handleRemoveMember(member.userId, member.name)}
                            loading={removeMutation.isPending && removeMutation.variables?.userId === member.userId}
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
              No members yet. {canInviteTenantMembers && 'Invite your first team member to get started.'}
            </Text>
          </Box>
        )}

      </Box>

      {/* Invite Member Modal */}
      <Modal
        opened={inviteModalOpen}
        onClose={() => setInviteModalOpen(false)}
        title="Invite Team Member"
      >
        <Stack gap="md">
          <TextInput
            label="Email Address"
            placeholder="member@example.com"
            value={inviteEmail}
            onChange={(e) => setInviteEmail(e.target.value)}
          />
          <Select
            label="Role"
            value={inviteRole}
            onChange={(value) => setInviteRole((value as TenantRole) || TENANT_ROLES.MEMBER)}
            data={[
              { value: TENANT_ROLES.ADMIN, label: TENANT_ROLE_LABELS[TENANT_ROLES.ADMIN] },
              { value: TENANT_ROLES.MEMBER, label: TENANT_ROLE_LABELS[TENANT_ROLES.MEMBER] },
            ]}
          />
          <Button
            onClick={handleInviteMember}
            loading={inviting}
            disabled={inviting || !inviteEmail.trim()}
          >
            Send Invitation
          </Button>
        </Stack>
      </Modal>

      <ConfirmationModal
        opened={removeConfirmUser !== null}
        onClose={() => setRemoveConfirmUser(null)}
        onConfirm={confirmRemoveMember}
        title="Remove Member?"
        message={`Are you sure you want to remove ${removeConfirmUser?.userName} from the organization? They will lose access to all projects immediately.`}
        confirmLabel="Yes, Remove"
        cancelLabel="Cancel"
        confirmColor="red"
        loading={removeMutation.isPending}
        severity="warning"
      />
    </Box>
  );
}
