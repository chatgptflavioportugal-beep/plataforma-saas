package com.saas.platformdatabase.support;

import com.saas.platformdatabase.annotations.Column;

/** TO que estende {@link BaseAuditTO} — cobre o caso de campo {@code @Column} herdado. */
public class SampleAuditedUserTO extends BaseAuditTO {

    @Column(name = "email")
    private String email;

    public String getEmail() {
        return email;
    }
}
