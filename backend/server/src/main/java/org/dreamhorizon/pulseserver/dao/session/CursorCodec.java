package org.dreamhorizon.pulseserver.dao.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Base64;

public final class CursorCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CursorCodec() {}

    public static String encode(Object sortValue, String sessionId) {
        try {
            byte[] json = MAPPER.writeValueAsBytes(new CursorPayload(sortValue, sessionId));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode cursor", e);
        }
    }

    public static CursorValue decode(String cursor) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor);
            CursorPayload payload = MAPPER.readValue(json, CursorPayload.class);
            return new CursorValue(payload.v, payload.s);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor: " + cursor, e);
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class CursorValue {
        private final Object sortValue;
        private final String sessionId;
    }

    private record CursorPayload(
            @JsonProperty("v") Object v,
            @JsonProperty("s") String s
    ) {}
}
