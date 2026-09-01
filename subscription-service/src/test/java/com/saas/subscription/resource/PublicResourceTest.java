package com.saas.subscription.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Endpoints públicos (sem autenticação) do catálogo — únicos endpoints deste
 * serviço seguros para exercitar em qualquer ambiente, pois são SELECT-only
 * e não requerem JWT/tenant. Os demais endpoints (autenticados/tenant-scoped)
 * não têm cobertura automatizada aqui porque exigir um datasource real (este
 * serviço usa o Supabase compartilhado de dev, não um banco de testes
 * isolado/Devservices) tornaria os testes não confiáveis para rodar em CI
 * sem uma role de banco dedicada a testes — ver relatório de migração,
 * seção 10 (Testes).
 */
@QuarkusTest
class PublicResourceTest {

    @Test
    void listPlans_returnsOkWithoutAuthentication() {
        given()
            .when().get("/api/v1/public/plans")
            .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    void listPlans_acceptsOptionalTypeFilter() {
        given()
            .queryParam("type", "individual")
            .when().get("/api/v1/public/plans")
            .then()
                .statusCode(200);
    }

    @Test
    void listPlans_acceptsOptionalPagination() {
        given()
            .queryParam("page", 1)
            .queryParam("size", 5)
            .when().get("/api/v1/public/plans")
            .then()
                .statusCode(200);
    }

    @Test
    void listModuleBillingOptions_returnsOkWithoutAuthentication() {
        given()
            .when().get("/api/v1/public/modules/billing-options")
            .then()
                .statusCode(200)
                .body(notNullValue());
    }
}
