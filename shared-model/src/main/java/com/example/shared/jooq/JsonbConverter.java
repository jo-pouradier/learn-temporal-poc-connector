package com.example.shared.jooq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jooq.JSONB;
import org.jooq.Converter;

/**
 * Generic jOOQ Converter for JSONB columns.
 * Converts between PostgreSQL JSONB and Java objects using Jackson.
 *
 * @param <T> the target Java type
 */
public class JsonbConverter<T> implements Converter<JSONB, T> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final Class<T> targetType;

    public JsonbConverter(Class<T> targetType) {
        this.targetType = targetType;
    }

    /**
     * Create a converter for the specified type.
     */
    public static <T> JsonbConverter<T> of(Class<T> type) {
        return new JsonbConverter<>(type);
    }

    @Override
    public T from(JSONB jsonb) {
        if (jsonb == null || jsonb.data() == null || jsonb.data().isEmpty()) {
            return null;
        }

        try {
            return MAPPER.readValue(jsonb.data(), targetType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSONB to " + targetType.getSimpleName(), e);
        }
    }

    @Override
    public JSONB to(T obj) {
        if (obj == null) {
            return null;
        }

        try {
            return JSONB.valueOf(MAPPER.writeValueAsString(obj));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize " + targetType.getSimpleName() + " to JSONB", e);
        }
    }

    @Override
    public Class<JSONB> fromType() {
        return JSONB.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<T> toType() {
        return targetType;
    }
}
