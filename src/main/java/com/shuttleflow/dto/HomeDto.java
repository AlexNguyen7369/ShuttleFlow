package com.shuttleflow.dto;

public class HomeDto {

    private final String appName;
    private final int openSlotCount;

    public HomeDto(String appName, int openSlotCount) {
        this.appName = appName;
        this.openSlotCount = openSlotCount;
    }

    public String getAppName() {
        return appName;
    }

    public int getOpenSlotCount() {
        return openSlotCount;
    }
}
