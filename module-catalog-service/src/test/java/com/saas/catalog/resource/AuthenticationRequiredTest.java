package com.saas.catalog.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Confirma que a rota de catálogo rejeita requisições sem JWT — regressão
 * simples para a checagem @Authenticated do Resource, independente da
 * lógica de negócio por trás dela. Não requer datasource real: a rejeição
 * ocorre no filtro JWT antes de o método do Resource ser executado (mesmo
 * padrão de subscription-service/AuthenticationRequiredTest.java).
 */
@QuarkusTest
class AuthenticationRequiredTest {

    @Test
    void resolveRoute_requiresAuthentication() {
        given()
            .when().get("/api/v1/services/resolve-route/qualquer-rota")
            .then()
                .statusCode(401);
    }
}
