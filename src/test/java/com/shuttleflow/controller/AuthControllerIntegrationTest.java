package com.shuttleflow.controller;

import com.shuttleflow.auth.UserSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void customerLoginCreatesSessionAndReturnsSafeIdentity() throws Exception {
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"alex@shuttleflow.com\",\"password\":\"alex-test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.email").value("alex@shuttleflow.com"))
                .andExpect(jsonPath("$.providerId").doesNotExist())
                .andReturn();

        Object sessionIdentity = result.getRequest().getSession(false).getAttribute(UserSession.ATTRIBUTE);
        org.junit.jupiter.api.Assertions.assertNotNull(sessionIdentity);
        org.junit.jupiter.api.Assertions.assertFalse(result.getResponse().getContentAsString().contains("password"));
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
    void malformedBodyIsBadRequestAndLogoutClearsSession() throws Exception {
        mvc.perform(post("/auth/login").contentType("application/json").content("not-json"))
                .andExpect(status().isBadRequest());
        MvcResult login = mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"email\":\"alex@shuttleflow.com\",\"password\":\"alex-test\"}"))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(post("/auth/logout").session((org.springframework.mock.web.MockHttpSession)
                        login.getRequest().getSession(false))).andExpect(status().isNoContent());
    }

    @Test
    void sessionEndpointRestoresIdentityAndRejectsAnonymousOrLoggedOutCallers() throws Exception {
        mvc.perform(get("/auth/session")).andExpect(status().isUnauthorized());
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/auth/provider/login")
                        .contentType("application/json")
                        .content("{\"email\":\"coach.kim@shuttleflow.com\",\"password\":\"coach-kim-test\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        mvc.perform(get("/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PROVIDER"))
                .andExpect(jsonPath("$.providerId").value(2))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/auth/logout").session(session)).andExpect(status().isNoContent());
        mvc.perform(get("/auth/session").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginIssuesFreshSessionInsteadOfReusingPreLoginSession() throws Exception {
        MockHttpSession preLogin = new MockHttpSession();
        String preLoginId = preLogin.getId();
        MockHttpSession afterLogin = (MockHttpSession) mvc.perform(post("/auth/login").session(preLogin)
                        .contentType("application/json")
                        .content("{\"email\":\"alex@shuttleflow.com\",\"password\":\"alex-test\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        assertNotEquals(preLoginId, afterLogin.getId());
        assertTrue(preLogin.isInvalid());
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
