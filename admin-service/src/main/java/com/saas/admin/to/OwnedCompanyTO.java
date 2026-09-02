package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para uma empresa (tenant) da qual o cliente e owner. */
public class OwnedCompanyTO {

    @Column(name = "id") private String id;
    @Column(name = "name") private String name;
    @Column(name = "slug") private String slug;
    @Column(name = "status") private String status;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "plan_name") private String planName;
    @Column(name = "plan_code") private String planCode;
    @Column(name = "member_count") private Integer memberCount;

    public String id() { return id; }
    public String name() { return name; }
    public String slug() { return slug; }
    public String status() { return status; }
    public String createdAt() { return createdAt; }
    public String planName() { return planName; }
    public String planCode() { return planCode; }
    public Integer memberCount() { return memberCount; }
}
