package com.milktea.agent.model;

public enum OrderStatus {

    PENDING("待制作"),
    MAKING("制作中"),
    COMPLETED("已完成"),
    CANCELLED("已取消"),
    REFUNDED("已退单");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
