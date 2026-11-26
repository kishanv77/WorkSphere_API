package com.worksphere.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.worksphere.common.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RestAuthenticationEntryPointTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void writesAJsonUnauthorizedError() throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(jsonMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("bad token"));

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertEquals(401, body.path("status").asInt());
        assertEquals("Unauthorized", body.path("error").asString());
        assertEquals("Authentication is required", body.path("message").asString());
        assertEquals("/api/v1/projects", body.path("path").asString());
        assertFalse(body.path("timestamp").asString().isBlank());
    }
}
