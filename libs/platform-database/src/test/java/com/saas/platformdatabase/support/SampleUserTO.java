package com.saas.platformdatabase.support;

import com.saas.platformdatabase.annotations.Column;

import java.math.BigDecimal;
import java.util.UUID;

/** TO de teste cobrindo os tipos suportados pelo {@code ConversionUtils}. */
public class SampleUserTO {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email")
    private String email;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "login_count")
    private Long loginCount;

    @Column(name = "score")
    private BigDecimal score;

    /** Sem {@code @Column} — deve permanecer nulo mesmo se a linha tiver um alias "ignored". */
    private String ignored;

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public Boolean getActive() {
        return active;
    }

    public Long getLoginCount() {
        return loginCount;
    }

    public BigDecimal getScore() {
        return score;
    }

    public String getIgnored() {
        return ignored;
    }
}
