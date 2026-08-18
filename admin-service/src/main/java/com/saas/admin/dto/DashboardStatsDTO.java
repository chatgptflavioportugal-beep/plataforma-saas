package com.saas.admin.dto;

public record DashboardStatsDTO(
        long totalTenants,
        long activeTenants,
        long trialTenants,
        long suspendedTenants,
        long totalUsers,
        long totalPdfJobs,
        long usersWithIndividualProfile,
        long usersWithoutIndividualProfile,
        long totalMemberLinks,
        long usersInMultipleCompanies,
        long companiesWithoutExtraMembers,
        long companiesWithInvitedMembers) {
}
