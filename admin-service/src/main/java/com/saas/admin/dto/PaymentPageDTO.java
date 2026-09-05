package com.saas.admin.dto;

import java.util.List;

public record PaymentPageDTO(List<PaymentListItemDTO> items, long total, int page, int size) {
}
