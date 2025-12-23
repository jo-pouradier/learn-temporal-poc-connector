package com.example.shared.model;

import java.time.LocalDateTime;

public record CallbackResponse(
        String correlationId,
        String status,
        LocalDateTime processedAt,
        String type,
        Integer price,
        Boolean isPriceOk,
        Integer stock,
        Boolean isStockOk,
        String error
) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String correlationId;
        private String status;
        private LocalDateTime processedAt;
        private String type;
        private Integer price;
        private Boolean isPriceOk;
        private Integer stock;
        private Boolean isStockOk;
        private String error;

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder processedAt(LocalDateTime processedAt) {
            this.processedAt = processedAt;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder price(Integer price) {
            this.price = price;
            return this;
        }

        public Builder priceOk(Boolean isPriceOk) {
            this.isPriceOk = isPriceOk;
            return this;
        }

        public Builder stock(Integer stock) {
            this.stock = stock;
            return this;
        }

        public Builder stockOk(Boolean isStockOk) {
            this.isStockOk = isStockOk;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public CallbackResponse build() {
            return new CallbackResponse(
                    correlationId,
                    status,
                    processedAt,
                    type,
                    price,
                    isPriceOk,
                    stock,
                    isStockOk,
                    error
            );
        }
    }
}
