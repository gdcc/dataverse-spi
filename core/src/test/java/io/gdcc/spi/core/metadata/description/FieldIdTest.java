package io.gdcc.spi.core.metadata.description;

import io.gdcc.spi.core.metadata.Namespace;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FieldIdTest {
    
    /**
     * Tests for the static factory method {@link FieldId#of(Namespace, String)}.
     */
    @Test
    void testOfWithValidInputs() {
        Namespace namespace = Namespace.of(URI.create("http://example.org/"), "ex");
        String localName = "field";
        
        FieldId fieldId = FieldId.of(namespace, localName);
        
        assertNotNull(fieldId);
        assertEquals(namespace, fieldId.getNamespace());
        assertEquals(localName, fieldId.getLocalName());
    }
    
    @Test
    void testOfWithNullNamespaceThrowsException() {
        String localName = "field";
        
        Exception exception = assertThrows(NullPointerException.class, () -> FieldId.of(null, localName));
        assertEquals("namespace must not be null", exception.getMessage());
    }
    
    @Test
    void testOfWithNullLocalNameThrowsException() {
        Namespace namespace = Namespace.of(URI.create("http://example.org/"), "ex");
        
        Exception exception = assertThrows(NullPointerException.class, () -> FieldId.of(namespace, null));
        assertEquals("localName must not be null", exception.getMessage());
    }
    
    @Test
    void testOfWithEmptyLocalNameThrowsException() {
        Namespace namespace = Namespace.of(URI.create("http://example.org/"), "ex");
        String emptyLocalName = "";
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> FieldId.of(namespace, emptyLocalName));
        assertEquals("localName cannot be empty or blank", exception.getMessage());
    }
    
    @Test
    void testOfWithBlankLocalNameThrowsException() {
        Namespace namespace = Namespace.of(URI.create("http://example.org/"), "ex");
        String blankLocalName = "   ";
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> FieldId.of(namespace, blankLocalName));
        assertEquals("localName cannot be empty or blank", exception.getMessage());
    }
}