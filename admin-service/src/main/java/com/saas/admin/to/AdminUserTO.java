package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/**
 * TO da camada de dados para um usuario administrativo (tabelas {@code user_profiles} +
 * {@code auth.users} + {@code admin_access_levels}). Reaproveitado por consultas que nao
 * selecionam todas as colunas (o campo fica {@code null} quando a coluna nao esta na query).
 */
public class AdminUserTO {

    @Column(name = "id") private String id;
    @Column(name = "email") private String email;
    @Column(name = "full_name") private String fullName;
    @Column(name = "system_role") private String systemRole;
    @Column(name = "is_active") private Boolean isActive;
    @Column(name = "admin_access_level_id") private String adminAccessLevelId;
    @Column(name = "access_level_name") private String accessLevelName;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "last_sign_in_at") private String lastSignInAt;

    public String id() { return id; }
    public String email() { return email; }
    public String fullName() { return fullName; }
    public String systemRole() { return systemRole; }
    public Boolean isActive() { return isActive; }
    public String adminAccessLevelId() { return adminAccessLevelId; }
    public String accessLevelName() { return accessLevelName; }
    public String createdAt() { return createdAt; }
    public String lastSignInAt() { return lastSignInAt; }
}
