package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para uma entrada do historico de auditoria de uma campanha de Trial. */
public class TrialCampaignHistoryEntryTO {

    @Column(name = "action") private String action;
    @Column(name = "actor_name") private String actorName;
    @Column(name = "created_at") private String createdAt;

    public String action() { return action; }
    public String actorName() { return actorName; }
    public String createdAt() { return createdAt; }
}
