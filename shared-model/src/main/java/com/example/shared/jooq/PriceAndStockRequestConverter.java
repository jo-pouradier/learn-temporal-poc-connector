package com.example.shared.jooq;

import com.example.shared.model.PriceAndStockRequest;
import org.jooq.JSONB;
import org.jooq.Converter;

/**
 * jOOQ Converter for original_request_json JSONB column.
 * Converts between PostgreSQL JSONB and PriceAndStockRequest.
 */
public class PriceAndStockRequestConverter implements Converter<JSONB, PriceAndStockRequest> {

    private static final JsonbConverter<PriceAndStockRequest> DELEGATE = 
            JsonbConverter.of(PriceAndStockRequest.class);

    @Override
    public PriceAndStockRequest from(JSONB jsonb) {
        return DELEGATE.from(jsonb);
    }

    @Override
    public JSONB to(PriceAndStockRequest obj) {
        return DELEGATE.to(obj);
    }

    @Override
    public Class<JSONB> fromType() {
        return JSONB.class;
    }

    @Override
    public Class<PriceAndStockRequest> toType() {
        return PriceAndStockRequest.class;
    }
}
