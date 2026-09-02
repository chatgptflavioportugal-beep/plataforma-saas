package com.saas.profile.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para um membro ativo de um tenant. */
public class ActiveMemberTO {

    @Column(name = "user_id")
    private String userId;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email")
    private String email;

    @Column(name = "role")
    private String role;

    @Column(name = "created_at")
    private String joinedAt;

    @Column(name = "access_level_id")
    private String accessLevelId;

    @Column(name = "access_level_name")
    private String accessLevelName;

    public String userId() { return userId; }
    public String fullName() { return fullName; }
    public String email() { return email; }
    public String role() { return role; }
    public String joinedAt() { return joinedAt; }
    public String accessLevelId() { return accessLevelId; }
    public String accessLevelName() { return accessLevelName; }
}
