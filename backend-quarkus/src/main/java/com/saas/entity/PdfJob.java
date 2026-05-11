package com.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "pdf_jobs")
public class PdfJob extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(nullable = false)
    public String status = "pending";

    @Column(name = "file_a_name", nullable = false)
    public String fileAName;

    @Column(name = "file_b_name", nullable = false)
    public String fileBName;

    @Column(name = "file_a_path", nullable = false)
    public String fileAPath;

    @Column(name = "file_b_path", nullable = false)
    public String fileBPath;

    @Column(name = "result_path")
    public String resultPath;

    @Column(name = "result_name")
    public String resultName;

    @Column(name = "error_message")
    public String errorMessage;

    public static List<PdfJob> findByTenant(UUID tenantId) {
        return find("tenantId = ?1 ORDER BY createdAt DESC", tenantId).list();
    }

    public static java.util.Optional<PdfJob> findByIdAndTenant(UUID id, UUID tenantId) {
        return find("id = ?1 AND tenantId = ?2", id, tenantId).firstResultOptional();
    }
}
