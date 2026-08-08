package com.saas.catalog.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mapeia user_tenants (mesma tabela já consultada via SQL nativo em
 * UserTenantRepository/TenantSubscriptionRepository). Este serviço não usa
 * JPQL nem Panache — toda leitura continua via EntityManager.createNativeQuery.
 * Sem nenhuma @Entity, o Hibernate ORM não registra o persistence unit e o
 * EntityManager injetado nos repositórios/resources falha ao subir (ver
 * docs/decisoes-pos-revisao-arquitetura.md, item 5). Esta classe existe só
 * para ativar o persistence unit — quarkus.hibernate-orm.database.generation
 * =none, então nunca é usada para gerar/validar schema.
 */
@Entity
@Table(name = "user_tenants")
public class UserTenant extends PanacheEntityBase {

    @Id
    @Column(updatable = false, nullable = false)
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Column(nullable = false)
    public String role;

    @Column(name = "is_active", nullable = false)
    public boolean isActive;

    @Column(name = "permissions_version", nullable = false)
    public int permissionsVersion;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}
