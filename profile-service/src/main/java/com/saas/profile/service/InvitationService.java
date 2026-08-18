package com.saas.profile.service;

import com.saas.profile.dto.member.*;
import com.saas.profile.repository.InvitationRepository;
import com.saas.profile.repository.UserTenantRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class InvitationService {

    @Inject
    InvitationRepository invitationRepository;

    @Inject
    EmailService emailService;

    @Inject
    UserTenantRepository userTenantRepository;

    @ConfigProperty(name = "app.base-url", defaultValue = "http://localhost:5100")
    String baseUrl;

    // ----------------------------------------------------------------
    // Listar membros ativos do tenant (com nome e e-mail)
    // ----------------------------------------------------------------

    public List<MemberDto> listMembers(UUID tenantId) {
        return invitationRepository.findActiveMembers(tenantId).stream()
                .map(r -> new MemberDto(r.userId(), r.fullName(), r.email(), r.role(), r.joinedAt(), r.accessLevelId(), r.accessLevelName()))
                .toList();
    }

    // ----------------------------------------------------------------
    // Remover membro do tenant (soft delete em user_tenants)
    // Apenas owner pode remover qualquer membro.
    // Admin pode remover somente membros (não outros admins).
    // Ninguém remove a si mesmo nem o último owner.
    // ----------------------------------------------------------------

    @Transactional
    public void removeMember(UUID tenantId, UUID targetUserId, UUID requestingUserId, String requestingRole) {
        if (targetUserId.equals(requestingUserId)) {
            throw new BadRequestException("Você não pode remover a si mesmo");
        }

        String targetRole = invitationRepository.findActiveMemberRole(tenantId, targetUserId)
                .orElseThrow(() -> new NotFoundException("Membro não encontrado"));

        if ("owner".equals(targetRole)) {
            throw new ForbiddenException("Não é possível remover o proprietário da empresa");
        }
        if ("admin".equals(requestingRole) && "admin".equals(targetRole)) {
            throw new ForbiddenException("Administradores não podem remover outros administradores");
        }

        invitationRepository.deactivateMember(tenantId, targetUserId);

        // Membro removido — invalida qualquer PAT/MAT em cache dele (ModuleTokenFilter
        // já rejeita por is_active=FALSE, o bump cobre também o caso de readmissão futura).
        userTenantRepository.bumpVersionForMember(targetUserId, tenantId);
    }

    // ----------------------------------------------------------------
    // Alterar o nível de acesso de um membro ativo
    // ----------------------------------------------------------------

    @Transactional
    public void changeMemberAccessLevel(UUID tenantId, UUID targetUserId, String accessLevelId) {
        UUID alId;
        try {
            alId = UUID.fromString(accessLevelId);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("ID de nível de acesso inválido");
        }

        if (invitationRepository.findActiveAccessLevelName(tenantId, alId).isEmpty()) {
            throw new BadRequestException("Nível de acesso inválido ou inativo");
        }

        String targetRole = invitationRepository.findActiveMemberRole(tenantId, targetUserId)
                .orElseThrow(() -> new NotFoundException("Membro não encontrado"));
        if (!"member".equals(targetRole)) {
            throw new BadRequestException("Apenas membros possuem nível de acesso atribuído");
        }

        invitationRepository.updateMemberAccessLevel(tenantId, targetUserId, alId);
        userTenantRepository.bumpVersionForMember(targetUserId, tenantId);
    }

    // ----------------------------------------------------------------
    // Listar convites do tenant (pendentes + histórico)
    // ----------------------------------------------------------------

    public List<InvitationDto> listInvitations(UUID tenantId) {
        return invitationRepository.findAllByTenant(tenantId).stream()
                .map(r -> new InvitationDto(r.id(), r.email(), r.role(), r.status(), r.expiresAt(), r.createdAt(), r.accessLevelId(), r.accessLevelName()))
                .toList();
    }

    // ----------------------------------------------------------------
    // Enviar convite com Nível de Acesso
    // ----------------------------------------------------------------

    @Transactional
    public SendInvitationResponse sendInvitation(UUID tenantId, String email, String accessLevelId, UUID invitedBy) {
        String normalizedEmail = email.trim().toLowerCase();

        UUID alId;
        try {
            alId = UUID.fromString(accessLevelId);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("ID de nível de acesso inválido");
        }

        String accessLevelName = invitationRepository.findActiveAccessLevelName(tenantId, alId)
                .orElseThrow(() -> new BadRequestException("Nível de acesso inválido ou inativo"));

        if (invitationRepository.countActiveMemberByEmail(tenantId, normalizedEmail) > 0) {
            throw new BadRequestException("Este usuário já é membro da empresa");
        }

        if (invitationRepository.countPendingInvitation(tenantId, normalizedEmail) > 0) {
            throw new BadRequestException("Já existe um convite pendente para este e-mail");
        }

        String tenantName = invitationRepository.findTenantName(tenantId);

        var created = invitationRepository.insertInvitation(tenantId, invitedBy, normalizedEmail, alId);

        emailService.sendInvitationEmail(normalizedEmail, tenantName, accessLevelName,
                baseUrl + "/invite/accept?token=" + created.token());

        return new SendInvitationResponse(created.id(), normalizedEmail, alId.toString(), accessLevelName, created.expiresAt());
    }

    // ----------------------------------------------------------------
    // Cancelar convite
    // ----------------------------------------------------------------

    @Transactional
    public void cancelInvitation(UUID tenantId, UUID invitationId) {
        if (!invitationRepository.cancelPendingInvitation(tenantId, invitationId)) {
            throw new NotFoundException("Convite não encontrado ou já processado");
        }
    }

    // ----------------------------------------------------------------
    // Preview público do convite (sem autenticação)
    // ----------------------------------------------------------------

    public InvitationPreviewResponse getInvitationPreview(String token) {
        var row = invitationRepository.findPreviewByToken(token)
                .orElseThrow(() -> new NotFoundException("Convite não encontrado"));
        return new InvitationPreviewResponse(row.email(), row.role(), row.status(), row.expiresAt(), row.tenantName(), row.accessLevelName());
    }

    // ----------------------------------------------------------------
    // Aceitar convite (requer usuário autenticado)
    // ----------------------------------------------------------------

    @Transactional
    public AcceptInvitationResponse acceptInvitation(String token, UUID userId, String userEmail) {
        String inviteEmail = invitationRepository.findAcceptableInvitationEmail(token).orElseGet(() -> {
            if (!invitationRepository.invitationTokenExists(token)) {
                throw new NotFoundException("Convite não encontrado");
            }
            throw new BadRequestException("Convite inválido, expirado ou já utilizado");
        });

        String normalizedUserEmail = userEmail != null ? userEmail.trim().toLowerCase() : "";

        if (!inviteEmail.equalsIgnoreCase(normalizedUserEmail)) {
            throw new WebApplicationException(Response
                    .status(Response.Status.FORBIDDEN)
                    .entity(Map.of(
                            "error", "wrong_email",
                            "invitation_email", inviteEmail,
                            "user_email", normalizedUserEmail
                    ))
                    .type(MediaType.APPLICATION_JSON)
                    .build());
        }

        Object result = invitationRepository.callAcceptInvitationFunction(token, userId);
        String json = result.toString();

        if (json.contains("\"success\":false") || json.contains("\"success\": false")) {
            String error = extractJsonString(json, "error");
            throw new BadRequestException(error != null ? error : "Não foi possível aceitar o convite");
        }

        String tenantId = extractJsonString(json, "tenant_id");
        String role = extractJsonString(json, "role");

        return new AcceptInvitationResponse(tenantId, role);
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) {
            search = "\"" + key + "\": \"";
            idx = json.indexOf(search);
        }
        if (idx < 0) return null;
        int start = idx + search.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }
}
