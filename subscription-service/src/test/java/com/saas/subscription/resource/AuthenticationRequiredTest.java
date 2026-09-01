package com.saas.subscription.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Confirma que as rotas autenticadas (tenant-scoped e admin) rejeitam
 * requisições sem JWT — regressão simples para a checagem @Authenticated
 * de cada Resource, independente da lógica de negócio por trás dela.
 */
@QuarkusTest
class AuthenticationRequiredTest {

    @Test
    void dashboardModules_requiresAuthentication() {
        given()
            .when().get("/api/v1/dashboard/modules")
            .then()
                .statusCode(401);
    }

    @Test
    void listModuleSubscriptions_requiresAuthentication() {
        given()
            .when().get("/api/v1/subscriptions/modules")
            .then()
                .statusCode(401);
    }

    @Test
    void moduleAccessResolve_requiresAuthentication() {
        given()
            .when().get("/api/v1/internal/module-access/pdf")
            .then()
                .statusCode(401);
    }

    @Test
    void adminCancelSubscription_requiresAuthentication() {
        given()
            .when().post("/api/v1/admin/subscriptions/00000000-0000-0000-0000-000000000000/cancel")
            .then()
                .statusCode(401);
    }
}
