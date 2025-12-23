package com.example.shared.model;

public class JavaResponse {

    private String correlationId;
    private String status;
    private int price;
    private boolean isPriceOk;
    private boolean isStockOk;
    private int stock;
    private String receivedAt;
    private String priceProcessedAt;
    private String stockProcessedAt;
    private String completedAt;

    public JavaResponse() {
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public boolean isPriceOk() {
        return isPriceOk;
    }

    public void setPriceOk(boolean priceOk) {
        isPriceOk = priceOk;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public boolean isStockOk() {
        return isStockOk;
    }

    public void setStockOk(boolean stockOk) {
        isStockOk = stockOk;
    }

    public String getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(String receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getPriceProcessedAt() {
        return priceProcessedAt;
    }

    public void setPriceProcessedAt(String priceProcessedAt) {
        this.priceProcessedAt = priceProcessedAt;
    }

    public String getStockProcessedAt() {
        return stockProcessedAt;
    }

    public void setStockProcessedAt(String stockProcessedAt) {
        this.stockProcessedAt = stockProcessedAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    @Override
    public String toString() {
        return "JavaResponse{" +
                "correlationId='" + correlationId + '\'' +
                ", status='" + status + '\'' +
                ", price=" + price +
                ", isPriceOk=" + isPriceOk +
                ", stock=" + stock +
                ", isStockOk=" + isStockOk +
                ", receivedAt=" + receivedAt +
                ", priceProcessedAt=" + priceProcessedAt +
                ", stockProcessedAt=" + stockProcessedAt +
                ", completedAt=" + completedAt +
                '}';
    }
}
