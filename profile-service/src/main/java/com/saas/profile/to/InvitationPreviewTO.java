package com.saas.profile.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para o preview de um convite a partir do token (tela de aceite). */
public class InvitationPreviewTO {

    @Column(name = "email")
    private String email;

    @Column(name = "role")
    private String role;

    @Column(name = "status")
    private String status;

    @Column(name = "expires_at")
    private String expiresAt;

    @Column(name = "tenant_name")
    private String tenantName;

    @Column(name = "access_level_name")
    private String accessLevelName;

    public String email() { return email; }
    public String role() { return role; }
    public String status() { return status; }
    public String expiresAt() { return expiresAt; }
    public String tenantName() { return tenantName; }
    public String accessLevelName() { return accessLevelName; }
}
