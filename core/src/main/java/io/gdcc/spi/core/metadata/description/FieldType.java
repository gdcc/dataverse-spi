package io.gdcc.spi.core.metadata.description;

import io.gdcc.spi.core.metadata.primitives.Email;

import java.net.URI;
import java.time.Instant;

public enum FieldType {
    
    TEXT(String.class),
    TEXTBOX(String.class),
    STRING(String.class),
    DATE(Instant.class),
    EMAIL(Email.class),
    URL(URI.class),
    FRACTIONAL(Double.class),
    NONFRACTIONAL(Long.class),
    COMPOUND(null);
    
    private final Class<?> type;
    
    FieldType(Class<?> type) {
        this.type = type;
    }
    
    public Class<?> getType() {
        return type;
    }
    
}
