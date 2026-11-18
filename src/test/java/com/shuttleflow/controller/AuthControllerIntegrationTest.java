package com.shuttleflow.controller;

import com.shuttleflow.auth.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void customerLoginSetsAnHttpOnlyTokenCookieAndReturnsSafeIdentity() throws Exception {
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"alex@shuttleflow.com\",\"password\":\"alex-test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.email").value("alex@shuttleflow.com"))
                .andExpect(jsonPath("$.providerId").doesNotExist())
                .andReturn();

        Cookie token = result.getResponse().getCookie(JwtService.COOKIE_NAME);
        org.junit.jupiter.api.Assertions.assertNotNull(token);
        assertTrue(token.isHttpOnly(), "page JavaScript must not be able to read the token");
        assertTrue(result.getResponse().getHeader("Set-Cookie").contains("SameSite=Lax"));
        String body = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("password"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains(token.getValue()), "the token is never in the body");
    }

    @Test
    void providerLoginReturnsProviderLink() throws Exception {
        mvc.perform(post("/auth/provider/login")
                        .contentType("application/json")
                        .content("{\"email\":\"court.manager@shuttleflow.com\",\"password\":\"court-manager-test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PROVIDER"))
                .andExpect(jsonPath("$.providerId").value(1));
    }

    @Test
    void wrongOrUnknownCredentialsHaveSameUnauthorizedResponse() throws Exception {
        mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"email\":\"alex@shuttleflow.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password."));
        mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password."));
    }

    @Test
    void providerLoginRejectsCustomerAndMissingCredentials() throws Exception {
        mvc.perform(post("/auth/provider/login").contentType("application/json")
                        .content("{\"email\":\"alex@shuttleflow.com\",\"password\":\"alex-test\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedBodyIsBadRequestAndLogoutClearsTheCookie() throws Exception {
        mvc.perform(post("/auth/login").contentType("application/json").content("not-json"))
                .andExpect(status().isBadRequest());
        MvcResult login = mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"email\":\"alex@shuttleflow.com\",\"password\":\"alex-test\"}"))
                .andExpect(status().isOk()).andReturn();
        Cookie cleared = mvc.perform(post("/auth/logout").cookie(login.getResponse().getCookie(JwtService.COOKIE_NAME)))
                .andExpect(status().isNoContent()).andReturn().getResponse().getCookie(JwtService.COOKIE_NAME);
        assertEquals(0, cleared.getMaxAge());
        assertEquals("", cleared.getValue());
    }

    @Test
    void sessionEndpointRestoresIdentityAndRejectsAnonymousOrLoggedOutCallers() throws Exception {
        mvc.perform(get("/auth/session")).andExpect(status().isUnauthorized());
        Cookie session = mvc.perform(post("/auth/provider/login")
                        .contentType("application/json")
                        .content("{\"email\":\"coach.kim@shuttleflow.com\",\"password\":\"coach-kim-test\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(JwtService.COOKIE_NAME);
        mvc.perform(get("/auth/session").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PROVIDER"))
                .andExpect(jsonPath("$.providerId").value(2))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        // Logout makes the browser drop the cookie; the request that follows carries the emptied cookie.
        Cookie cleared = mvc.perform(post("/auth/logout").cookie(session)).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie(JwtService.COOKIE_NAME);
        mvc.perform(get("/auth/session").cookie(cleared)).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedOrForeignTokensAreUnauthorized() throws Exception {
        String token = mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"email\":\"alex@shuttleflow.com\",\"password\":\"alex-test\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(JwtService.COOKIE_NAME).getValue();
        // Flip one character of the signature.
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        mvc.perform(get("/auth/session").cookie(new Cookie(JwtService.COOKIE_NAME, tampered)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/session").cookie(new Cookie(JwtService.COOKIE_NAME, "not.a.jwt")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/session").cookie(new Cookie(JwtService.COOKIE_NAME, token)))
                .andExpect(status().isOk());
    }

    @Test
    void frontendOriginPassesCorsPreflightWithCredentials() throws Exception {
        mvc.perform(options("/appointments")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/appointments")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownRouteAndWrongMethodKeepTheirStatusInsteadOf500() throws Exception {
        mvc.perform(get("/no-such-route")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
        mvc.perform(put("/slots"))
                .andExpect(status().isMethodNotAllowed());
    }
}
