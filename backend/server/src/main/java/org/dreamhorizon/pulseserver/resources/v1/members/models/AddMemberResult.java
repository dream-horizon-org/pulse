package org.dreamhorizon.pulseserver.resources.v1.members.models;

/**
 * Sealed interface for add-member API responses.
 * Single invite returns {@link MemberResponse}; bulk invite returns {@link BulkInviteResult}.
 */
public sealed interface AddMemberResult permits MemberResponse, BulkInviteResult {}
