package com.saas.profile.to;

import com.saas.platformdatabase.annotations.Column;

import java.util.UUID;

/** TO da camada de dados para a assinatura ativa de um tenant (tabelas {@code tenant_subscriptions} + {@code plans}). */
public class SubscriptionAccountTO {

    @Column(name = "id")
    private UUID id;

    @Column(name = "status")
    private String status;

    @Column(name = "code")
    private String planCode;

    public UUID id() {
        return id;
    }

    public String status() {
        return status;
    }

    public String planCode() {
        return planCode;
    }
}
