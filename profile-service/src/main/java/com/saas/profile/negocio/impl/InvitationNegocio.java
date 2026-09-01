package com.saas.profile.negocio.impl;

import com.saas.profile.dto.member.AcceptInvitationResponse;
import com.saas.profile.dto.member.InvitationDto;
import com.saas.profile.dto.member.InvitationPreviewResponse;
import com.saas.profile.dto.member.MemberDto;
import com.saas.profile.dto.member.SendInvitationResponse;

import java.util.List;
import java.util.UUID;

/**
 * Membros e convites de um tenant: listagem, remoção, alteração de nível de
 * acesso, envio/cancelamento de convite e aceite pelo convidado.
 */
public interface InvitationNegocio {

    List<MemberDto> listMembers(UUID tenantId);

    void removeMember(UUID tenantId, UUID targetUserId, UUID requestingUserId, String requestingRole);

    void changeMemberAccessLevel(UUID tenantId, UUID targetUserId, String accessLevelId);

    List<InvitationDto> listInvitations(UUID tenantId);

    SendInvitationResponse sendInvitation(UUID tenantId, String email, String accessLevelId, UUID invitedBy);

    void cancelInvitation(UUID tenantId, UUID invitationId);

    InvitationPreviewResponse getInvitationPreview(String token);

    AcceptInvitationResponse acceptInvitation(String token, UUID userId, String userEmail);
}
