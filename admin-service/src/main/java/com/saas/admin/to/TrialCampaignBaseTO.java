package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/**
 * TO da camada de dados com as colunas comuns de uma campanha de Trial — compartilhadas
 * por {@code findByPlan}/{@code listAll}/{@code findDetail} (ver {@code TrialCampaignDAO}).
 * {@link TrialCampaignListItemTO} e {@link TrialCampaignDetailTO} estendem esta classe para
 * adicionar as colunas extras de cada consulta (o {@code GenericTOMapper} mapeia campos
 * {@code @Column} herdados normalmente).
 */
public class TrialCampaignBaseTO {

    @Column(name = "id") private String id;
    @Column(name = "plan_version_module_id") private String planVersionModuleId;
    @Column(name = "module_id") private String moduleId;
    @Column(name = "module_name") private String moduleName;
    @Column(name = "name") private String name;
    @Column(name = "status") private String status;
    @Column(name = "days") private Integer days;
    @Column(name = "max_slots") private Integer maxSlots;
    @Column(name = "used_slots") private Integer usedSlots;
    @Column(name = "start_date") private String startDate;
    @Column(name = "end_date") private String endDate;
    @Column(name = "notes") private String notes;
    @Column(name = "priority") private Integer priority;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "updated_at") private String updatedAt;
    @Column(name = "created_by_user_id") private String createdByUserId;
    @Column(name = "created_by_name") private String createdByName;
    @Column(name = "updated_by_user_id") private String updatedByUserId;
    @Column(name = "updated_by_name") private String updatedByName;
    @Column(name = "expired") private Boolean expired;

    public String id() { return id; }
    public String planVersionModuleId() { return planVersionModuleId; }
    public String moduleId() { return moduleId; }
    public String moduleName() { return moduleName; }
    public String name() { return name; }
    public String status() { return status; }
    public Integer days() { return days; }
    public Integer maxSlots() { return maxSlots; }
    public Integer usedSlots() { return usedSlots; }
    public String startDate() { return startDate; }
    public String endDate() { return endDate; }
    public String notes() { return notes; }
    public Integer priority() { return priority; }
    public String createdAt() { return createdAt; }
    public String updatedAt() { return updatedAt; }
    public String createdByUserId() { return createdByUserId; }
    public String createdByName() { return createdByName; }
    public String updatedByUserId() { return updatedByUserId; }
    public String updatedByName() { return updatedByName; }
    public Boolean expired() { return expired; }
}
