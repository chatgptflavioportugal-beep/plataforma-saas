package com.saas.profile.to;

import com.saas.platformdatabase.annotations.Column;

/**
 * TO da camada de dados para um vinculo usuario-tenant com os dados do tenant associado
 * (tabelas {@code user_tenants} + {@code tenants}) — usado para listar todos os tenants de
 * um usuario ("meus perfis").
 */
public class UserTenantMembershipTO {

    @Column(name = "id")
    private String id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "role")
    private String role;

    @Column(name = "is_active")
    private boolean isActive;

    @Column(name = "name")
    private String tenantName;

    @Column(name = "slug")
    private String tenantSlug;

    @Column(name = "status")
    private String tenantStatus;

    @Column(name = "type")
    private String tenantType;

    @Column(name = "plan_id")
    private String planId;

    @Column(name = "trial_ends_at")
    private String trialEndsAt;

    @Column(name = "created_at")
    private String createdAt;

    @Column(name = "updated_at")
    private String updatedAt;

    public String id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String role() {
        return role;
    }

    public boolean isActive() {
        return isActive;
    }

    public String tenantName() {
        return tenantName;
    }

    public String tenantSlug() {
        return tenantSlug;
    }

    public String tenantStatus() {
        return tenantStatus;
    }

    public String tenantType() {
        return tenantType;
    }

    public String planId() {
        return planId;
    }

    public String trialEndsAt() {
        return trialEndsAt;
    }

    public String createdAt() {
        return createdAt;
    }

    public String updatedAt() {
        return updatedAt;
    }
}
