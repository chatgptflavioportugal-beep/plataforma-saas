package com.saas.subscription.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant extends PanacheEntityBase {

    @Id
    public UUID id;

    /** individual | business */
    @Column(nullable = false)
    public String type;
}
