package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para os campos basicos de usuario no detalhe de cliente. */
public class CustomerUserTO {

    @Column(name = "id") private String id;
    @Column(name = "email") private String email;
    @Column(name = "full_name") private String fullName;
    @Column(name = "is_active") private Boolean isActive;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "last_sign_in_at") private String lastSignInAt;

    public String id() { return id; }
    public String email() { return email; }
    public String fullName() { return fullName; }
    public Boolean isActive() { return isActive; }
    public String createdAt() { return createdAt; }
    public String lastSignInAt() { return lastSignInAt; }
}
