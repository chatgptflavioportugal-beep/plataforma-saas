package com.saas.admin.dto;

import java.util.List;

public record SubscriptionPageDTO(List<SubscriptionListItemDTO> items, long total, int page, int size) {
}
