package com.saas.admin.to;

import com.saas.platformdatabase.annotations.Column;

/** TO da camada de dados para uma contagem agrupada por status. */
public class StatusCountTO {

    @Column(name = "status") private String status;
    @Column(name = "count") private Long count;

    public String status() { return status; }
    public long count() { return count != null ? count : 0L; }
}
