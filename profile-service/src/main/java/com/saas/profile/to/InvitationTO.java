package com.saas.profile.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para um convite de um tenant. */
public class InvitationTO {

    @Column(name = "id")
    private String id;

    @Column(name = "email")
    private String email;

    @Column(name = "role")
    private String role;

    @Column(name = "status")
    private String status;

    @Column(name = "expires_at")
    private String expiresAt;

    @Column(name = "created_at")
    private String createdAt;

    @Column(name = "access_level_id")
    private String accessLevelId;

    @Column(name = "access_level_name")
    private String accessLevelName;

    public String id() { return id; }
    public String email() { return email; }
    public String role() { return role; }
    public String status() { return status; }
    public String expiresAt() { return expiresAt; }
    public String createdAt() { return createdAt; }
    public String accessLevelId() { return accessLevelId; }
    public String accessLevelName() { return accessLevelName; }
}
