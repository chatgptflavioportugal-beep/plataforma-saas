package com.saas.admin.negocio.impl;

import com.saas.admin.dto.CancelRequest;
import com.saas.admin.dto.TrialCampaignDTO;
import com.saas.admin.dto.TrialCampaignDetailDTO;
import com.saas.admin.dto.TrialCampaignHistoryEntryDTO;
import com.saas.admin.dto.TrialCampaignPageDTO;
import com.saas.admin.dto.TrialCampaignParticipantDTO;
import com.saas.admin.dto.TrialCampaignRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Administração do ciclo de vida das campanhas de Trial (CRUD, cancelamento,
 * indicadores e histórico). Regras de negócio e validações de
 * AdminTrialCampaignResource — persistência isolada em TrialCampaignDAO.
 */
public interface AdminTrialCampaignNegocio {

    record UpdateResult(boolean termsLocked) {
    }

    List<TrialCampaignDTO> listByPlan(String planId);

    TrialCampaignPageDTO listAll(
            String status, String moduleId, String planId, String createdBy, String search,
            String startDateFrom, String startDateTo, Boolean hasSlots,
            String sortBy, String sortDir, Integer page, Integer size);

    Optional<TrialCampaignDetailDTO> getDetail(String id);

    List<TrialCampaignParticipantDTO> listParticipants(String id);

    List<TrialCampaignHistoryEntryDTO> history(String id);

    UUID create(TrialCampaignRequest req, String userId);

    Optional<UpdateResult> update(String id, TrialCampaignRequest req, String userId);

    boolean close(String id, String userId);

    boolean cancel(String id, CancelRequest req, String userId);
}
