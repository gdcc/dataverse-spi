package io.gdcc.spi.core.metadata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NamespaceTest {
    
    /**
     * Tests for the resolve method and creation methods (of) of the Namespace class.
     * - The resolve method appends the given localName to the baseUri of the Namespace.
     * - The of methods construct Namespace instances and enforce validation on inputs.
     * <p>
     * Exceptions are thrown when invalid inputs such as null or blank values are provided.
     */
    
    @ParameterizedTest
    @CsvSource({
        "http://example.com/, resource, http://example.com/resource",
        "http://example.com, resource, http://example.com/resource",
        "http://example.com#, resource, http://example.com#resource"
    })
    void resolveWithValidInputs(String baseUri, String localName, String expected) {
        // Arrange
        Namespace namespace = Namespace.of(URI.create(baseUri), "ex");
        
        // Act
        URI result = namespace.resolve(localName);
        
        // Assert
        assertEquals(URI.create(expected), result);
    }
    
    @Test
    void resolveThrowsExceptionForNullLocalName() {
        // Arrange
        URI baseUri = URI.create("http://example.com/");
        Namespace namespace = Namespace.of(baseUri, "ex");
        
        // Act and Assert
        assertThrows(IllegalArgumentException.class, () -> namespace.resolve(null),
            "Expected resolve() to throw for null localName but it didn't.");
    }
    
    @Test
    void resolveThrowsExceptionForBlankLocalName() {
        // Arrange
        URI baseUri = URI.create("http://example.com/");
        Namespace namespace = Namespace.of(baseUri, "ex");
        
        // Act and Assert
        assertThrows(IllegalArgumentException.class, () -> namespace.resolve(" "),
            "Expected resolve() to throw for blank localName but it didn't.");
    }
    
    @Test
    void ofThrowsExceptionForNullUri() {
        // Act and Assert
        assertThrows(NullPointerException.class,
            () -> Namespace.of((URI) null, "ex"),
            "Expected of() to throw for null baseUri but it didn't.");
    }
    
    @Test
    void ofThrowsExceptionForNullPrefix() {
        // Act and Assert
        assertThrows(NullPointerException.class,
            () -> Namespace.of(URI.create("http://example.com/"), null),
            "Expected of() to throw for null prefix but it didn't.");
    }
    
    @Test
    void ofThrowsExceptionForBlankPrefix() {
        // Act and Assert
        assertThrows(IllegalArgumentException.class,
            () -> Namespace.of(URI.create("http://example.com/"), " "),
            "Expected of() to throw for blank prefix but it didn't.");
    }
    
    @Test
    void ofStringThrowsExceptionForInvalidUri() {
        // Act and Assert
        assertThrows(IllegalArgumentException.class,
            () -> Namespace.of("invalid-uri", "ex"),
            "Expected of() to throw for invalid baseUri string but it didn't.");
    }
    
    @Test
    void ofThrowsExceptionForSchemalessUri() {
        // Act and Assert
        var uri = URI.create("//example.com/");
        assertThrows(IllegalArgumentException.class,
            () -> Namespace.of(uri, " "),
            "Expected of() to throw for blank prefix but it didn't.");
    }
    
    @Test
    void ofCorrectlyCreatesNamespace() {
        // Arrange
        URI baseUri = URI.create("http://example.com/");
        String prefix = "ex";
        
        // Act
        Namespace namespace = Namespace.of(baseUri, prefix);
        
        // Assert
        assertNotNull(namespace, "Expected Namespace object to be created but it was null.");
        assertEquals(baseUri, namespace.getBaseUri());
        assertEquals(prefix, namespace.getPrefix());
    }
}