package com.shuttleflow.dto;

public final class HealthDto {
    private final String status;
    private final String database;

    public HealthDto(String status, String database) {
        this.status = status;
        this.database = database;
    }

    public String getStatus() { return status; }
    public String getDatabase() { return database; }
}
