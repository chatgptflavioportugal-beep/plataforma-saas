package com.saas.platformsecurity;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.is;

@QuarkusTest
class ModuleAccessFilterTest {

    @Test
    void missingAuthorizationHeaderReturns401() {
        RestAssured.given()
                .when().get("/test/secured/pdf")
                .then().statusCode(401);
    }

    @Test
    void malformedAuthorizationHeaderReturns401() {
        RestAssured.given()
                .header("Authorization", "Token abc")
                .when().get("/test/secured/pdf")
                .then().statusCode(401);
    }

    @Test
    void validTokenReturns200() {
        String token = TestTokens.moduleToken("pdf", List.of());

        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when().get("/test/secured/pdf")
                .then().statusCode(200)
                .body("tenantId", is(TestTokens.TENANT_ID));
    }

    @Test
    void wrongModuleSlugReturns403() {
        String token = TestTokens.moduleToken("whatsapp", List.of());

        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when().get("/test/secured/pdf")
                .then().statusCode(403);
    }

    @Test
    void missingPermissionReturns403() {
        String token = TestTokens.moduleToken("pdf", List.of());

        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when().get("/test/secured/pdf-merge")
                .then().statusCode(403);
    }

    @Test
    void withPermissionReturns200() {
        String token = TestTokens.moduleToken("pdf", List.of("pdf-merge"));

        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when().get("/test/secured/pdf-merge")
                .then().statusCode(200)
                .body("ok", is(true));
    }
}
