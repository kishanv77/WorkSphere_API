package com.worksphere.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worksphere.PostgreSqlIntegrationTest;
import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.auth.security.JwtService;
import com.worksphere.user.domain.UserAccount;
import com.worksphere.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIntegrationTests extends PostgreSqlIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void registrationCreatesAnActiveTenantAdminWithABcryptHash() throws Exception {
        JsonNode response = register("acme", "admin@acme.test", "CorrectHorseBatteryStaple1!");

        assertEquals("acme", response.path("tenantSlug").asText());
        assertEquals("TENANT_ADMIN", response.path("role").asText());
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
        assertEquals(registration.path("userId").asText(), principal.userId().toString());
        assertEquals(registration.path("tenantId").asText(), principal.tenantId().toString());
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
                .andExpect(jsonPath("$.userId").value(registration.path("userId").asText()))
                .andExpect(jsonPath("$.tenantId").value(registration.path("tenantId").asText()))
                .andExpect(jsonPath("$.email").value("admin@acme.test"))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"))
                .andExpect(jsonPath("$.role").value("TENANT_ADMIN"));
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
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
