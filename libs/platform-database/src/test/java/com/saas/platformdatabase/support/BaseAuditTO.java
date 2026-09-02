package com.saas.platformdatabase.support;

import com.saas.platformdatabase.annotations.Column;

import java.time.LocalDateTime;

/** Superclasse de TO usada para testar que {@code @Column} herdado tambem e mapeado. */
public abstract class BaseAuditTO {

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
