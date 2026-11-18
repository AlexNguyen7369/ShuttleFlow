package com.shuttleflow.auth;

/** Immutable identity of the signed-in caller, carried in the login token (JWT). */
public final class UserSession {

    private final long userId;
    private final String email;
    private final String fullName;
    private final String role;
    private final Long providerId;

    public UserSession(long userId, String email, String fullName, String role, Long providerId) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.providerId = providerId;
    }

    public long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
    public Long getProviderId() { return providerId; }
}
