package com.saas.admin.service;

import com.saas.admin.dao.TrialCampaignDAO;
import com.saas.admin.dto.CancelRequest;
import com.saas.admin.dto.TrialCampaignDTO;
import com.saas.admin.dto.TrialCampaignDetailDTO;
import com.saas.admin.dto.TrialCampaignHistoryEntryDTO;
import com.saas.admin.dto.TrialCampaignListItemDTO;
import com.saas.admin.dto.TrialCampaignPageDTO;
import com.saas.admin.dto.TrialCampaignParticipantDTO;
import com.saas.admin.dto.TrialCampaignRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Administração do ciclo de vida das campanhas de Trial (CRUD, cancelamento,
 * indicadores e histórico). Regras de negócio e validações de
 * AdminTrialCampaignResource — persistência isolada em {@link TrialCampaignDAO}.
 */
@ApplicationScoped
public class AdminTrialCampaignService {

    // Status que podem ser atribuídos diretamente por criação/edição. CANCELLED só
    // é alcançável pela ação dedicada POST /{id}/cancel (confirmação + auditoria).
    private static final List<String> VALID_REQUEST_STATUSES = List.of("ACTIVE", "SCHEDULED", "CLOSED");

    @Inject
    TrialCampaignDAO dao;

    @Inject
    AdminAuditService auditService;

    @Inject
    TrialCampaignAdminService trialCampaignAdminService;

    public List<TrialCampaignDTO> listByPlan(String planId) {
        return dao.findByPlan(planId);
    }

    public TrialCampaignPageDTO listAll(
            String status, String moduleId, String planId, String createdBy, String search,
            String startDateFrom, String startDateTo, Boolean hasSlots,
            String sortBy, String sortDir, Integer page, Integer size) {

        int safeSize = (size == null || size < 1 || size > 100) ? 20 : size;
        int safePage = (page == null || page < 1) ? 1 : page;

        TrialCampaignDAO.ListFilters filters = new TrialCampaignDAO.ListFilters(
            status, moduleId, planId, createdBy, search, startDateFrom, startDateTo, hasSlots,
            sortBy, sortDir, safePage, safeSize);

        TrialCampaignDAO.TrialCampaignListResult result = dao.listAll(filters);
        return new TrialCampaignPageDTO(result.items(), result.total(), safePage, safeSize);
    }

    public Optional<TrialCampaignDetailDTO> getDetail(String id) {
        return dao.findDetail(id);
    }

    public List<TrialCampaignParticipantDTO> listParticipants(String id) {
        return dao.findParticipants(id);
    }

    public List<TrialCampaignHistoryEntryDTO> history(String id) {
        return dao.findHistory(id);
    }

    @Transactional
    public UUID create(TrialCampaignRequest req, String userId) {
        if (req.planVersionModuleId() == null || req.planVersionModuleId().isBlank())
            throw new BadRequestException("planVersionModuleId é obrigatório");
        if (req.name() == null || req.name().isBlank())
            throw new BadRequestException("name é obrigatório");
        validateTerms(req.days(), req.maxSlots());
        String status = validateStatus(req.status());
        validateDates(req.startDate(), req.endDate());

        if (dao.countPvm(req.planVersionModuleId()) == 0)
            throw new NotFoundException("Módulo do plano não encontrado");

        // O plano Free já é a modalidade gratuita permanente do módulo — Trial não
        // faz sentido nele. Validação de negócio fica na camada de serviço para não
        // depender só do Controller nem do que o Frontend deixa de listar.
        if (trialCampaignAdminService.isFreePlanVersionModule(req.planVersionModuleId())) {
            auditService.log(userId, "trial_campaign.creation_denied", "trial_campaigns",
                req.planVersionModuleId(),
                Map.of(
                    "reason", "Free plans do not support Trial campaigns.",
                    "planVersionModuleId", req.planVersionModuleId()
                ));
            throw new BadRequestException("Não é permitido criar campanhas Trial para o plano Free.");
        }

        UUID id = dao.insert(req, status, userId);

        auditService.log(userId, "trial_campaign.created", "trial_campaigns", id.toString(),
            Map.of("name", req.name(), "status", status, "days", req.days(), "maxSlots", req.maxSlots()));

        return id;
    }

    public record UpdateResult(boolean termsLocked) {
    }

    @Transactional
    public Optional<UpdateResult> update(String id, TrialCampaignRequest req, String userId) {
        String oldStatus = dao.findStatus(id).orElse(null);
        if (oldStatus == null) return Optional.empty();

        // CANCELLED só é alcançável pela ação dedicada POST /{id}/cancel — mas se a
        // campanha já está CANCELLED, a edição pode seguir mexendo em outros campos
        // (notas, datas) sem reverter/reafirmar o cancelamento por aqui.
        String status;
        if ("CANCELLED".equals(oldStatus)) {
            if (!"CANCELLED".equals(req.status()))
                throw new BadRequestException("Campanha cancelada não pode ter o status alterado por edição");
            status = "CANCELLED";
        } else {
            status = validateStatus(req.status());
        }
        validateDates(req.startDate(), req.endDate());

        boolean hasParticipants = dao.hasParticipants(id);
        boolean termsLocked = hasParticipants;
        if (hasParticipants) {
            // Termos que afetariam retroativamente quem já participou não podem mudar —
            // dias e vagas ficam congelados; para alterá-los, crie uma nova campanha.
            dao.updateLocked(id, status, req, userId);
        } else {
            validateTerms(req.days(), req.maxSlots());
            dao.updateFull(id, status, req, userId);
        }

        auditService.log(userId, "trial_campaign.updated", "trial_campaigns", id,
            Map.of("statusBefore", oldStatus, "statusAfter", status));

        return Optional.of(new UpdateResult(termsLocked));
    }

    @Transactional
    public boolean close(String id, String userId) {
        int updated = dao.close(id, userId);
        if (updated == 0) return false;

        auditService.log(userId, "trial_campaign.closed", "trial_campaigns", id, null);
        return true;
    }

    @Transactional
    public boolean cancel(String id, CancelRequest req, String userId) {
        String reason = (req != null && req.reason() != null && !req.reason().isBlank())
            ? req.reason() : "Cancelado manualmente pelo administrador.";

        // Cancelar apenas impede novas ativações — participantes que já iniciaram o
        // Trial continuam normalmente até trialEndAt (nenhuma outra tabela é tocada).
        int updated = dao.cancel(id, reason, userId);
        if (updated == 0) return false;

        auditService.log(userId, "trial_campaign.cancelled", "trial_campaigns", id,
            Map.of("reason", reason));
        return true;
    }

    private void validateTerms(Integer days, Integer maxSlots) {
        if (days == null || days < 1 || days > 365)
            throw new BadRequestException("days deve estar entre 1 e 365");
        if (maxSlots == null || maxSlots < 1)
            throw new BadRequestException("maxSlots deve ser maior ou igual a 1");
    }

    private String validateStatus(String status) {
        if (status == null || !VALID_REQUEST_STATUSES.contains(status))
            throw new BadRequestException(
                "status inválido: deve ser um de " + VALID_REQUEST_STATUSES +
                " (cancelamento é feito via POST /{id}/cancel)");
        return status;
    }

    private void validateDates(String startDate, String endDate) {
        if (startDate == null || endDate == null) return;
        try {
            if (LocalDate.parse(endDate).isBefore(LocalDate.parse(startDate)))
                throw new BadRequestException("endDate não pode ser anterior a startDate");
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Data inválida: " + e.getMessage());
        }
    }
}
