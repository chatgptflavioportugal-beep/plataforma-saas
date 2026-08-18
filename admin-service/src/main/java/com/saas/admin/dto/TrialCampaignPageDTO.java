package com.saas.admin.dto;

import java.util.List;

public record TrialCampaignPageDTO(List<TrialCampaignListItemDTO> items, long total, int page, int size) {
}
