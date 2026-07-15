package com.nthippar.eventledger.event.mapper;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.nthippar.eventledger.event.api.CreateEventRequest;
import com.nthippar.eventledger.event.api.EventResponse;
import com.nthippar.eventledger.event.domain.LedgerEvent;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LedgerEventMapper {

    private final ObjectMapper objectMapper;

    public LedgerEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LedgerEvent toEntity(CreateEventRequest request) {
        return new LedgerEvent(
                request.eventId(),
                request.accountId(),
                request.type(),
                request.amount(),
                request.currency().toUpperCase(),
                request.eventTimestamp(),
                writeMetadata(request.metadata())
        );
    }

    public EventResponse toResponse(LedgerEvent event) {
        return new EventResponse(
                event.getEventId(),
                event.getAccountId(),
                event.getType(),
                event.getAmount(),
                event.getCurrency(),
                event.getEventTimestamp(),
                readMetadata(event.getMetadataJson())
        );
    }

    private String writeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "metadata could not be serialized",
                    exception
            );
        }
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(
                    metadataJson,
                    new TypeReference<>() {
                    }
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "stored metadata could not be deserialized",
                    exception
            );
        }
    }
}