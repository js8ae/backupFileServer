package com.intocns.backup.api.config;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class FlexibleInstantDeserializer extends StdDeserializer<Instant> {

    private static final DateTimeFormatter SPACE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public FlexibleInstantDeserializer() {
        super(Instant.class);
    }

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctx) throws JacksonException {
        String text = p.getString().trim();
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(text, SPACE_FORMAT)
                    .atZone(ZoneId.of("Asia/Seoul"))
                    .toInstant();
        }
    }
}
