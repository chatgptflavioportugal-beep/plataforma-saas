package com.saas.subscription.to;

import com.saas.platformdatabase.annotations.Column;

import java.time.OffsetDateTime;

/** TO da camada de dados para o fim do cooldown de reutilizacao de um modulo em Trial. */
public class CooldownTO {

    @Column(name = "cooldown_ends_at")
    private OffsetDateTime cooldownEndsAt;

    public OffsetDateTime cooldownEndsAt() {
        return cooldownEndsAt;
    }
}
