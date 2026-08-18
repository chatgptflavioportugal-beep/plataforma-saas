package com.saas.admin.dto;

import java.util.List;

public record CustomerDetailDTO(
        String id,
        String email,
        String fullName,
        Boolean isActive,
        String createdAt,
        String lastSignInAt,
        IndividualProfileDTO individualProfile,
        List<OwnedCompanyDTO> ownedCompanies,
        List<MemberCompanyDTO> memberCompanies) {

    public record IndividualProfileDTO(String id, String name, String slug, String status, String createdAt) {
    }

    public record OwnedCompanyDTO(
            String id, String name, String slug, String status, String createdAt,
            String planName, String planCode, Integer memberCount) {
    }

    public record MemberCompanyDTO(
            String id, String name, String slug, String role, Boolean linkActive,
            String joinedAt, String invitedByName) {
    }
}
