package com.shuttleflow.controller;

import com.shuttleflow.auth.UserSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

}
