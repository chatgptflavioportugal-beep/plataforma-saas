package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para o resumo de um cliente (usuario) na listagem administrativa. */
public class CustomerSummaryTO {

    @Column(name = "id") private String id;
    @Column(name = "email") private String email;
    @Column(name = "full_name") private String fullName;
    @Column(name = "is_active") private Boolean isActive;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "last_sign_in_at") private String lastSignInAt;
    @Column(name = "has_individual_profile") private Boolean hasIndividualProfile;
    @Column(name = "owned_companies_count") private Integer ownedCompaniesCount;
    @Column(name = "member_companies_count") private Integer memberCompaniesCount;

    public String id() { return id; }
    public String email() { return email; }
    public String fullName() { return fullName; }
    public Boolean isActive() { return isActive; }
    public String createdAt() { return createdAt; }
    public String lastSignInAt() { return lastSignInAt; }
    public Boolean hasIndividualProfile() { return hasIndividualProfile; }
    public Integer ownedCompaniesCount() { return ownedCompaniesCount; }
    public Integer memberCompaniesCount() { return memberCompaniesCount; }
}
