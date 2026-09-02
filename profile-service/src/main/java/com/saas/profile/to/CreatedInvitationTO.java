package com.saas.profile.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para o convite recem-criado (INSERT ... RETURNING). */
public class CreatedInvitationTO {

    @Column(name = "id")
    private String id;

    @Column(name = "token")
    private String token;

    @Column(name = "expires_at")
    private String expiresAt;

    public String id() { return id; }
    public String token() { return token; }
    public String expiresAt() { return expiresAt; }
}
