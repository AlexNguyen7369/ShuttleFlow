package com.shuttleflow.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Login input. The password is never returned or stored in this DTO. */
public final class LoginRequest {
    private final String email;
    private final String password;

    @JsonCreator
    public LoginRequest(@JsonProperty("email") String email, @JsonProperty("password") String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() { return email; }
    public String getPassword() { return password; }
}
