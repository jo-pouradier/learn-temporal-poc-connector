package com.example.temporal.temporal.utils;

public class WorkflowIdBuilder {

    private WorkflowIdBuilder() {}

    public static String priceStockWorkflowId(String orderId) {
        return "price-and-stock-" + orderId;
    }

    public static String sendPriceUpdatesWorkflowId() {
        return "send-price-updates-global";
    }

    public static String sendStockUpdatesWorkflowId() {
        return "send-stock-updates-global";
    }
}
