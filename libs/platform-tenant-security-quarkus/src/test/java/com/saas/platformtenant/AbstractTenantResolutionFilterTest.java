package com.saas.platformtenant;

import com.saas.platformtenant.fixtures.FakeTenantMembershipResolver;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;

@QuarkusTest
class AbstractTenantResolutionFilterTest {

    @Test
    void noToken_returns401() {
        RestAssured.given()
                .when().get("/test/tenant/secured")
                .then().statusCode(401);
    }

    @Test
    void excludedPath_skipsFilter_evenWithoutToken() {
        RestAssured.given()
                .when().get("/test/tenant/excluded")
                .then().statusCode(200)
                .body("ok", is(true));
    }

    @Test
    @TestSecurity(user = "22222222-2222-2222-2222-222222222222")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "22222222-2222-2222-2222-222222222222") })
    void excludedPath_skipsTenantResolution_evenForUserWithNoMembership() {
        RestAssured.given()
                .when().get("/test/tenant/excluded-authenticated")
                .then().statusCode(200)
                .body("ok", is(true));
    }

    @Test
    @TestSecurity(user = "11111111-1111-1111-1111-111111111111")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "11111111-1111-1111-1111-111111111111") })
    void validToken_noTenantHeader_resolvesDefaultTenant() {
        RestAssured.given()
                .when().get("/test/tenant/secured")
                .then().statusCode(200)
                .body("tenantId", is(FakeTenantMembershipResolver.DEFAULT_TENANT_ID.toString()))
                .body("planCode", is("PRO"))
                .body("hasPdf", is(true));
    }

    @Test
    @TestSecurity(user = "22222222-2222-2222-2222-222222222222")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "22222222-2222-2222-2222-222222222222") })
    void validToken_noTenantHeader_noDefaultTenant_returns401() {
        RestAssured.given()
                .when().get("/test/tenant/secured")
                .then().statusCode(401)
                .body("error", is("Usuário sem tenant"));
    }

    @Test
    @TestSecurity(user = "11111111-1111-1111-1111-111111111111")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "11111111-1111-1111-1111-111111111111") })
    void validToken_withTenantHeader_memberButNoSubscription_isTolerant() {
        RestAssured.given()
                .header("X-Tenant-ID", FakeTenantMembershipResolver.MEMBER_TENANT_ID.toString())
                .when().get("/test/tenant/secured")
                .then().statusCode(200)
                .body("tenantId", is(FakeTenantMembershipResolver.MEMBER_TENANT_ID.toString()))
                .body("planCode", is(""))
                .body("hasPdf", is(false));
    }

    @Test
    @TestSecurity(user = "11111111-1111-1111-1111-111111111111")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "11111111-1111-1111-1111-111111111111") })
    void validToken_withTenantHeader_noAccess_returns401() {
        RestAssured.given()
                .header("X-Tenant-ID", FakeTenantMembershipResolver.FOREIGN_TENANT_ID.toString())
                .when().get("/test/tenant/secured")
                .then().statusCode(401)
                .body("error", is("Acesso negado ao tenant"));
    }

    @Test
    @TestSecurity(user = "11111111-1111-1111-1111-111111111111")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "11111111-1111-1111-1111-111111111111") })
    void validToken_malformedTenantHeader_returns401() {
        RestAssured.given()
                .header("X-Tenant-ID", "not-a-uuid")
                .when().get("/test/tenant/secured")
                .then().statusCode(401)
                .body("error", is("X-Tenant-ID inválido"));
    }

    @Test
    @TestSecurity(user = "11111111-1111-1111-1111-111111111111")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "11111111-1111-1111-1111-111111111111") })
    void pathTenantMismatch_returns403() {
        RestAssured.given()
                .header("X-Tenant-ID", FakeTenantMembershipResolver.DEFAULT_TENANT_ID.toString())
                .when().get("/test/tenant/" + FakeTenantMembershipResolver.MEMBER_TENANT_ID + "/scoped")
                .then().statusCode(403)
                .body("error", is("Acesso negado ao tenant"));
    }

    @Test
    @TestSecurity(user = "11111111-1111-1111-1111-111111111111")
    @JwtSecurity(claims = { @Claim(key = "sub", value = "11111111-1111-1111-1111-111111111111") })
    void pathTenantMatch_returns200() {
        RestAssured.given()
                .header("X-Tenant-ID", FakeTenantMembershipResolver.DEFAULT_TENANT_ID.toString())
                .when().get("/test/tenant/" + FakeTenantMembershipResolver.DEFAULT_TENANT_ID + "/scoped")
                .then().statusCode(200)
                .body("tenantId", is(FakeTenantMembershipResolver.DEFAULT_TENANT_ID.toString()));
    }
}
