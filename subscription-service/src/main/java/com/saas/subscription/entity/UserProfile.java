package com.saas.subscription.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "user_profiles")
public class UserProfile extends PanacheEntityBase {

    /** Mesmo id de auth.users (Supabase). */
    @Id
    public UUID id;

    /** user | SUPER_ADMIN | ADMIN_USER | ADMIN | SUPPORT | FINANCE_ADMIN */
    @Column(name = "system_role", nullable = false)
    public String systemRole;

    @Column(name = "is_active", nullable = false)
    public boolean isActive;

    @Column(name = "admin_access_level_id")
    public UUID adminAccessLevelId;
}
