import { useState, useEffect, useCallback } from 'react';
import { Container, Title, Table, Button, Group, Text, Modal, TextInput, Select, Badge, ActionIcon, Loader } from '@mantine/core';
import { IconUserPlus, IconTrash, IconCheck, IconX, IconEdit } from '@tabler/icons-react';
import { useTenantContext } from '../../contexts';
import { usePermissions } from '../../hooks';
import { showNotification } from '../../helpers/showNotification';
import { API_BASE_URL, COOKIES_KEY } from '../../constants';
import { makeRequest } from '../../helpers/makeRequest';
import { getCookies } from '../../helpers/cookies';

interface Member {
  userId: string;
  email: string;
  name: string;
  role: string;
  status: string;
  lastLoginAt?: string;
}

interface MemberListResponse {
  members: Member[];
  totalCount: number;
}

export function OrganizationMembers() {
  const { tenantId } = useTenantContext();
  const { canInviteTenantMembers, canRemoveTenantMembers, canUpdateTenantRoles } = usePermissions();
  
  const [members, setMembers] = useState<Member[]>([]);
  const [loading, setLoading] = useState(true);
  const [inviteModalOpen, setInviteModalOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState<string>('member');
  const [inviting, setInviting] = useState(false);
  const [editingRoleUserId, setEditingRoleUserId] = useState<string | null>(null);
  const [newRole, setNewRole] = useState<string>('');
  const [updatingRole, setUpdatingRole] = useState(false);
  
  // Get current user ID to prevent self-role changes
  const currentUserId = getCookies(COOKIES_KEY.USER_ID);

  const fetchMembers = useCallback(async () => {
    if (!tenantId) return;
    
    setLoading(true);
    try {
      const response = await makeRequest<MemberListResponse>({
        url: `${API_BASE_URL}/v1/tenants/${tenantId}/members`,
        init: {
          method: 'GET',
        },
      });
      
      if (response.data) {
        setMembers(response.data.members);
      } else {
        showNotification(
          'Error',
          response.error?.message || 'Failed to load members',
          <IconUserPlus />,
          '#fa5252'
        );
      }
    } catch (error) {
      console.error('[OrganizationMembers] Error fetching members:', error);
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    if (tenantId) {
      fetchMembers();
    }
  }, [tenantId, fetchMembers]);

  const handleInviteMember = async () => {
    if (!inviteEmail.trim() || !tenantId) return;
    
    setInviting(true);
    try {
      const response = await makeRequest<Member>({
        url: `${API_BASE_URL}/v1/tenants/${tenantId}/members`,
        init: {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            email: inviteEmail.trim(),
            role: inviteRole,
          }),
        },
      });
      
      if (response.data) {
        showNotification(
          'Success',
          `Invitation sent to ${inviteEmail}`,
          <IconUserPlus />,
          '#0ec9c2'
        );
        setInviteModalOpen(false);
        setInviteEmail('');
        setInviteRole('member');
        // Refresh members list
        fetchMembers();
      } else {
        showNotification(
          'Error',
          response.error?.message || 'Failed to invite member',
          <IconUserPlus />,
          '#fa5252'
        );
      }
    } catch (error: any) {
      showNotification(
        'Error',
        error.message || 'Failed to invite member',
        <IconUserPlus />,
        '#fa5252'
      );
    } finally {
      setInviting(false);
    }
  };

  const handleRemoveMember = async (userId: string, userName: string) => {
    if (!window.confirm(`Are you sure you want to remove ${userName} from the organization?`)) {
      return;
    }
    
    if (!tenantId) return;
    
    try {
      const response = await makeRequest<void>({
        url: `${API_BASE_URL}/v1/tenants/${tenantId}/members/${userId}`,
        init: {
          method: 'DELETE',
        },
      });
      
      if (response.data !== undefined || !response.error) {
        showNotification(
          'Success',
          `${userName} removed from organization`,
          <IconTrash />,
          '#0ec9c2'
        );
        // Refresh members list
        fetchMembers();
      } else {
        showNotification(
          'Error',
          response.error?.message || 'Failed to remove member',
          <IconTrash />,
          '#fa5252'
        );
      }
    } catch (error: any) {
      showNotification(
        'Error',
        error.message || 'Failed to remove member',
        <IconTrash />,
        '#fa5252'
      );
    }
  };

  const handleRoleUpdate = async (userId: string, currentRole: string, userName: string) => {
    if (!tenantId || !newRole || newRole === currentRole) {
      setEditingRoleUserId(null);
      return;
    }
    
    setUpdatingRole(true);
    try {
      const response = await makeRequest<void>({
        url: `${API_BASE_URL}/v1/tenants/${tenantId}/members/${userId}`,
        init: {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ newRole }),
        },
      });
      
      if (response.data !== undefined || !response.error) {
        showNotification('Success', `Updated ${userName}'s role to ${newRole}`, <IconUserPlus />, '#0ec9c2');
        setEditingRoleUserId(null);
        fetchMembers();
      } else {
        showNotification('Error', response.error?.message || 'Failed to update role', <IconUserPlus />, '#fa5252');
      }
    } catch (error: any) {
      showNotification('Error', error.message || 'Failed to update role', <IconUserPlus />, '#fa5252');
    } finally {
      setUpdatingRole(false);
    }
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
      <Container size="xl">
        <Loader size="lg" />
        <Text mt="md">Loading members...</Text>
      </Container>
    );
  }

  return (
    <Container size="xl">
      <Group justify="space-between" mb="xl">
        <div>
          <Title order={1}>Team Members</Title>
          <Text c="dimmed" size="sm">
            Manage your organization's team members
          </Text>
        </div>
        {canInviteTenantMembers && (
          <Button
            leftSection={<IconUserPlus size={16} />}
            onClick={() => setInviteModalOpen(true)}
            variant="gradient"
            gradient={{ from: '#0ec9c2', to: '#0ba09a' }}
          >
            Invite Member
          </Button>
        )}
      </Group>
      
      {members.length === 0 ? (
        <Text ta="center" c="dimmed" py="xl">
          No members yet. {canInviteTenantMembers && 'Invite your first team member to get started.'}
        </Text>
      ) : (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Name</Table.Th>
              <Table.Th>Email</Table.Th>
              <Table.Th>Role</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th>Last Login</Table.Th>
              {canRemoveTenantMembers && <Table.Th>Actions</Table.Th>}
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
                          onChange={(val) => setNewRole(val || member.role)}
                          data={[
                            { value: 'admin', label: 'Admin - Can manage members and projects' },
                            { value: 'member', label: 'Member - Can view and access projects' },
                          ]}
                          style={{ width: 200 }}
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
                              setNewRole(member.role);
                            }}
                          >
                            <IconEdit size={12} />
                          </ActionIcon>
                        )}
                      </Group>
                    )}
                  </Table.Td>
                  <Table.Td>
                    <Badge color={member.status === 'active' ? 'teal' : 'gray'} variant="light">
                      {member.status}
                    </Badge>
                  </Table.Td>
                  <Table.Td>
                    {member.lastLoginAt 
                      ? new Date(member.lastLoginAt).toLocaleDateString()
                      : 'Never'}
                  </Table.Td>
                  {canRemoveTenantMembers && (
                    <Table.Td>
                      {!isCurrentUser && (
                        <ActionIcon
                          color="red"
                          variant="subtle"
                          onClick={() => handleRemoveMember(member.userId, member.name)}
                        >
                          <IconTrash size={16} />
                        </ActionIcon>
                      )}
                    </Table.Td>
                  )}
                </Table.Tr>
              );
            })}
          </Table.Tbody>
        </Table>
      )}

      {/* Invite Member Modal */}
      <Modal
        opened={inviteModalOpen}
        onClose={() => setInviteModalOpen(false)}
        title="Invite Team Member"
        size="md"
      >
        <TextInput
          label="Email Address"
          placeholder="member@example.com"
          value={inviteEmail}
          onChange={(e) => setInviteEmail(e.target.value)}
          required
          mb="md"
        />
        <Select
          label="Role"
          value={inviteRole}
          onChange={(value) => setInviteRole(value || 'member')}
          data={[
            { value: 'member', label: 'Member - Can view and access projects' },
            { value: 'admin', label: 'Admin - Can manage members and projects' },
          ]}
          required
          mb="lg"
        />
        <Group justify="flex-end">
          <Button
            variant="subtle"
            color="gray"
            onClick={() => setInviteModalOpen(false)}
            disabled={inviting}
          >
            Cancel
          </Button>
          <Button
            onClick={handleInviteMember}
            loading={inviting}
            disabled={inviting || !inviteEmail.trim()}
            variant="gradient"
            gradient={{ from: '#0ec9c2', to: '#0ba09a' }}
          >
            Send Invitation
          </Button>
        </Group>
      </Modal>
    </Container>
  );
}
