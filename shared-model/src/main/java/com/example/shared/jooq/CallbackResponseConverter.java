package com.example.shared.jooq;

import com.example.shared.model.CallbackResponse;
import org.jooq.JSONB;
import org.jooq.Converter;

/**
 * jOOQ Converter for callback JSONB columns.
 * Converts between PostgreSQL JSONB and CallbackResponse.
 * Used for: price_callback_json, stock_callback_json, final_response_json
 */
public class CallbackResponseConverter implements Converter<JSONB, CallbackResponse> {

    private static final JsonbConverter<CallbackResponse> DELEGATE = 
            JsonbConverter.of(CallbackResponse.class);

    @Override
    public CallbackResponse from(JSONB jsonb) {
        return DELEGATE.from(jsonb);
    }

    @Override
    public JSONB to(CallbackResponse obj) {
        return DELEGATE.to(obj);
    }

    @Override
    public Class<JSONB> fromType() {
        return JSONB.class;
    }

    @Override
    public Class<CallbackResponse> toType() {
        return CallbackResponse.class;
    }
}
