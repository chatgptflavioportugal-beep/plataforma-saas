package com.saas.subscription.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "trial_campaigns")
public class TrialCampaign extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "plan_version_module_id", nullable = false)
    public UUID planVersionModuleId;

    @Column(nullable = false)
    public String name;

    /** ACTIVE | SCHEDULED | CLOSED | CANCELLED */
    @Column(nullable = false)
    public String status;

    @Column(nullable = false)
    public int days;

    @Column(name = "max_slots", nullable = false)
    public int maxSlots;

    @Column(name = "used_slots", nullable = false)
    public int usedSlots;

    @Column(name = "start_date")
    public LocalDate startDate;

    @Column(name = "end_date")
    public LocalDate endDate;

    @Column(nullable = false)
    public int priority;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    public boolean isSelectable() {
        LocalDate today = LocalDate.now();
        boolean withinWindow = (startDate == null || !startDate.isAfter(today))
                && (endDate == null || !endDate.isBefore(today));
        return "ACTIVE".equals(status) && usedSlots < maxSlots && withinWindow;
    }
}
