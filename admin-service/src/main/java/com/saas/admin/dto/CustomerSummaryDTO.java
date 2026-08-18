package com.saas.admin.dto;

public record CustomerSummaryDTO(
        String id,
        String email,
        String fullName,
        Boolean isActive,
        String createdAt,
        String lastSignInAt,
        Boolean hasIndividualProfile,
        Integer ownedCompaniesCount,
        Integer memberCompaniesCount,
        int totalProfiles) {
}
