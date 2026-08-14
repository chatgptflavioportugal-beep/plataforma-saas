package com.saas.subscription.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "platform_modules")
public class PlatformModule extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String slug;

    public String description;

    @Column(name = "icon_path")
    public String iconPath;

    @Column(name = "is_active", nullable = false)
    public boolean isActive;

    @Column(name = "sort_order", nullable = false)
    public int sortOrder;
}
