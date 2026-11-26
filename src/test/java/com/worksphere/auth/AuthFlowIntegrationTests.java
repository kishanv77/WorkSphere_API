package com.worksphere.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.worksphere.PostgreSqlIntegrationTest;
import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.auth.security.JwtService;
import com.worksphere.user.domain.UserAccount;
import com.worksphere.user.repository.UserAccountRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIntegrationTests extends PostgreSqlIntegrationTest {

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void registrationCreatesAnActiveTenantAdminWithABcryptHash() throws Exception {
        JsonNode response = register("acme", "admin@acme.test", "CorrectHorseBatteryStaple1!");

        assertEquals("acme", response.path("tenantSlug").asString());
        assertEquals("TENANT_ADMIN", response.path("role").asString());
        UserAccount user = userAccountRepository.findByEmailIgnoreCase("admin@acme.test").orElseThrow();
        assertNotEquals("CorrectHorseBatteryStaple1!", user.getPasswordHash());
        assertTrue(passwordEncoder.matches("CorrectHorseBatteryStaple1!", user.getPasswordHash()));
    }

    @Test
    void registrationRejectsDuplicateEmailAndSlug() throws Exception {
        register("acme", "admin@acme.test", "CorrectHorseBatteryStaple1!");

        mockMvc.perform(post("/api/v1/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody("other-company", "admin@acme.test", "AnotherCorrectPassword1!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(post("/api/v1/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody("acme", "new-admin@acme.test", "AnotherCorrectPassword1!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void loginReturnsASignedTokenWithTheRequiredClaims() throws Exception {
        JsonNode registration = register("acme", "admin@acme.test", "CorrectHorseBatteryStaple1!");
        String token = login("admin@acme.test", "CorrectHorseBatteryStaple1!");

        JwtPrincipal principal = jwtService.parseAccessToken(token);
        assertEquals(registration.path("userId").asString(), principal.userId().toString());
        assertEquals(registration.path("tenantId").asString(), principal.tenantId().toString());
        assertEquals("TENANT_ADMIN", principal.role().name());
    }

    @Test
    void loginRejectsAnInvalidPassword() throws Exception {
        register("acme", "admin@acme.test", "CorrectHorseBatteryStaple1!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@acme.test","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void meRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void meReturnsTheIdentityBoundToTheBearerToken() throws Exception {
        JsonNode registration = register("acme", "admin@acme.test", "CorrectHorseBatteryStaple1!");
        String token = login("admin@acme.test", "CorrectHorseBatteryStaple1!");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(registration.path("userId").asString()))
                .andExpect(jsonPath("$.tenantId").value(registration.path("tenantId").asString()))
                .andExpect(jsonPath("$.email").value("admin@acme.test"))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"))
                .andExpect(jsonPath("$.role").value("TENANT_ADMIN"));
    }

    @Test
    void loginSetsAnHttpOnlyRefreshCookieThatRotatesOnRefresh() throws Exception {
        register("acme", "admin@acme.test", "CorrectHorseBatteryStaple1!");
        Cookie first = loginResult("admin@acme.test", "CorrectHorseBatteryStaple1!").getResponse().getCookie("ws_refresh");

        assertNotNull(first);
        assertTrue(first.isHttpOnly());
        assertTrue(first.getSecure());
        assertEquals("/api/v1/auth", first.getPath());

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        Cookie second = refreshed.getResponse().getCookie("ws_refresh");
        assertNotNull(second);
        assertNotEquals(first.getValue(), second.getValue());
        String accessToken = objectMapper.readTree(refreshed.getResponse().getContentAsString())
                .path("accessToken").asString();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // The rotated-out token no longer works.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshIgnoresAStaleBearerToken() throws Exception {
        register("acme", "admin@acme.test", "CorrectHorseBatteryStaple1!");
        Cookie cookie = loginResult("admin@acme.test", "CorrectHorseBatteryStaple1!").getResponse().getCookie("ws_refresh");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie).header("Authorization", "Bearer expired.or.junk"))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRevokesTheRefreshTokenAndClearsTheCookie() throws Exception {
        register("acme", "admin@acme.test", "CorrectHorseBatteryStaple1!");
        Cookie cookie = loginResult("admin@acme.test", "CorrectHorseBatteryStaple1!").getResponse().getCookie("ws_refresh");

        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andReturn();
        assertEquals(0, logout.getResponse().getCookie("ws_refresh").getMaxAge());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedFailedLoginsAreThrottled() throws Exception {
        register("acme", "throttle@acme.test", "CorrectHorseBatteryStaple1!");
        for (int attempt = 0; attempt < 5; attempt++) {
            failedLoginFrom("10.9.9.9", "throttle@acme.test").andExpect(status().isUnauthorized());
        }

        failedLoginFrom("10.9.9.9", "throttle@acme.test")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    private org.springframework.test.web.servlet.ResultActions failedLoginFrom(String address, String email)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"wrong-password"}
                        """.formatted(email)));
    }

    private MvcResult loginResult(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private JsonNode register(String slug, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(slug, email, password)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asString();
    }

    private String registrationBody(String slug, String email, String password) {
        return """
                {
                  "tenantName":"Acme Corporation",
                  "tenantSlug":"%s",
                  "firstName":"Ada",
                  "lastName":"Lovelace",
                  "email":"%s",
                  "password":"%s"
                }
                """.formatted(slug, email, password);
    }
}
